// com/wifi/positioning/health/AsyncProcessingHealthIndicatorTest.java
package com.wifi.positioning.health;

import com.wifi.positioning.config.IntegrationProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.LinkedBlockingQueue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Unit tests for AsyncProcessingHealthIndicator.
 */
@ExtendWith(MockitoExtension.class)
class AsyncProcessingHealthIndicatorTest {

    @Mock
    private IntegrationProperties integrationProperties;
    
    @Mock
    private IntegrationProperties.Processing processing;
    
    @Mock
    private IntegrationProperties.Processing.Async asyncConfig;
    
    @Mock
    private ApplicationContext applicationContext;
    
    @Mock
    private ThreadPoolTaskExecutor taskExecutor;
    
    @Mock
    private ThreadPoolExecutor threadPoolExecutor;
    
    private AsyncProcessingHealthIndicator healthIndicator;
    
    @BeforeEach
    void setUp() {
        when(integrationProperties.getProcessing()).thenReturn(processing);
        when(processing.getAsync()).thenReturn(asyncConfig);
        when(taskExecutor.getThreadPoolExecutor()).thenReturn(threadPoolExecutor);
        when(applicationContext.getBean(eq("integrationAsyncExecutor"), eq(ThreadPoolTaskExecutor.class)))
            .thenReturn(taskExecutor);
        
        healthIndicator = new AsyncProcessingHealthIndicator(integrationProperties, applicationContext);
    }
    
    @Test
    void asyncProcessingHealth_WhenEnabled_ReturnsHealthyStatus() {
        // Given
        when(asyncConfig.isEnabled()).thenReturn(true);
        when(asyncConfig.getQueueCapacity()).thenReturn(1000);
        when(asyncConfig.getWorkers()).thenReturn(4);
        
        when(threadPoolExecutor.getQueue()).thenReturn(new LinkedBlockingQueue<>());
        when(threadPoolExecutor.getActiveCount()).thenReturn(1);
        when(threadPoolExecutor.getPoolSize()).thenReturn(2);
        when(threadPoolExecutor.getCompletedTaskCount()).thenReturn(100L);
        when(threadPoolExecutor.getMaximumPoolSize()).thenReturn(4);
        when(threadPoolExecutor.getCorePoolSize()).thenReturn(4);
        
        // When
        Map<String, Object> health = healthIndicator.asyncProcessingHealth();
        
        // Then
        assertThat(health.get("status")).isEqualTo("HEALTHY");
        assertThat(health.get("enabled")).isEqualTo(true);
        
        @SuppressWarnings("unchecked")
        Map<String, Object> queueMetrics = (Map<String, Object>) health.get("queue");
        assertThat(queueMetrics.get("size")).isEqualTo(0);
        assertThat(queueMetrics.get("capacity")).isEqualTo(1000);
        assertThat(queueMetrics.get("utilizationPercent")).isEqualTo(0.0);
        
        @SuppressWarnings("unchecked")
        Map<String, Object> threadPoolMetrics = (Map<String, Object>) health.get("threadPool");
        assertThat(threadPoolMetrics.get("activeThreads")).isEqualTo(1);
        assertThat(threadPoolMetrics.get("maxPoolSize")).isEqualTo(4);
        assertThat(threadPoolMetrics.get("threadUtilizationPercent")).isEqualTo(25.0);
    }
    
    @Test
    void asyncProcessingHealth_WhenDisabled_ReturnsDisabledStatus() {
        // Given
        when(asyncConfig.isEnabled()).thenReturn(false);
        when(asyncConfig.getQueueCapacity()).thenReturn(1000);
        
        when(threadPoolExecutor.getQueue()).thenReturn(new LinkedBlockingQueue<>());
        when(threadPoolExecutor.getActiveCount()).thenReturn(0);
        when(threadPoolExecutor.getPoolSize()).thenReturn(0);
        when(threadPoolExecutor.getCompletedTaskCount()).thenReturn(0L);
        when(threadPoolExecutor.getMaximumPoolSize()).thenReturn(4);
        when(threadPoolExecutor.getCorePoolSize()).thenReturn(4);
        
        // When
        Map<String, Object> health = healthIndicator.asyncProcessingHealth();
        
        // Then
        assertThat(health.get("status")).isEqualTo("DISABLED");
        assertThat(health.get("enabled")).isEqualTo(false);
    }
    
    @Test
    void asyncProcessingHealth_WhenQueueAlmostFull_ReturnsDegradedStatus() {
        // Given
        when(asyncConfig.isEnabled()).thenReturn(true);
        when(asyncConfig.getQueueCapacity()).thenReturn(100);
        when(asyncConfig.getWorkers()).thenReturn(4);
        
        // Create a queue with 85 items (85% full)
        LinkedBlockingQueue<Runnable> queue = new LinkedBlockingQueue<>();
        for (int i = 0; i < 85; i++) {
            queue.offer(() -> {});
        }
        
        when(threadPoolExecutor.getQueue()).thenReturn(queue);
        when(threadPoolExecutor.getActiveCount()).thenReturn(2);
        when(threadPoolExecutor.getPoolSize()).thenReturn(4);
        when(threadPoolExecutor.getCompletedTaskCount()).thenReturn(200L);
        when(threadPoolExecutor.getMaximumPoolSize()).thenReturn(4);
        when(threadPoolExecutor.getCorePoolSize()).thenReturn(4);
        
        // When
        Map<String, Object> health = healthIndicator.asyncProcessingHealth();
        
        // Then
        assertThat(health.get("status")).isEqualTo("DEGRADED");
        
        @SuppressWarnings("unchecked")
        Map<String, Object> queueMetrics = (Map<String, Object>) health.get("queue");
        assertThat(queueMetrics.get("size")).isEqualTo(85);
        assertThat(queueMetrics.get("utilizationPercent")).isEqualTo(85.0);
        
        assertThat(health.get("warning")).asString().contains("high");
    }
    
    @Test
    void asyncProcessingHealth_WhenQueueFull_ReturnsCriticalStatus() {
        // Given
        when(asyncConfig.isEnabled()).thenReturn(true);
        when(asyncConfig.getQueueCapacity()).thenReturn(100);
        when(asyncConfig.getWorkers()).thenReturn(4);
        
        // Create a queue with 97 items (97% full)
        LinkedBlockingQueue<Runnable> queue = new LinkedBlockingQueue<>();
        for (int i = 0; i < 97; i++) {
            queue.offer(() -> {});
        }
        
        when(threadPoolExecutor.getQueue()).thenReturn(queue);
        when(threadPoolExecutor.getActiveCount()).thenReturn(4);
        when(threadPoolExecutor.getPoolSize()).thenReturn(4);
        when(threadPoolExecutor.getCompletedTaskCount()).thenReturn(500L);
        when(threadPoolExecutor.getMaximumPoolSize()).thenReturn(4);
        when(threadPoolExecutor.getCorePoolSize()).thenReturn(4);
        
        // When
        Map<String, Object> health = healthIndicator.asyncProcessingHealth();
        
        // Then
        assertThat(health.get("status")).isEqualTo("CRITICAL");
        
        @SuppressWarnings("unchecked")
        Map<String, Object> queueMetrics = (Map<String, Object>) health.get("queue");
        assertThat(queueMetrics.get("size")).isEqualTo(97);
        assertThat(queueMetrics.get("utilizationPercent")).isEqualTo(97.0);
        
        assertThat(health.get("warning")).asString().contains("critically high");
    }
    
    @Test
    void incrementCounters_UpdatesMetricsCorrectly() {
        // Given
        when(asyncConfig.isEnabled()).thenReturn(true);
        when(asyncConfig.getQueueCapacity()).thenReturn(1000);
        when(asyncConfig.getWorkers()).thenReturn(4);
        
        when(threadPoolExecutor.getQueue()).thenReturn(new LinkedBlockingQueue<>());
        when(threadPoolExecutor.getActiveCount()).thenReturn(0);
        when(threadPoolExecutor.getPoolSize()).thenReturn(0);
        when(threadPoolExecutor.getCompletedTaskCount()).thenReturn(0L);
        when(threadPoolExecutor.getMaximumPoolSize()).thenReturn(4);
        when(threadPoolExecutor.getCorePoolSize()).thenReturn(4);
        
        // When
        healthIndicator.incrementSuccessfulProcessing();
        healthIndicator.incrementSuccessfulProcessing();
        healthIndicator.incrementFailedProcessing();
        healthIndicator.incrementRejectedTasks();
        
        Map<String, Object> health = healthIndicator.asyncProcessingHealth();
        
        // Then
        @SuppressWarnings("unchecked")
        Map<String, Object> processingMetrics = (Map<String, Object>) health.get("processing");
        assertThat(processingMetrics.get("successfulProcessing")).isEqualTo(2L);
        assertThat(processingMetrics.get("failedProcessing")).isEqualTo(1L);
        assertThat(processingMetrics.get("rejectedTasks")).isEqualTo(1L);
        assertThat(processingMetrics.get("successRatePercent")).isEqualTo(66.67);
        
        @SuppressWarnings("unchecked")
        Map<String, Object> indicators = (Map<String, Object>) health.get("indicators");
        assertThat(indicators.get("recentRejections")).isEqualTo(true);
        assertThat(indicators.get("processingErrors")).isEqualTo(true);
    }
    
    @Test
    void resetCounters_ClearsAllMetrics() {
        // Given
        healthIndicator.incrementSuccessfulProcessing();
        healthIndicator.incrementFailedProcessing();
        healthIndicator.incrementRejectedTasks();
        
        // When
        healthIndicator.resetCounters();
        
        // Then - setup mocks for health check
        when(asyncConfig.isEnabled()).thenReturn(true);
        when(asyncConfig.getQueueCapacity()).thenReturn(1000);
        when(asyncConfig.getWorkers()).thenReturn(4);
        
        when(threadPoolExecutor.getQueue()).thenReturn(new LinkedBlockingQueue<>());
        when(threadPoolExecutor.getActiveCount()).thenReturn(0);
        when(threadPoolExecutor.getPoolSize()).thenReturn(0);
        when(threadPoolExecutor.getCompletedTaskCount()).thenReturn(0L);
        when(threadPoolExecutor.getMaximumPoolSize()).thenReturn(4);
        when(threadPoolExecutor.getCorePoolSize()).thenReturn(4);
        
        Map<String, Object> health = healthIndicator.asyncProcessingHealth();
        
        @SuppressWarnings("unchecked")
        Map<String, Object> processingMetrics = (Map<String, Object>) health.get("processing");
        assertThat(processingMetrics.get("successfulProcessing")).isEqualTo(0L);
        assertThat(processingMetrics.get("failedProcessing")).isEqualTo(0L);
        assertThat(processingMetrics.get("rejectedTasks")).isEqualTo(0L);
    }
}
