// wifi-measurements-transformer-service/src/main/java/com/wifi/measurements/transformer/service/ProcessingMetricsService.java
package com.wifi.measurements.transformer.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.DistributionSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Comprehensive metrics service for tracking processing performance and Firehose operations.
 * 
 * <p>This service provides detailed metrics collection for all aspects of the WiFi measurement
 * processing pipeline, including file processing, record transformation, Firehose operations,
 * and overall system performance.</p>
 * 
 * <p><strong>Metric Categories:</strong></p>
 * <ul>
 *   <li><strong>File Processing:</strong> Files processed, sizes, processing times</li>
 *   <li><strong>Record Processing:</strong> Records transformed, validation outcomes, errors</li>
 *   <li><strong>Firehose Operations:</strong> Batch delivery, success/failure rates, latency</li>
 *   <li><strong>System Performance:</strong> Memory usage, processing throughput, error rates</li>
 * </ul>
 * 
 * <p><strong>Performance Tracking:</strong></p>
 * <ul>
 *   <li>End-to-end processing duration from S3 download to Firehose delivery</li>
 *   <li>Component-level timing for optimization opportunities</li>
 *   <li>Throughput metrics for capacity planning</li>
 *   <li>Error rate tracking for reliability monitoring</li>
 * </ul>
 */
@Service
public class ProcessingMetricsService {

    private static final Logger logger = LoggerFactory.getLogger(ProcessingMetricsService.class);

    private final MeterRegistry meterRegistry;

    // File Processing Metrics
    private final Counter filesProcessedCounter;
    private final Counter filesFailedCounter;
    private final Timer fileProcessingTimer;
    private final DistributionSummary fileSizeDistribution;
    private final Counter fileDownloadErrors;

    // Record Processing Metrics
    private final Counter recordsProcessedCounter;
    private final Counter recordsValidatedCounter;
    private final Counter recordsFilteredCounter;
    private final Counter recordsRejectedCounter;
    private final Timer recordTransformationTimer;
    private final DistributionSummary recordsPerFileDistribution;

    // Firehose Metrics
    private final Counter firehoseBatchesDeliveredCounter;
    private final Counter firehoseBatchesFailedCounter;
    private final Counter firehoseRecordsDeliveredCounter;
    private final Counter firehoseRecordsFailedCounter;
    private final Timer firehoseBatchDeliveryTimer;
    private final DistributionSummary firehoseBatchSizeDistribution;
    private final DistributionSummary firehoseBatchSizeBytesDistribution;
    private final Counter firehoseRetryCounter;

    // System Performance Metrics
    private final Timer endToEndProcessingTimer;
    private final Counter processingErrorsCounter;
    private final DistributionSummary processingThroughputDistribution;
    
    // Current state tracking
    private final AtomicLong currentlyProcessingFiles = new AtomicLong(0);
    private final AtomicLong totalBytesProcessed = new AtomicLong(0);
    private final AtomicLong totalRecordsProcessed = new AtomicLong(0);
    private final AtomicReference<Instant> lastProcessingTime = new AtomicReference<>(Instant.now());

    public ProcessingMetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        // Initialize File Processing Metrics
        this.filesProcessedCounter = Counter.builder("processing.files.processed.total")
            .description("Total number of files processed successfully")
            .register(meterRegistry);

        this.filesFailedCounter = Counter.builder("processing.files.failed.total")
            .description("Total number of files that failed processing")
            .register(meterRegistry);

        this.fileProcessingTimer = Timer.builder("processing.files.duration")
            .description("Time taken to process individual files")
            .register(meterRegistry);

        this.fileSizeDistribution = DistributionSummary.builder("processing.files.size.bytes")
            .description("Distribution of file sizes being processed")
            .baseUnit("bytes")
            .register(meterRegistry);

        this.fileDownloadErrors = Counter.builder("processing.files.download.errors.total")
            .description("Total number of file download errors")
            .register(meterRegistry);

        // Initialize Record Processing Metrics
        this.recordsProcessedCounter = Counter.builder("processing.records.processed.total")
            .description("Total number of records processed")
            .register(meterRegistry);

        this.recordsValidatedCounter = Counter.builder("processing.records.validated.total")
            .description("Total number of records that passed validation")
            .register(meterRegistry);

        this.recordsFilteredCounter = Counter.builder("processing.records.filtered.total")
            .description("Total number of records filtered out")
            .register(meterRegistry);

        this.recordsRejectedCounter = Counter.builder("processing.records.rejected.total")
            .description("Total number of records rejected due to validation failures")
            .register(meterRegistry);

        this.recordTransformationTimer = Timer.builder("processing.records.transformation.duration")
            .description("Time taken to transform individual records")
            .register(meterRegistry);

        this.recordsPerFileDistribution = DistributionSummary.builder("processing.records.per.file")
            .description("Distribution of number of records per file")
            .register(meterRegistry);

        // Initialize Firehose Metrics
        this.firehoseBatchesDeliveredCounter = Counter.builder("firehose.batches.delivered.total")
            .description("Total number of batches successfully delivered to Firehose")
            .register(meterRegistry);

        this.firehoseBatchesFailedCounter = Counter.builder("firehose.batches.failed.total")
            .description("Total number of batches that failed to deliver to Firehose")
            .register(meterRegistry);

        this.firehoseRecordsDeliveredCounter = Counter.builder("firehose.records.delivered.total")
            .description("Total number of records successfully delivered to Firehose")
            .register(meterRegistry);

        this.firehoseRecordsFailedCounter = Counter.builder("firehose.records.failed.total")
            .description("Total number of records that failed to deliver to Firehose")
            .register(meterRegistry);

        this.firehoseBatchDeliveryTimer = Timer.builder("firehose.batch.delivery.duration")
            .description("Time taken to deliver batches to Firehose")
            .register(meterRegistry);

        this.firehoseBatchSizeDistribution = DistributionSummary.builder("firehose.batch.size.records")
            .description("Distribution of batch sizes in number of records")
            .register(meterRegistry);

        this.firehoseBatchSizeBytesDistribution = DistributionSummary.builder("firehose.batch.size.bytes")
            .description("Distribution of batch sizes in bytes")
            .baseUnit("bytes")
            .register(meterRegistry);

        this.firehoseRetryCounter = Counter.builder("firehose.retries.total")
            .description("Total number of Firehose delivery retries")
            .register(meterRegistry);

        // Initialize System Performance Metrics
        this.endToEndProcessingTimer = Timer.builder("processing.end.to.end.duration")
            .description("Total time from file receipt to Firehose delivery")
            .register(meterRegistry);

        this.processingErrorsCounter = Counter.builder("processing.errors.total")
            .description("Total number of processing errors")
            .register(meterRegistry);

        this.processingThroughputDistribution = DistributionSummary.builder("processing.throughput.records.per.second")
            .description("Processing throughput in records per second")
            .baseUnit("records/second")
            .register(meterRegistry);
    }

    @PostConstruct
    private void registerGauges() {
        // Current state gauges
        Gauge.builder("processing.files.currently.processing", this, 
                service -> service.currentlyProcessingFiles.get())
            .description("Number of files currently being processed")
            .register(meterRegistry);

        Gauge.builder("processing.bytes.processed.total", this, 
                service -> service.totalBytesProcessed.get())
            .description("Total bytes processed since startup")
            .baseUnit("bytes")
            .register(meterRegistry);

        Gauge.builder("processing.records.processed.total.counter", this, 
                service -> service.totalRecordsProcessed.get())
            .description("Total records processed since startup")
            .register(meterRegistry);

        Gauge.builder("processing.last.processing.time.seconds", this, service -> {
                Instant last = service.lastProcessingTime.get();
                return last != null ? Duration.between(last, Instant.now()).getSeconds() : -1;
            })
            .description("Seconds since last processing activity")
            .register(meterRegistry);
    }

    // File Processing Metrics Methods
    public Timer.Sample startFileProcessing() {
        currentlyProcessingFiles.incrementAndGet();
        lastProcessingTime.set(Instant.now());
        return Timer.start(meterRegistry);
    }

    public void recordFileProcessed(Timer.Sample sample, long fileSizeBytes, int recordCount) {
        sample.stop(fileProcessingTimer);
        currentlyProcessingFiles.decrementAndGet();
        filesProcessedCounter.increment();
        fileSizeDistribution.record(fileSizeBytes);
        recordsPerFileDistribution.record(recordCount);
        totalBytesProcessed.addAndGet(fileSizeBytes);
        lastProcessingTime.set(Instant.now());
        
        logger.debug("File processed: {} bytes, {} records", fileSizeBytes, recordCount);
    }

    public void recordFileProcessingFailed(Timer.Sample sample, String reason) {
        if (sample != null) {
            sample.stop(fileProcessingTimer);
        }
        currentlyProcessingFiles.decrementAndGet();
        filesFailedCounter.increment();
        processingErrorsCounter.increment();
        lastProcessingTime.set(Instant.now());
        
        logger.warn("File processing failed: {}", reason);
    }

    public void recordFileDownloadError() {
        fileDownloadErrors.increment();
        processingErrorsCounter.increment();
    }

    // Record Processing Metrics Methods
    public Timer.Sample startRecordTransformation() {
        return Timer.start(meterRegistry);
    }

    public void recordRecordProcessed(Timer.Sample sample) {
        if (sample != null) {
            sample.stop(recordTransformationTimer);
        }
        recordsProcessedCounter.increment();
        totalRecordsProcessed.incrementAndGet();
        lastProcessingTime.set(Instant.now());
    }

    public void recordRecordValidated() {
        recordsValidatedCounter.increment();
    }

    public void recordRecordFiltered(String reason) {
        recordsFilteredCounter.increment();
        logger.debug("Record filtered: {}", reason);
    }

    public void recordRecordRejected(String reason) {
        recordsRejectedCounter.increment();
        processingErrorsCounter.increment();
        logger.debug("Record rejected: {}", reason);
    }

    // Firehose Metrics Methods
    public Timer.Sample startFirehoseBatchDelivery() {
        return Timer.start(meterRegistry);
    }

    public void recordFirehoseBatchDelivered(Timer.Sample sample, int recordCount, long batchSizeBytes) {
        if (sample != null) {
            sample.stop(firehoseBatchDeliveryTimer);
        }
        firehoseBatchesDeliveredCounter.increment();
        firehoseRecordsDeliveredCounter.increment(recordCount);
        firehoseBatchSizeDistribution.record(recordCount);
        firehoseBatchSizeBytesDistribution.record(batchSizeBytes);
        lastProcessingTime.set(Instant.now());
        
        logger.debug("Firehose batch delivered: {} records, {} bytes", recordCount, batchSizeBytes);
    }

    public void recordFirehoseBatchFailed(Timer.Sample sample, int recordCount, String reason) {
        if (sample != null) {
            sample.stop(firehoseBatchDeliveryTimer);
        }
        firehoseBatchesFailedCounter.increment();
        firehoseRecordsFailedCounter.increment(recordCount);
        processingErrorsCounter.increment();
        
        logger.error("Firehose batch delivery failed: {} records, reason: {}", recordCount, reason);
    }

    public void recordFirehoseRetry() {
        firehoseRetryCounter.increment();
    }

    // System Performance Metrics Methods
    public Timer.Sample startEndToEndProcessing() {
        return Timer.start(meterRegistry);
    }

    public void recordEndToEndProcessing(Timer.Sample sample, int recordCount, Duration processingDuration) {
        if (sample != null) {
            sample.stop(endToEndProcessingTimer);
        }
        
        // Calculate throughput
        if (processingDuration.toMillis() > 0) {
            double throughput = (double) recordCount / processingDuration.toSeconds();
            processingThroughputDistribution.record(throughput);
        }
        
        lastProcessingTime.set(Instant.now());
        logger.info("End-to-end processing completed: {} records in {}ms", 
                   recordCount, processingDuration.toMillis());
    }

    public void recordProcessingError(String component, String reason) {
        processingErrorsCounter.increment();
        logger.error("Processing error in {}: {}", component, reason);
    }

    // Utility Methods
    public double getFilesProcessedRate() {
        return filesProcessedCounter.count();
    }

    public double getRecordsProcessedRate() {
        return recordsProcessedCounter.count();
    }

    public double getFirehoseBatchSuccessRate() {
        double delivered = firehoseBatchesDeliveredCounter.count();
        double failed = firehoseBatchesFailedCounter.count();
        double total = delivered + failed;
        return total > 0 ? delivered / total : 0.0;
    }

    public long getCurrentlyProcessingFiles() {
        return currentlyProcessingFiles.get();
    }

    public String getMetricsSummary() {
        return String.format(
            "Processing Metrics Summary: " +
            "Files[Processed: %.0f, Failed: %.0f, Currently: %d] " +
            "Records[Processed: %.0f, Validated: %.0f, Filtered: %.0f, Rejected: %.0f] " +
            "Firehose[Batches: %.0f, Records: %.0f, Success Rate: %.2f%%] " +
            "Errors: %.0f",
            filesProcessedCounter.count(),
            filesFailedCounter.count(),
            currentlyProcessingFiles.get(),
            recordsProcessedCounter.count(),
            recordsValidatedCounter.count(),
            recordsFilteredCounter.count(),
            recordsRejectedCounter.count(),
            firehoseBatchesDeliveredCounter.count(),
            firehoseRecordsDeliveredCounter.count(),
            getFirehoseBatchSuccessRate() * 100,
            processingErrorsCounter.count()
        );
    }
}