// wifi-measurements-transformer-service/src/main/java/com/wifi/measurements/transformer/service/GracefulShutdownService.java
package com.wifi.measurements.transformer.service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import com.wifi.measurements.transformer.listener.SqsMessageReceiver;

import jakarta.annotation.PreDestroy;

/**
 * Service for handling graceful shutdown with proper Firehose batch flushing.
 *
 * <p>This service ensures that during application shutdown, all pending Firehose batches are
 * properly flushed and delivered before the application terminates. It coordinates with SQS message
 * processing to stop accepting new work while allowing in-flight operations to complete.
 *
 * <p><strong>Shutdown Sequence:</strong>
 *
 * <ol>
 *   <li>Stop accepting new SQS messages
 *   <li>Wait for in-flight processing to complete
 *   <li>Flush all pending Firehose batches
 *   <li>Wait for Firehose delivery confirmation
 *   <li>Complete shutdown
 * </ol>
 *
 * <p><strong>Timeout Handling:</strong>
 *
 * <ul>
 *   <li>Maximum shutdown timeout: 30 seconds
 *   <li>Firehose flush timeout: 15 seconds
 *   <li>Processing completion timeout: 10 seconds
 * </ul>
 */
@Service
public class GracefulShutdownService {

  private static final Logger logger = LoggerFactory.getLogger(GracefulShutdownService.class);

  private static final Duration MAX_SHUTDOWN_TIMEOUT = Duration.ofSeconds(30);
  private static final Duration FIREHOSE_FLUSH_TIMEOUT = Duration.ofSeconds(15);
  private static final Duration PROCESSING_COMPLETION_TIMEOUT = Duration.ofSeconds(10);

  private final SqsMessageReceiver sqsMessageReceiver;
  private final WiFiMeasurementsPublisher wifiMeasurementsPublisher;
  private final ProcessingMetricsService processingMetricsService;

  private final AtomicBoolean shutdownInProgress = new AtomicBoolean(false);

  public GracefulShutdownService(
      SqsMessageReceiver sqsMessageReceiver,
      WiFiMeasurementsPublisher wifiMeasurementsPublisher,
      ProcessingMetricsService processingMetricsService) {
    this.sqsMessageReceiver = sqsMessageReceiver;
    this.wifiMeasurementsPublisher = wifiMeasurementsPublisher;
    this.processingMetricsService = processingMetricsService;
  }

  /** Handles Spring context closure event for graceful shutdown. */
  @EventListener
  public void onContextClosed(ContextClosedEvent event) {
    logger.info("Context closed event received, initiating graceful shutdown");
    initiateGracefulShutdown();
  }

  /** PreDestroy hook for graceful shutdown. */
  @PreDestroy
  public void preDestroy() {
    logger.info("PreDestroy hook called, initiating graceful shutdown");
    initiateGracefulShutdown();
  }

  /** Initiates the graceful shutdown process. */
  public void initiateGracefulShutdown() {
    if (!shutdownInProgress.compareAndSet(false, true)) {
      logger.info("Graceful shutdown already in progress, ignoring duplicate request");
      return;
    }

    Instant shutdownStart = Instant.now();
    logger.info("Starting graceful shutdown process...");

    try {
      // Step 1: Stop accepting new SQS messages
      stopSqsMessageProcessing();

      // Step 2: Wait for in-flight processing to complete
      waitForProcessingCompletion();

      // Step 3: Flush all pending Firehose batches
      flushFirehoseBatches();

      Duration shutdownDuration = Duration.between(shutdownStart, Instant.now());
      logger.info("Graceful shutdown completed successfully in {}ms", shutdownDuration.toMillis());

    } catch (Exception e) {
      Duration shutdownDuration = Duration.between(shutdownStart, Instant.now());
      logger.error("Graceful shutdown failed after {}ms", shutdownDuration.toMillis(), e);
    } finally {
      logShutdownMetrics();
    }
  }

  private void stopSqsMessageProcessing() {
    logger.info("Stopping SQS message processing...");

    try {
      // Stop the SQS message receiver
      sqsMessageReceiver.stop();
      logger.info("SQS message processing stopped successfully");

    } catch (Exception e) {
      logger.error("Error stopping SQS message processing", e);
    }
  }

  private void waitForProcessingCompletion() {
    logger.info("Waiting for in-flight processing to complete...");

    Instant waitStart = Instant.now();

    try {
      // Wait for all files to finish processing
      CompletableFuture<Void> processingCompletion =
          CompletableFuture.runAsync(
              () -> {
                while (processingMetricsService.getCurrentlyProcessingFiles() > 0) {
                  try {
                    Thread.sleep(500); // Check every 500ms
                  } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(
                        "Interrupted while waiting for processing completion", e);
                  }
                }
              });

      processingCompletion.get(PROCESSING_COMPLETION_TIMEOUT.toSeconds(), TimeUnit.SECONDS);

      Duration waitDuration = Duration.between(waitStart, Instant.now());
      logger.info("In-flight processing completed in {}ms", waitDuration.toMillis());

    } catch (Exception e) {
      Duration waitDuration = Duration.between(waitStart, Instant.now());
      logger.warn(
          "Timeout waiting for processing completion after {}ms, proceeding with shutdown",
          waitDuration.toMillis(),
          e);
    }
  }

  private void flushFirehoseBatches() {
    logger.info("Flushing pending Firehose batches...");

    Instant flushStart = Instant.now();

    try {
      // Create timeout for flush operation
      CompletableFuture<Void> flushCompletion =
          CompletableFuture.runAsync(
              () -> {
                try {
                  wifiMeasurementsPublisher.flushCurrentBatch().join();
                } catch (Exception e) {
                  throw new RuntimeException("Error flushing Firehose batches", e);
                }
              });

      flushCompletion.get(FIREHOSE_FLUSH_TIMEOUT.toSeconds(), TimeUnit.SECONDS);

      Duration flushDuration = Duration.between(flushStart, Instant.now());
      logger.info("Firehose batch flush completed in {}ms", flushDuration.toMillis());

    } catch (Exception e) {
      Duration flushDuration = Duration.between(flushStart, Instant.now());
      logger.error("Error flushing Firehose batches after {}ms", flushDuration.toMillis(), e);
    }
  }

  private void logShutdownMetrics() {
    logger.info("Shutdown metrics: {}", processingMetricsService.getMetricsSummary());
  }

  /** Checks if graceful shutdown is in progress. */
  public boolean isShutdownInProgress() {
    return shutdownInProgress.get();
  }

  /** Forces immediate shutdown (use only in emergency situations). */
  public void forceShutdown() {
    logger.warn("Force shutdown requested - skipping graceful procedures");
    shutdownInProgress.set(true);

    try {
      sqsMessageReceiver.stop();
    } catch (Exception e) {
      logger.error("Error during force shutdown", e);
    }
  }
}
