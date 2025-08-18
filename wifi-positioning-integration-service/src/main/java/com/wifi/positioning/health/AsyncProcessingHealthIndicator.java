package com.wifi.positioning.health;

import com.wifi.positioning.config.IntegrationProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Custom health endpoint for async processing metrics that doesn't affect overall service health.
 * This provides detailed monitoring of the async processing thread pool and queue without
 * impacting the main /actuator/health endpoint status.
 * 
 * <p>Available via /actuator/async-processing endpoint</p>
 * 
 * <p><strong>Metrics Provided:</strong>
 * <ul>
 *   <li><strong>Queue Depth:</strong> Current number of tasks waiting in the queue</li>
 *   <li><strong>Active Threads:</strong> Number of threads currently processing tasks</li>
 *   <li><strong>Pool Size:</strong> Current thread pool size</li>
 *   <li><strong>Completed Tasks:</strong> Total number of completed tasks since startup</li>
 *   <li><strong>Rejected Tasks:</strong> Number of tasks rejected due to queue overflow</li>
 *   <li><strong>Configuration:</strong> Current async processing configuration</li>
 * </ul>
 * 
 * <p><strong>Health Determination:</strong>
 * <ul>
 *   <li><strong>HEALTHY:</strong> Queue under 80% capacity, threads available</li>
 *   <li><strong>DEGRADED:</strong> Queue 80-95% full or high thread utilization</li>
 *   <li><strong>CRITICAL:</strong> Queue >95% full or all threads busy for extended time</li>
 * </ul>
 */
@Component
@Endpoint(id = "async-processing")
@RequiredArgsConstructor
@Slf4j
public class AsyncProcessingHealthIndicator {

    private final IntegrationProperties integrationProperties;
    private final ApplicationContext applicationContext;
    
    // Counters for tracking async processing metrics
    private final AtomicLong successfulProcessingCount = new AtomicLong(0);
    private final AtomicLong failedProcessingCount = new AtomicLong(0);
    private final AtomicLong rejectedTaskCount = new AtomicLong(0);
    
    // Tracking for health status determination
    private volatile long lastHealthyTimestamp = System.currentTimeMillis();
    private volatile String lastHealthStatus = "HEALTHY";

    @ReadOperation
    public Map<String, Object> asyncProcessingHealth() {
        try {
            long checkTimestamp = System.currentTimeMillis();
            
            // Get thread pool executor for detailed metrics - lookup to avoid circular dependency
            ThreadPoolTaskExecutor taskExecutor = getAsyncExecutor();
            if (taskExecutor == null) {
                return buildErrorResponse("Async executor not found", checkTimestamp);
            }
            
            ThreadPoolExecutor executor = taskExecutor.getThreadPoolExecutor();
            IntegrationProperties.Processing.Async config = integrationProperties.getProcessing().getAsync();
            
            // Calculate current metrics
            int queueSize = executor.getQueue().size();
            int activeThreads = executor.getActiveCount();
            int poolSize = executor.getPoolSize();
            long completedTasks = executor.getCompletedTaskCount();
            int maxPoolSize = executor.getMaximumPoolSize();
            int corePoolSize = executor.getCorePoolSize();
            
            // Calculate health status and percentages
            double queueUtilization = config.getQueueCapacity() > 0 ? 
                (double) queueSize / config.getQueueCapacity() * 100.0 : 0.0;
            double threadUtilization = maxPoolSize > 0 ? 
                (double) activeThreads / maxPoolSize * 100.0 : 0.0;
            
            String healthStatus = determineHealthStatus(queueUtilization, threadUtilization, config.isEnabled());
            updateHealthTracking(healthStatus, checkTimestamp);
            
            // Build comprehensive metrics response
            Map<String, Object> health = new HashMap<>();
            
            // Overall status
            health.put("status", healthStatus);
            health.put("enabled", config.isEnabled());
            health.put("checkTimestamp", Instant.ofEpochMilli(checkTimestamp));
            health.put("lastHealthyAt", Instant.ofEpochMilli(lastHealthyTimestamp));
            
            // Queue metrics
            Map<String, Object> queueMetrics = new HashMap<>();
            queueMetrics.put("size", queueSize);
            queueMetrics.put("capacity", config.getQueueCapacity());
            queueMetrics.put("utilizationPercent", Math.round(queueUtilization * 100.0) / 100.0);
            queueMetrics.put("availableCapacity", config.getQueueCapacity() - queueSize);
            health.put("queue", queueMetrics);
            
            // Thread pool metrics
            Map<String, Object> threadPoolMetrics = new HashMap<>();
            threadPoolMetrics.put("activeThreads", activeThreads);
            threadPoolMetrics.put("poolSize", poolSize);
            threadPoolMetrics.put("corePoolSize", corePoolSize);
            threadPoolMetrics.put("maxPoolSize", maxPoolSize);
            threadPoolMetrics.put("threadUtilizationPercent", Math.round(threadUtilization * 100.0) / 100.0);
            threadPoolMetrics.put("availableThreads", maxPoolSize - activeThreads);
            health.put("threadPool", threadPoolMetrics);
            
            // Processing metrics
            Map<String, Object> processingMetrics = new HashMap<>();
            processingMetrics.put("completedTasks", completedTasks);
            processingMetrics.put("successfulProcessing", successfulProcessingCount.get());
            processingMetrics.put("failedProcessing", failedProcessingCount.get());
            processingMetrics.put("rejectedTasks", rejectedTaskCount.get());
            
            long totalProcessing = successfulProcessingCount.get() + failedProcessingCount.get();
            double successRate = totalProcessing > 0 ? 
                (double) successfulProcessingCount.get() / totalProcessing * 100.0 : 0.0;
            processingMetrics.put("successRatePercent", Math.round(successRate * 100.0) / 100.0);
            health.put("processing", processingMetrics);
            
            // Configuration details
            Map<String, Object> configMetrics = new HashMap<>();
            configMetrics.put("workers", config.getWorkers());
            configMetrics.put("queueCapacity", config.getQueueCapacity());
            configMetrics.put("enabled", config.isEnabled());
            health.put("configuration", configMetrics);
            
            // Health indicators and warnings
            Map<String, Object> indicators = new HashMap<>();
            indicators.put("queueNearCapacity", queueUtilization > 80.0);
            indicators.put("highThreadUtilization", threadUtilization > 80.0);
            indicators.put("recentRejections", rejectedTaskCount.get() > 0);
            indicators.put("processingErrors", failedProcessingCount.get() > 0);
            health.put("indicators", indicators);
            
            // Add warnings if needed
            if (queueUtilization > 95.0) {
                health.put("warning", "Queue utilization is critically high (>95%)");
            } else if (queueUtilization > 80.0) {
                health.put("warning", "Queue utilization is high (>80%)");
            } else if (threadUtilization > 90.0) {
                health.put("warning", "Thread utilization is very high (>90%)");
            }
            
            log.debug("Async processing health check completed - status: {}, queueSize: {}, activeThreads: {}", 
                healthStatus, queueSize, activeThreads);
            
            return health;
            
        } catch (Exception e) {
            log.error("Error checking async processing health", e);
            return buildErrorResponse(e.getMessage(), System.currentTimeMillis());
        }
    }
    
    /**
     * Gets the async executor bean from application context to avoid circular dependency.
     */
    private ThreadPoolTaskExecutor getAsyncExecutor() {
        try {
            return applicationContext.getBean("integrationAsyncExecutor", ThreadPoolTaskExecutor.class);
        } catch (Exception e) {
            log.warn("Failed to get async executor bean: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Builds error response for health check failures.
     */
    private Map<String, Object> buildErrorResponse(String error, long timestamp) {
        Map<String, Object> errorHealth = new HashMap<>();
        errorHealth.put("status", "ERROR");
        errorHealth.put("error", error);
        errorHealth.put("checkTimestamp", Instant.ofEpochMilli(timestamp));
        return errorHealth;
    }
    
    /**
     * Determines health status based on queue and thread utilization.
     */
    private String determineHealthStatus(double queueUtilization, double threadUtilization, boolean enabled) {
        if (!enabled) {
            return "DISABLED";
        }
        
        if (queueUtilization > 95.0 || (threadUtilization > 95.0 && queueUtilization > 50.0)) {
            return "CRITICAL";
        } else if (queueUtilization > 80.0 || threadUtilization > 85.0) {
            return "DEGRADED";
        } else {
            return "HEALTHY";
        }
    }
    
    /**
     * Updates health tracking timestamps.
     */
    private void updateHealthTracking(String currentStatus, long timestamp) {
        if ("HEALTHY".equals(currentStatus)) {
            lastHealthyTimestamp = timestamp;
        }
        lastHealthStatus = currentStatus;
    }
    
    /**
     * Increments successful processing counter.
     * Should be called by async processing services when processing succeeds.
     */
    public void incrementSuccessfulProcessing() {
        successfulProcessingCount.incrementAndGet();
    }
    
    /**
     * Increments failed processing counter.
     * Should be called by async processing services when processing fails.
     */
    public void incrementFailedProcessing() {
        failedProcessingCount.incrementAndGet();
    }
    
    /**
     * Increments rejected task counter.
     * Should be called when tasks are rejected due to queue overflow.
     */
    public void incrementRejectedTasks() {
        rejectedTaskCount.incrementAndGet();
    }
    
    /**
     * Resets all counters. Useful for testing or administrative operations.
     */
    public void resetCounters() {
        successfulProcessingCount.set(0);
        failedProcessingCount.set(0);
        rejectedTaskCount.set(0);
        lastHealthyTimestamp = System.currentTimeMillis();
        lastHealthStatus = "HEALTHY";
        log.info("Reset async processing health counters");
    }
}
