// com/wifi/positioning/config/AsyncConfig.java
package com.wifi.positioning.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

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

    /**
     * Creates a custom thread pool executor for async integration processing.
     * Uses bounded queue to prevent memory exhaustion and provides backpressure handling.
     */
    @Bean(name = "integrationAsyncExecutor")
    public Executor integrationAsyncExecutor() {
        IntegrationProperties.Processing.Async asyncConfig = integrationProperties.getProcessing().getAsync();
        
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        // Core pool size - minimum number of threads to keep alive
        executor.setCorePoolSize(asyncConfig.getWorkers());
        
        // Maximum pool size - maximum number of threads
        executor.setMaxPoolSize(asyncConfig.getWorkers());
        
        // Queue capacity - bounded queue for backpressure
        executor.setQueueCapacity(asyncConfig.getQueueCapacity());
        
        // Thread naming for easier debugging
        executor.setThreadNamePrefix("integration-async-");
        
        // Keep alive time for idle threads (in seconds)
        executor.setKeepAliveSeconds(60);
        
        // Allow core threads to timeout when idle
        executor.setAllowCoreThreadTimeOut(true);
        
        // Custom rejection handler for when queue is full
        executor.setRejectedExecutionHandler(new IntegrationRejectedExecutionHandler());
        
        // Wait for tasks to complete before shutdown (up to 30 seconds)
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        
        executor.initialize();
        
        log.info("Initialized async executor - workers: {}, queueCapacity: {}, enabled: {}", 
            asyncConfig.getWorkers(), asyncConfig.getQueueCapacity(), asyncConfig.isEnabled());
        
        return executor;
    }

    /**
     * Custom rejection handler that logs queue overflow scenarios.
     * This handler is called when the thread pool and queue are both full.
     */
    private static class IntegrationRejectedExecutionHandler implements RejectedExecutionHandler {
        
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

