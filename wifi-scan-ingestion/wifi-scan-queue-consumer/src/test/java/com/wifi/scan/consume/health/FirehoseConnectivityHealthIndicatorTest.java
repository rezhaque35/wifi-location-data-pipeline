package com.wifi.scan.consume.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import com.wifi.scan.consume.service.BatchFirehoseMessageService;
import com.wifi.scan.consume.service.BatchFirehoseMessageService.BatchFirehoseMetrics;

/**
 * Unit tests for FirehoseConnectivityHealthIndicator (Readiness) with batch service. Tests the
 * readiness behavior that checks batch Firehose connectivity and delivery stream accessibility.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Batch Firehose Connectivity Health Indicator Tests (Readiness)")
class FirehoseConnectivityHealthIndicatorTest {

  @Mock(lenient = true)
  private BatchFirehoseMessageService batchFirehoseService;

  private FirehoseConnectivityHealthIndicator healthIndicator;

  private static final String DELIVERY_STREAM_NAME = "test-stream";

  @BeforeEach
  void setUp() {
    healthIndicator = new FirehoseConnectivityHealthIndicator(batchFirehoseService);
  }

  @Nested
  @DisplayName("Connectivity Status Tests")
  class ConnectivityStatusTests {

    @Test
    @DisplayName("Should return UP when batch Firehose is accessible")
    void shouldReturnUpWhenBatchFirehoseAccessible() {
      // Given
      BatchFirehoseMetrics mockMetrics =
          BatchFirehoseMetrics.builder()
              .deliveryStreamName(DELIVERY_STREAM_NAME)
              .successfulBatches(10)
              .failedBatches(2)
              .totalMessagesProcessed(150)
              .totalBytesProcessed(5000)
              .build();

      when(batchFirehoseService.testConnectivity()).thenReturn(true);
      when(batchFirehoseService.getMetrics()).thenReturn(mockMetrics);

      // When
      Health health = healthIndicator.health();

      // Then
      assertThat(health.getStatus()).isEqualTo(Status.UP);
      assertThat(health.getDetails())
          .containsEntry("status", "Batch Firehose delivery stream accessible")
          .containsEntry("deliveryStreamName", DELIVERY_STREAM_NAME)
          .containsEntry("successfulBatches", 10L)
          .containsEntry("failedBatches", 2L)
          .containsEntry("totalMessagesProcessed", 150L)
          .containsEntry("totalBytesProcessed", 5000L)
          .containsEntry("batchSuccessRate", "83.33%")
          .containsEntry("averageMessagesPerBatch", "15.0")
          .containsKey("lastCheckTime");

      verify(batchFirehoseService, times(1)).testConnectivity();
      verify(batchFirehoseService, times(1)).getMetrics();
    }

    @Test
    @DisplayName("Should return DOWN when batch Firehose is not accessible")
    void shouldReturnDownWhenBatchFirehoseNotAccessible() {
      // Given
      when(batchFirehoseService.testConnectivity()).thenReturn(false);

      // When
      Health health = healthIndicator.health();

      // Then
      assertThat(health.getStatus()).isEqualTo(Status.DOWN);
      assertThat(health.getDetails())
          .containsEntry("status", "Batch Firehose delivery stream not accessible")
          .containsEntry("reason", "Failed to describe delivery stream")
          .containsKey("lastCheckTime");

      verify(batchFirehoseService, times(1)).testConnectivity();
      verify(batchFirehoseService, times(0))
          .getMetrics(); // Should not call getMetrics when connectivity fails
    }

    @Test
    @DisplayName("Should return DOWN when connectivity check throws exception")
    void shouldReturnDownWhenConnectivityCheckThrowsException() {
      // Given
      RuntimeException testException = new RuntimeException("Connection timeout");
      when(batchFirehoseService.testConnectivity()).thenThrow(testException);

      // When
      Health health = healthIndicator.health();

      // Then
      assertThat(health.getStatus()).isEqualTo(Status.DOWN);
      assertThat(health.getDetails())
          .containsEntry("status", "Batch Firehose connectivity check failed")
          .containsEntry("error", "Connection timeout")
          .containsKey("lastCheckTime");

      verify(batchFirehoseService, times(1)).testConnectivity();
    }
  }

  @Nested
  @DisplayName("Metrics Integration Tests")
  class MetricsIntegrationTests {

    @Test
    @DisplayName("Should include detailed batch metrics when Firehose is healthy")
    void shouldIncludeDetailedBatchMetricsWhenFirehoseHealthy() {
      // Given - simulate healthy batch Firehose with good metrics
      BatchFirehoseMetrics mockMetrics =
          BatchFirehoseMetrics.builder()
              .deliveryStreamName(DELIVERY_STREAM_NAME)
              .successfulBatches(100)
              .failedBatches(0)
              .totalMessagesProcessed(15000)
              .totalBytesProcessed(50000)
              .build();

      when(batchFirehoseService.testConnectivity()).thenReturn(true);
      when(batchFirehoseService.getMetrics()).thenReturn(mockMetrics);

      // When
      Health health = healthIndicator.health();

      // Then
      assertThat(health.getStatus()).isEqualTo(Status.UP);
      assertThat(health.getDetails())
          .containsEntry("deliveryStreamName", DELIVERY_STREAM_NAME)
          .containsEntry("successfulBatches", 100L)
          .containsEntry("failedBatches", 0L)
          .containsEntry("totalMessagesProcessed", 15000L)
          .containsEntry("totalBytesProcessed", 50000L)
          .containsEntry("batchSuccessRate", "100.00%")
          .containsEntry("averageMessagesPerBatch", "150.0");
    }

    @Test
    @DisplayName("Should handle zero batches metrics correctly")
    void shouldHandleZeroBatchesMetricsCorrectly() {
      // Given - simulate newly started batch Firehose with no batches yet
      BatchFirehoseMetrics mockMetrics =
          BatchFirehoseMetrics.builder()
              .deliveryStreamName(DELIVERY_STREAM_NAME)
              .successfulBatches(0)
              .failedBatches(0)
              .totalMessagesProcessed(0)
              .totalBytesProcessed(0)
              .build();

      when(batchFirehoseService.testConnectivity()).thenReturn(true);
      when(batchFirehoseService.getMetrics()).thenReturn(mockMetrics);

      // When
      Health health = healthIndicator.health();

      // Then
      assertThat(health.getStatus()).isEqualTo(Status.UP);
      assertThat(health.getDetails())
          .containsEntry("successfulBatches", 0L)
          .containsEntry("failedBatches", 0L)
          .containsEntry("totalMessagesProcessed", 0L)
          .containsEntry("totalBytesProcessed", 0L)
          .containsEntry("batchSuccessRate", "0.00%")
          .containsEntry("averageMessagesPerBatch", "0.0");
    }

    @Test
    @DisplayName("Should calculate batch success rate correctly for various scenarios")
    void shouldCalculateBatchSuccessRateCorrectlyForVariousScenarios() {
      // Test scenario 1: 50% batch success rate
      BatchFirehoseMetrics metrics50Percent =
          BatchFirehoseMetrics.builder()
              .deliveryStreamName(DELIVERY_STREAM_NAME)
              .successfulBatches(50)
              .failedBatches(50)
              .totalMessagesProcessed(7500)
              .totalBytesProcessed(10000)
              .build();

      when(batchFirehoseService.testConnectivity()).thenReturn(true);
      when(batchFirehoseService.getMetrics()).thenReturn(metrics50Percent);

      Health health = healthIndicator.health();
      assertThat(health.getDetails()).containsEntry("batchSuccessRate", "50.00%");

      // Test scenario 2: 33.33% batch success rate
      BatchFirehoseMetrics metrics33Percent =
          BatchFirehoseMetrics.builder()
              .deliveryStreamName(DELIVERY_STREAM_NAME)
              .successfulBatches(1)
              .failedBatches(2)
              .totalMessagesProcessed(150)
              .totalBytesProcessed(1000)
              .build();

      when(batchFirehoseService.getMetrics()).thenReturn(metrics33Percent);

      health = healthIndicator.health();
      assertThat(health.getDetails()).containsEntry("batchSuccessRate", "33.33%");
    }

    @Test
    @DisplayName("Should handle metrics retrieval exception gracefully")
    void shouldHandleMetricsRetrievalExceptionGracefully() {
      // Given
      when(batchFirehoseService.testConnectivity()).thenReturn(true);
      when(batchFirehoseService.getMetrics()).thenThrow(new RuntimeException("Metrics error"));

      // When
      Health health = healthIndicator.health();

      // Then
      assertThat(health.getStatus()).isEqualTo(Status.DOWN);
      assertThat(health.getDetails())
          .containsEntry("status", "Batch Firehose connectivity check failed")
          .containsEntry("error", "Metrics error")
          .containsKey("lastCheckTime");
    }
  }

  @Nested
  @DisplayName("Readiness Probe Integration Tests")
  class ReadinessProbeIntegrationTests {

    @Test
    @DisplayName("Should support readiness probe behavior - UP allows traffic")
    void shouldSupportReadinessProbeUpAllowsTraffic() {
      // Given - batch Firehose is healthy and accessible
      BatchFirehoseMetrics mockMetrics =
          BatchFirehoseMetrics.builder()
              .deliveryStreamName(DELIVERY_STREAM_NAME)
              .successfulBatches(25)
              .failedBatches(1)
              .totalMessagesProcessed(3900)
              .totalBytesProcessed(15000)
              .build();

      when(batchFirehoseService.testConnectivity()).thenReturn(true);
      when(batchFirehoseService.getMetrics()).thenReturn(mockMetrics);

      // When
      Health health = healthIndicator.health();

      // Then - Should be UP (ready to receive traffic)
      assertThat(health.getStatus()).isEqualTo(Status.UP);
      assertThat(health.getDetails())
          .containsEntry("status", "Batch Firehose delivery stream accessible");
    }

    @Test
    @DisplayName("Should support readiness probe behavior - DOWN prevents traffic")
    void shouldSupportReadinessProbeDownPreventsTraffic() {
      // Given - batch Firehose is not accessible (e.g., LocalStack down)
      when(batchFirehoseService.testConnectivity()).thenReturn(false);

      // When
      Health health = healthIndicator.health();

      // Then - Should be DOWN (pod removed from service)
      assertThat(health.getStatus()).isEqualTo(Status.DOWN);
      assertThat(health.getDetails())
          .containsEntry("status", "Batch Firehose delivery stream not accessible")
          .containsEntry("reason", "Failed to describe delivery stream");
    }

    @Test
    @DisplayName("Should provide meaningful error information for troubleshooting")
    void shouldProvideMeaningfulErrorInformationForTroubleshooting() {
      // Given - simulate specific AWS error
      RuntimeException awsError =
          new RuntimeException(
              "ResourceNotFoundException: Delivery stream 'test-stream' not found");
      when(batchFirehoseService.testConnectivity()).thenThrow(awsError);

      // When
      Health health = healthIndicator.health();

      // Then
      assertThat(health.getStatus()).isEqualTo(Status.DOWN);
      assertThat(health.getDetails())
          .containsEntry(
              "error", "ResourceNotFoundException: Delivery stream 'test-stream' not found")
          .containsKey("lastCheckTime");
    }
  }

  @Nested
  @DisplayName("Performance and Reliability Tests")
  class PerformanceReliabilityTests {

    @Test
    @DisplayName("Should handle multiple rapid health checks efficiently")
    void shouldHandleMultipleRapidHealthChecksEfficiently() {
      // Given
      BatchFirehoseMetrics mockMetrics =
          BatchFirehoseMetrics.builder()
              .deliveryStreamName(DELIVERY_STREAM_NAME)
              .successfulBatches(5)
              .failedBatches(0)
              .totalMessagesProcessed(750)
              .totalBytesProcessed(2500)
              .build();

      when(batchFirehoseService.testConnectivity()).thenReturn(true);
      when(batchFirehoseService.getMetrics()).thenReturn(mockMetrics);

      // When - call health check multiple times rapidly
      for (int i = 0; i < 5; i++) {
        Health health = healthIndicator.health();
        assertThat(health.getStatus()).isEqualTo(Status.UP);
      }

      // Then - should handle all calls without issues
      verify(batchFirehoseService, times(5)).testConnectivity();
      verify(batchFirehoseService, times(5)).getMetrics();
    }

    @Test
    @DisplayName("Should include timestamp for monitoring and debugging")
    void shouldIncludeTimestampForMonitoringAndDebugging() {
      // Given
      when(batchFirehoseService.testConnectivity()).thenReturn(true);
      BatchFirehoseMetrics mockMetrics =
          BatchFirehoseMetrics.builder()
              .deliveryStreamName(DELIVERY_STREAM_NAME)
              .successfulBatches(1)
              .failedBatches(0)
              .totalMessagesProcessed(150)
              .totalBytesProcessed(500)
              .build();
      when(batchFirehoseService.getMetrics()).thenReturn(mockMetrics);

      long beforeCheck = System.currentTimeMillis();

      // When
      Health health = healthIndicator.health();

      // Then
      long afterCheck = System.currentTimeMillis();
      Object lastCheckTime = health.getDetails().get("lastCheckTime");

      assertThat(lastCheckTime).isInstanceOf(Long.class);
      long timestamp = (Long) lastCheckTime;
      assertThat(timestamp).isBetween(beforeCheck, afterCheck);
    }

    @Test
    @DisplayName("Should be resilient to null metrics")
    void shouldBeResilientToNullMetrics() {
      // Given
      when(batchFirehoseService.testConnectivity()).thenReturn(true);
      when(batchFirehoseService.getMetrics()).thenReturn(null);

      // When
      Health health = healthIndicator.health();

      // Then - should handle gracefully and return DOWN
      assertThat(health.getStatus()).isEqualTo(Status.DOWN);
      assertThat(health.getDetails()).containsKey("error").containsKey("lastCheckTime");
    }
  }
}
