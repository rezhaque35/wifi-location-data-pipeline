// com/wifi/positioning/config/AsyncConfig.java
package com.wifi.positioning.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Configuration for asynchronous processing in the integration service.
 * Provides a custom thread pool executor for handling integration reports asynchronously.
 */
@Configuration
@EnableAsync
@RequiredArgsConstructor
@Slf4j
public class AsyncConfig {

    private final IntegrationProperties integrationProperties;
    
    // Keep reference to executor for graceful shutdown
    private ThreadPoolTaskExecutor asyncExecutor;

    /**
     * Creates a custom thread pool executor for async integration processing.
     * Uses bounded queue to prevent memory exhaustion and provides backpressure handling.
     */
    @Bean(name = "integrationAsyncExecutor")
    public ThreadPoolTaskExecutor integrationAsyncExecutor() {
        IntegrationProperties.Processing.Async asyncConfig = integrationProperties.getProcessing().getAsync();
        
        asyncExecutor = new ThreadPoolTaskExecutor();
        
        // Core pool size - minimum number of threads to keep alive
        asyncExecutor.setCorePoolSize(asyncConfig.getWorkers());
        
        // Maximum pool size - maximum number of threads
        asyncExecutor.setMaxPoolSize(asyncConfig.getWorkers());
        
        // Queue capacity - bounded queue for backpressure
        asyncExecutor.setQueueCapacity(asyncConfig.getQueueCapacity());
        
        // Thread naming for easier debugging
        asyncExecutor.setThreadNamePrefix("integration-async-");
        
        // Keep alive time for idle threads (in seconds)
        asyncExecutor.setKeepAliveSeconds(60);
        
        // Allow core threads to timeout when idle
        asyncExecutor.setAllowCoreThreadTimeOut(true);
        
        // Custom rejection handler for when queue is full
        asyncExecutor.setRejectedExecutionHandler(new IntegrationRejectedExecutionHandler());
        
        // Graceful shutdown configuration
        asyncExecutor.setWaitForTasksToCompleteOnShutdown(true);
        asyncExecutor.setAwaitTerminationSeconds(30);
        
        asyncExecutor.initialize();
        
        log.info("Initialized async executor - workers: {}, queueCapacity: {}, enabled: {}", 
            asyncConfig.getWorkers(), asyncConfig.getQueueCapacity(), asyncConfig.isEnabled());
        
        return asyncExecutor;
    }

    /**
     * Graceful shutdown hook for async processing.
     * Ensures all queued tasks are completed before application shutdown.
     */
    @PreDestroy
    public void shutdown() {
        if (asyncExecutor != null) {
            log.info("Initiating graceful shutdown of async processing...");
            
            // Get current queue size for logging
            ThreadPoolExecutor executor = asyncExecutor.getThreadPoolExecutor();
            int queueSize = executor.getQueue().size();
            int activeThreads = executor.getActiveCount();
            
            log.info("Shutdown initiated - Queue size: {}, Active threads: {}", queueSize, activeThreads);
            
            try {
                // Stop accepting new tasks
                asyncExecutor.shutdown();
                
                // Wait for existing tasks to complete (up to 45 seconds total)
                if (!asyncExecutor.getThreadPoolExecutor().awaitTermination(45, TimeUnit.SECONDS)) {
                    log.warn("Async processing did not complete within 45 seconds, forcing shutdown");
                    asyncExecutor.getThreadPoolExecutor().shutdownNow();
                    
                    // Wait a bit more for forced shutdown
                    if (!asyncExecutor.getThreadPoolExecutor().awaitTermination(10, TimeUnit.SECONDS)) {
                        log.error("Async processing tasks did not terminate after forced shutdown");
                    }
                } else {
                    log.info("Async processing shutdown completed gracefully");
                }
                
            } catch (InterruptedException e) {
                log.warn("Shutdown interrupted, forcing immediate termination");
                asyncExecutor.getThreadPoolExecutor().shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Custom rejection handler that logs queue overflow scenarios and updates health metrics.
     * This handler is called when the thread pool and queue are both full.
     */
    private class IntegrationRejectedExecutionHandler implements RejectedExecutionHandler {
        
        @Override
        public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
            log.warn("Integration async queue is full - rejecting task. " +
                    "Active threads: {}, Queue size: {}, Pool size: {}", 
                executor.getActiveCount(), executor.getQueue().size(), executor.getPoolSize());
            
            // Throw exception to be handled by the caller
            throw new AsyncQueueFullException("Integration async processing queue is full. " +
                "Active threads: " + executor.getActiveCount() + 
                ", Queue size: " + executor.getQueue().size());
        }
    }

    /**
     * Exception thrown when the async processing queue is full.
     */
    public static class AsyncQueueFullException extends RuntimeException {
        public AsyncQueueFullException(String message) {
            super(message);
        }
    }
}

