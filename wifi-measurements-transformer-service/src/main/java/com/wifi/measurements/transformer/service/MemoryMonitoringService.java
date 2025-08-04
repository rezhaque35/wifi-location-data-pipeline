// wifi-measurements-transformer-service/src/main/java/com/wifi/measurements/transformer/service/MemoryMonitoringService.java
package com.wifi.measurements.transformer.service;

import com.wifi.measurements.transformer.config.properties.MemoryManagementConfigurationProperties;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Service for monitoring memory usage, detecting memory pressure, and optimizing performance.
 * 
 * Provides memory monitoring, GC optimization recommendations, batch throttling,
 * and performance profiling for Firehose operations.
 */
@Service
public class MemoryMonitoringService {

    private static final Logger logger = LoggerFactory.getLogger(MemoryMonitoringService.class);

    private final MemoryManagementConfigurationProperties memoryConfig;
    private final MeterRegistry meterRegistry;
    private final MemoryMXBean memoryMXBean;
    
    // Memory monitoring state
    private final AtomicBoolean memoryPressureDetected = new AtomicBoolean(false);
    private final AtomicLong lastGcTime = new AtomicLong(0);
    private final AtomicLong totalGcTime = new AtomicLong(0);
    private final AtomicLong totalGcCollections = new AtomicLong(0);
    private final AtomicReference<MemoryStats> lastMemoryStats = new AtomicReference<>();
    
    // Performance profiling
    private final Timer jsonSerializationTimer;
    private final Timer batchAccumulationTimer;
    private final Timer memoryCheckTimer;

    public MemoryMonitoringService(
            MemoryManagementConfigurationProperties memoryConfig,
            MeterRegistry meterRegistry) {
        this.memoryConfig = memoryConfig;
        this.meterRegistry = meterRegistry;
        this.memoryMXBean = ManagementFactory.getMemoryMXBean();
        
        // Initialize performance timers
        this.jsonSerializationTimer = Timer.builder("memory.json.serialization.duration")
                .description("Time taken for JSON serialization operations")
                .register(meterRegistry);
        this.batchAccumulationTimer = Timer.builder("memory.batch.accumulation.duration")
                .description("Time taken for batch accumulation operations")
                .register(meterRegistry);
        this.memoryCheckTimer = Timer.builder("memory.check.duration")
                .description("Time taken for memory check operations")
                .register(meterRegistry);
    }

    @PostConstruct
    public void initialize() {
        if (!memoryConfig.enabled()) {
            logger.info("Memory monitoring is disabled");
            return;
        }

        logger.info("Initializing memory monitoring service with configuration: " +
                "pressureThreshold={}, maxBatchMemory={} bytes, checkInterval={} ms",
                memoryConfig.memoryPressureThreshold(),
                memoryConfig.maxBatchMemoryBytes(),
                memoryConfig.memoryCheckIntervalMs());

        // Register memory usage gauges
        registerMemoryGauges();
        
        // Initialize GC monitoring
        initializeGcMonitoring();
        
        logger.info("Memory monitoring service initialized successfully");
    }

    /**
     * Scheduled memory monitoring task.
     */
    @Scheduled(fixedDelayString = "#{@memoryManagementConfigurationProperties.memoryCheckIntervalMs}")
    public void checkMemoryUsage() {
        if (!memoryConfig.enabled()) {
            return;
        }

        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            MemoryStats currentStats = getCurrentMemoryStats();
            lastMemoryStats.set(currentStats);
            
            // Check for memory pressure
            boolean previousPressure = memoryPressureDetected.get();
            boolean currentPressure = currentStats.heapUsageRatio() > memoryConfig.memoryPressureThreshold();
            
            if (currentPressure != previousPressure) {
                memoryPressureDetected.set(currentPressure);
                
                if (currentPressure) {
                    if (logger.isWarnEnabled()) {
                        logger.warn("Memory pressure detected - Heap usage: {}% (threshold: {}%)",
                                String.format("%.2f", currentStats.heapUsageRatio() * 100),
                                String.format("%.2f", memoryConfig.memoryPressureThreshold() * 100));
                    }
                    handleMemoryPressure(currentStats);
                } else {
                    if (logger.isInfoEnabled()) {
                        logger.info("Memory pressure relieved - Heap usage: {}%",
                                String.format("%.2f", currentStats.heapUsageRatio() * 100));
                    }
                }
            }
            
            // Log memory statistics periodically
            if (memoryConfig.gcOptimization().logGcEvents() && 
                System.currentTimeMillis() % 30000 < memoryConfig.memoryCheckIntervalMs()) {
                logMemoryStatistics(currentStats);
            }
            
        } finally {
            sample.stop(memoryCheckTimer);
        }
    }

    /**
     * Gets the current memory statistics.
     */
    public MemoryStats getCurrentMemoryStats() {
        MemoryUsage heapUsage = memoryMXBean.getHeapMemoryUsage();
        MemoryUsage nonHeapUsage = memoryMXBean.getNonHeapMemoryUsage();
        
        return new MemoryStats(
            heapUsage.getUsed(),
            heapUsage.getMax(),
            (double) heapUsage.getUsed() / heapUsage.getMax(),
            nonHeapUsage.getUsed(),
            nonHeapUsage.getMax(),
            totalGcCollections.get(),
            totalGcTime.get()
        );
    }

    /**
     * Checks if memory pressure is currently detected.
     */
    public boolean isMemoryPressureDetected() {
        return memoryPressureDetected.get();
    }

    /**
     * Calculates the optimal batch size based on current memory usage.
     */
    public int getOptimalBatchSize(int defaultBatchSize) {
        if (!memoryConfig.enableBatchThrottling() || !isMemoryPressureDetected()) {
            return defaultBatchSize;
        }
        
        MemoryStats stats = lastMemoryStats.get();
        if (stats == null) {
            return defaultBatchSize;
        }
        
        // Calculate reduction factor based on memory pressure
        double pressureRatio = stats.heapUsageRatio() / memoryConfig.memoryPressureThreshold();
        double reductionFactor = Math.max(0.1, 1.0 / pressureRatio);
        
        int throttledSize = (int) (defaultBatchSize * reductionFactor);
        int finalSize = Math.max(memoryConfig.minThrottledBatchSize(), throttledSize);
        
        if (logger.isDebugEnabled()) {
            logger.debug("Calculated optimal batch size: {} (default: {}, reduction factor: {})",
                    finalSize, defaultBatchSize, String.format("%.2f", reductionFactor));
        }
        
        return finalSize;
    }

    /**
     * Creates a timer sample for JSON serialization performance profiling.
     */
    public Timer.Sample startJsonSerializationTimer() {
        return memoryConfig.enablePerformanceProfiling() ? 
                Timer.start(meterRegistry) : null;
    }

    /**
     * Stops the JSON serialization timer.
     */
    public void stopJsonSerializationTimer(Timer.Sample sample) {
        if (sample != null && memoryConfig.enablePerformanceProfiling()) {
            sample.stop(jsonSerializationTimer);
        }
    }

    /**
     * Creates a timer sample for batch accumulation performance profiling.
     */
    public Timer.Sample startBatchAccumulationTimer() {
        return memoryConfig.enablePerformanceProfiling() ? 
                Timer.start(meterRegistry) : null;
    }

    /**
     * Stops the batch accumulation timer.
     */
    public void stopBatchAccumulationTimer(Timer.Sample sample) {
        if (sample != null && memoryConfig.enablePerformanceProfiling()) {
            sample.stop(batchAccumulationTimer);
        }
    }

    private void registerMemoryGauges() {
        // Heap memory gauges
        Gauge.builder("memory.heap.used", this, service -> service.getCurrentMemoryStats().heapUsedBytes())
                .description("Current heap memory usage in bytes")
                .register(meterRegistry);

        Gauge.builder("memory.heap.max", this, service -> service.getCurrentMemoryStats().heapMaxBytes())
                .description("Maximum heap memory in bytes")
                .register(meterRegistry);

        Gauge.builder("memory.heap.usage.ratio", this, service -> service.getCurrentMemoryStats().heapUsageRatio())
                .description("Heap memory usage ratio (0.0-1.0)")
                .register(meterRegistry);

        // Memory pressure gauge
        Gauge.builder("memory.pressure.detected", this, service -> service.isMemoryPressureDetected() ? 1.0 : 0.0)
                .description("Whether memory pressure is currently detected (1.0 for true, 0.0 for false)")
                .register(meterRegistry);

        // GC metrics
        Gauge.builder("memory.gc.collections.total", this, service -> service.totalGcCollections.get())
                .description("Total number of garbage collection operations")
                .register(meterRegistry);

        Gauge.builder("memory.gc.time.total", this, service -> service.totalGcTime.get())
                .description("Total time spent in garbage collection (milliseconds)")
                .register(meterRegistry);
    }

    private void initializeGcMonitoring() {
        // Initialize GC collection counts and times
        for (GarbageCollectorMXBean gcBean : ManagementFactory.getGarbageCollectorMXBeans()) {
            totalGcCollections.addAndGet(gcBean.getCollectionCount());
            totalGcTime.addAndGet(gcBean.getCollectionTime());
        }
        lastGcTime.set(System.currentTimeMillis());
    }

    private void handleMemoryPressure(MemoryStats stats) {
        if (memoryConfig.gcOptimization().enabled()) {
            if (memoryConfig.gcOptimization().suggestGcOnPressure()) {
                logger.warn("Memory pressure detected - consider tuning GC parameters or increasing heap size. Current heap: {} MB / {} MB",
                        stats.heapUsedBytes() / 1024 / 1024, stats.heapMaxBytes() / 1024 / 1024);
            }
            
            // Update GC statistics
            updateGcStatistics();
        }
    }

    private void updateGcStatistics() {
        long totalCollections = 0;
        long totalTime = 0;
        
        for (GarbageCollectorMXBean gcBean : ManagementFactory.getGarbageCollectorMXBeans()) {
            totalCollections += gcBean.getCollectionCount();
            totalTime += gcBean.getCollectionTime();
        }
        
        totalGcCollections.set(totalCollections);
        totalGcTime.set(totalTime);
    }

    private void logMemoryStatistics(MemoryStats stats) {
        logger.info("Memory Statistics - Heap: {} MB / {} MB ({:.1f}%), " +
                "Non-Heap: {} MB, GC: {} collections, {} ms total",
                stats.heapUsedBytes() / 1024 / 1024,
                stats.heapMaxBytes() / 1024 / 1024,
                stats.heapUsageRatio() * 100,
                stats.nonHeapUsedBytes() / 1024 / 1024,
                stats.gcCollections(),
                stats.gcTimeMs());
    }

    @PreDestroy
    public void shutdown() {
        logger.info("Memory monitoring service shutting down");
    }

    /**
     * Record containing current memory usage statistics.
     */
    public record MemoryStats(
        long heapUsedBytes,
        long heapMaxBytes,
        double heapUsageRatio,
        long nonHeapUsedBytes,
        long nonHeapMaxBytes,
        long gcCollections,
        long gcTimeMs
    ) {}
}