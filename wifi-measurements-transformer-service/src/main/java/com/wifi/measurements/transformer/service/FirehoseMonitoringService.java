// wifi-measurements-transformer-service/src/main/java/com/wifi/measurements/transformer/service/FirehoseMonitoringService.java
package com.wifi.measurements.transformer.service;

import com.wifi.measurements.transformer.config.properties.FirehoseConfigurationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.firehose.FirehoseClient;
import software.amazon.awssdk.services.firehose.model.DescribeDeliveryStreamRequest;
import software.amazon.awssdk.services.firehose.model.DeliveryStreamStatus;
import software.amazon.awssdk.services.firehose.model.FirehoseException;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Comprehensive Firehose monitoring service for tracking delivery activity and health.
 * 
 * <p>This service provides detailed monitoring capabilities for Firehose delivery operations,
 * including connectivity tracking, delivery activity monitoring, stream status validation,
 * and performance metrics collection. It serves as the foundation for Firehose-based health
 * indicators in both readiness and liveness probes.</p>
 * 
 * <p><strong>Monitoring Capabilities:</strong></p>
 * <ul>
 *   <li><strong>Connectivity Monitoring:</strong> Firehose delivery stream accessibility and health</li>
 *   <li><strong>Delivery Activity:</strong> Batch delivery tracking, success/failure metrics</li>
 *   <li><strong>Stream Status:</strong> Delivery stream state monitoring and validation</li>
 *   <li><strong>Performance Metrics:</strong> Delivery times, throughput, error rates</li>
 * </ul>
 * 
 * <p><strong>Health Check Support:</strong></p>
 * <ul>
 *   <li><strong>Readiness:</strong> Stream connectivity, status validation, configuration checks</li>
 *   <li><strong>Liveness:</strong> Delivery activity monitoring, batch processing tracking, stuck detection</li>
 * </ul>
 * 
 * <p><strong>Activity Tracking:</strong></p>
 * <ul>
 *   <li>Last delivery attempt timestamp for idle period detection</li>
 *   <li>Delivery success/failure ratio tracking for reliability assessment</li>
 *   <li>Batch processing rate calculation for performance monitoring</li>
 *   <li>Delivery pipeline health status monitoring</li>
 * </ul>
 * 
 * @author WiFi Location Data Pipeline Team
 * @version 1.0
 * @since 2024
 */
@Service
public class FirehoseMonitoringService {

    private static final Logger logger = LoggerFactory.getLogger(FirehoseMonitoringService.class);

    private final FirehoseClient firehoseClient;
    private final FirehoseConfigurationProperties firehoseConfig;

    // Activity tracking
    private final AtomicReference<Instant> lastDeliveryAttemptTime = new AtomicReference<>(Instant.now());
    private final AtomicReference<Instant> lastSuccessfulDeliveryTime = new AtomicReference<>(Instant.now());
    private final AtomicLong totalBatchesDelivered = new AtomicLong(0);
    private final AtomicLong totalBatchesSucceeded = new AtomicLong(0);
    private final AtomicLong totalBatchesFailed = new AtomicLong(0);
    private final AtomicLong totalRecordsDelivered = new AtomicLong(0);
    private final AtomicLong totalRecordsSucceeded = new AtomicLong(0);
    private final AtomicLong totalRecordsFailed = new AtomicLong(0);

    // Stream state tracking
    private final AtomicReference<Instant> lastSuccessfulStreamCheck = new AtomicReference<>(null);
    private final AtomicReference<DeliveryStreamStatus> lastKnownStreamStatus = new AtomicReference<>(null);
    private final AtomicLong consecutiveStreamCheckFailures = new AtomicLong(0);
    private final AtomicLong consecutiveDeliveryFailures = new AtomicLong(0);

    /**
     * Creates a new FirehoseMonitoringService with required dependencies.
     * 
     * @param firehoseClient AWS Firehose client for stream operations
     * @param firehoseConfig Firehose configuration properties
     */
    public FirehoseMonitoringService(FirehoseClient firehoseClient, FirehoseConfigurationProperties firehoseConfig) {
        this.firehoseClient = firehoseClient;
        this.firehoseConfig = firehoseConfig;
    }

    /**
     * Checks if the Firehose delivery stream is accessible and in ACTIVE state.
     * 
     * @return true if stream is accessible and active, false otherwise
     */
    public boolean isStreamAccessible() {
        if (!firehoseConfig.enabled()) {
            return true; // If disabled, consider it "accessible"
        }

        try {
            DescribeDeliveryStreamRequest request = DescribeDeliveryStreamRequest.builder()
                .deliveryStreamName(firehoseConfig.deliveryStreamName())
                .build();

            var response = firehoseClient.describeDeliveryStream(request);
            DeliveryStreamStatus status = response.deliveryStreamDescription().deliveryStreamStatus();
            
            lastKnownStreamStatus.set(status);
            lastSuccessfulStreamCheck.set(Instant.now());
            consecutiveStreamCheckFailures.set(0);
            
            return status == DeliveryStreamStatus.ACTIVE;

        } catch (FirehoseException e) {
            logger.warn("Firehose stream accessibility check failed: {}", e.getMessage());
            consecutiveStreamCheckFailures.incrementAndGet();
            return false;
        } catch (Exception e) {
            logger.error("Unexpected error during Firehose stream check", e);
            consecutiveStreamCheckFailures.incrementAndGet();
            return false;
        }
    }

    /**
     * Checks if the Firehose delivery stream is ready for deliveries.
     * 
     * @return true if stream is ready, false otherwise
     */
    public boolean isStreamReady() {
        return isStreamAccessible();
    }

    /**
     * Gets the time in milliseconds since the last delivery attempt.
     * 
     * @return milliseconds since last delivery attempt
     */
    public long getTimeSinceLastDeliveryAttempt() {
        Instant lastAttempt = lastDeliveryAttemptTime.get();
        return lastAttempt != null ? 
            java.time.Duration.between(lastAttempt, Instant.now()).toMillis() : 
            Long.MAX_VALUE;
    }

    /**
     * Gets the time in milliseconds since the last successful delivery.
     * 
     * @return milliseconds since last successful delivery
     */
    public long getTimeSinceLastSuccessfulDelivery() {
        Instant lastSuccess = lastSuccessfulDeliveryTime.get();
        return lastSuccess != null ?
            java.time.Duration.between(lastSuccess, Instant.now()).toMillis() :
            Long.MAX_VALUE;
    }

    /**
     * Calculates the current delivery success rate as a percentage.
     * 
     * @return success rate as a decimal (0.0 to 1.0)
     */
    public double getDeliverySuccessRate() {
        long total = totalBatchesDelivered.get();
        if (total == 0) {
            return 1.0; // 100% if no deliveries attempted yet
        }

        long succeeded = totalBatchesSucceeded.get();
        return (double) succeeded / total;
    }

    /**
     * Calculates the current delivery rate in batches per minute.
     * 
     * @return delivery rate in batches per minute
     */
    public double getDeliveryRate() {
        long totalDelivered = totalBatchesDelivered.get();
        if (totalDelivered == 0) {
            return 0.0;
        }

        Instant now = Instant.now();
        Instant startTime = lastDeliveryAttemptTime.get();
        
        if (startTime == null) {
            return 0.0;
        }

        long elapsedMinutes = java.time.Duration.between(startTime, now).toMinutes();
        if (elapsedMinutes == 0) {
            elapsedMinutes = 1; // Avoid division by zero, treat as 1 minute minimum
        }

        return (double) totalDelivered / elapsedMinutes;
    }

    /**
     * Checks if delivery processing appears to be stuck or inactive.
     * 
     * @return true if delivery appears stuck, false otherwise
     */
    public boolean isDeliveryStuck() {
        // Consider delivery stuck if:
        // 1. We have consecutive delivery failures
        // 2. Stream checks are failing repeatedly
        // 3. Long period since last successful delivery when attempts are being made
        
        long consecutiveFailures = consecutiveDeliveryFailures.get();
        long consecutiveStreamFailures = consecutiveStreamCheckFailures.get();
        long timeSinceLastSuccess = getTimeSinceLastSuccessfulDelivery();
        long timeSinceLastAttempt = getTimeSinceLastDeliveryAttempt();

        // If many consecutive failures or stream checks failing
        if (consecutiveFailures > 5 || consecutiveStreamFailures > 3) {
            return true;
        }

        // If we're making attempts but haven't succeeded in a long time
        if (timeSinceLastAttempt < 300000 && timeSinceLastSuccess > 600000) { // Attempting within 5 min but no success in 10 min
            return true;
        }

        return false;
    }

    /**
     * Records that a delivery attempt was made.
     * 
     * @param batchSize number of records in the batch
     */
    public void recordDeliveryAttempt(int batchSize) {
        lastDeliveryAttemptTime.set(Instant.now());
        totalBatchesDelivered.incrementAndGet();
        totalRecordsDelivered.addAndGet(batchSize);
        
        logger.debug("Delivery attempt recorded - batch size: {}, total batches: {}", 
                batchSize, totalBatchesDelivered.get());
    }

    /**
     * Records the result of a delivery attempt.
     * 
     * @param success whether the delivery was successful
     * @param successfulRecords number of records that were successfully delivered
     * @param failedRecords number of records that failed delivery
     */
    public void recordDeliveryResult(boolean success, int successfulRecords, int failedRecords) {
        if (success || successfulRecords > 0) {
            totalBatchesSucceeded.incrementAndGet();
            totalRecordsSucceeded.addAndGet(successfulRecords);
            lastSuccessfulDeliveryTime.set(Instant.now());
            consecutiveDeliveryFailures.set(0);
        }
        
        if (!success || failedRecords > 0) {
            if (!success) {
                totalBatchesFailed.incrementAndGet();
                consecutiveDeliveryFailures.incrementAndGet();
            }
            totalRecordsFailed.addAndGet(failedRecords);
        }
        
        logger.debug("Delivery result recorded - success: {}, successful records: {}, failed records: {}", 
                success, successfulRecords, failedRecords);
    }

    /**
     * Gets comprehensive metrics for monitoring and health checks.
     * 
     * @return Firehose monitoring metrics
     */
    public FirehoseMetrics getMetrics() {
        return new FirehoseMetrics(
            totalBatchesDelivered.get(),
            totalBatchesSucceeded.get(),
            totalBatchesFailed.get(),
            totalRecordsDelivered.get(),
            totalRecordsSucceeded.get(),
            totalRecordsFailed.get(),
            getDeliverySuccessRate(),
            getDeliveryRate(),
            getTimeSinceLastDeliveryAttempt(),
            getTimeSinceLastSuccessfulDelivery(),
            lastSuccessfulStreamCheck.get(),
            lastKnownStreamStatus.get(),
            consecutiveStreamCheckFailures.get(),
            consecutiveDeliveryFailures.get()
        );
    }

    /**
     * Data class containing Firehose monitoring metrics.
     */
    public static class FirehoseMetrics {
        private final long totalBatchesDelivered;
        private final long totalBatchesSucceeded;
        private final long totalBatchesFailed;
        private final long totalRecordsDelivered;
        private final long totalRecordsSucceeded;
        private final long totalRecordsFailed;
        private final double deliverySuccessRate;
        private final double deliveryRate;
        private final long timeSinceLastDeliveryAttempt;
        private final long timeSinceLastSuccessfulDelivery;
        private final Instant lastSuccessfulStreamCheck;
        private final DeliveryStreamStatus lastKnownStreamStatus;
        private final long consecutiveStreamCheckFailures;
        private final long consecutiveDeliveryFailures;

        public FirehoseMetrics(long totalBatchesDelivered, long totalBatchesSucceeded,
                             long totalBatchesFailed, long totalRecordsDelivered,
                             long totalRecordsSucceeded, long totalRecordsFailed,
                             double deliverySuccessRate, double deliveryRate,
                             long timeSinceLastDeliveryAttempt, long timeSinceLastSuccessfulDelivery,
                             Instant lastSuccessfulStreamCheck, DeliveryStreamStatus lastKnownStreamStatus,
                             long consecutiveStreamCheckFailures, long consecutiveDeliveryFailures) {
            this.totalBatchesDelivered = totalBatchesDelivered;
            this.totalBatchesSucceeded = totalBatchesSucceeded;
            this.totalBatchesFailed = totalBatchesFailed;
            this.totalRecordsDelivered = totalRecordsDelivered;
            this.totalRecordsSucceeded = totalRecordsSucceeded;
            this.totalRecordsFailed = totalRecordsFailed;
            this.deliverySuccessRate = deliverySuccessRate;
            this.deliveryRate = deliveryRate;
            this.timeSinceLastDeliveryAttempt = timeSinceLastDeliveryAttempt;
            this.timeSinceLastSuccessfulDelivery = timeSinceLastSuccessfulDelivery;
            this.lastSuccessfulStreamCheck = lastSuccessfulStreamCheck;
            this.lastKnownStreamStatus = lastKnownStreamStatus;
            this.consecutiveStreamCheckFailures = consecutiveStreamCheckFailures;
            this.consecutiveDeliveryFailures = consecutiveDeliveryFailures;
        }

        // Getters
        public long getTotalBatchesDelivered() { return totalBatchesDelivered; }
        public long getTotalBatchesSucceeded() { return totalBatchesSucceeded; }
        public long getTotalBatchesFailed() { return totalBatchesFailed; }
        public long getTotalRecordsDelivered() { return totalRecordsDelivered; }
        public long getTotalRecordsSucceeded() { return totalRecordsSucceeded; }
        public long getTotalRecordsFailed() { return totalRecordsFailed; }
        public double getDeliverySuccessRate() { return deliverySuccessRate; }
        public double getDeliveryRate() { return deliveryRate; }
        public long getTimeSinceLastDeliveryAttempt() { return timeSinceLastDeliveryAttempt; }
        public long getTimeSinceLastSuccessfulDelivery() { return timeSinceLastSuccessfulDelivery; }
        public Instant getLastSuccessfulStreamCheck() { return lastSuccessfulStreamCheck; }
        public DeliveryStreamStatus getLastKnownStreamStatus() { return lastKnownStreamStatus; }
        public long getConsecutiveStreamCheckFailures() { return consecutiveStreamCheckFailures; }
        public long getConsecutiveDeliveryFailures() { return consecutiveDeliveryFailures; }
    }
}