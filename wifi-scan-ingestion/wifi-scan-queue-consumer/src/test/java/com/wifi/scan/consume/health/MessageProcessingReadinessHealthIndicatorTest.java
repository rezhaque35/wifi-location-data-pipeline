package com.wifi.scan.consume.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import com.wifi.scan.consume.config.HealthIndicatorConfiguration;
import com.wifi.scan.consume.metrics.KafkaConsumerMetrics;
import com.wifi.scan.consume.service.KafkaMonitoringService;

/**
 * Unit tests for MessageProcessingReadinessHealthIndicator. Tests the readiness-focused behavior
 * that checks service readiness for traffic, including comprehensive connectivity and processing
 * health checks.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Message Processing Readiness Health Indicator Tests")
class MessageProcessingReadinessHealthIndicatorTest {

  @Mock private KafkaMonitoringService kafkaMonitoringService;

  @Mock private HealthIndicatorConfiguration config;

  @Mock private KafkaConsumerMetrics metrics;

  private MessageProcessingReadinessHealthIndicator healthIndicator;

  @BeforeEach
  void setUp() {
    healthIndicator = new MessageProcessingReadinessHealthIndicator(kafkaMonitoringService, config);
  }

  @Test
  @DisplayName("should_ReturnUp_When_AllReadinessChecksPass")
  void should_ReturnUp_When_AllReadinessChecksPass() {
    // Given
    when(config.getConsumptionTimeoutMinutes()).thenReturn(10);
    when(config.getMinimumConsumptionRate()).thenReturn(0.0);
    when(kafkaMonitoringService.isConsumerConnected()).thenReturn(true);
    when(kafkaMonitoringService.isConsumerGroupActive()).thenReturn(true);
    when(kafkaMonitoringService.areTopicsAccessible()).thenReturn(true);
    when(kafkaMonitoringService.isMessageConsumptionHealthy(10, 0.0)).thenReturn(true);
    when(kafkaMonitoringService.getMessageConsumptionRate()).thenReturn(5.0);
    when(kafkaMonitoringService.getMetrics()).thenReturn(metrics);
    when(metrics.getSuccessRate()).thenReturn(98.0);
    when(metrics.getTotalMessagesConsumed()).thenReturn(new AtomicLong(100));
    when(metrics.getTotalMessagesProcessed()).thenReturn(new AtomicLong(98));

    // When
    Health health = healthIndicator.health();

    // Then
    assertThat(health.getStatus()).isEqualTo(Status.UP);
    assertThat(health.getDetails().get("consumerConnected")).isEqualTo(true);
    assertThat(health.getDetails().get("consumerGroupActive")).isEqualTo(true);
    assertThat(health.getDetails().get("topicsAccessible")).isEqualTo(true);
    assertThat(health.getDetails().get("consumptionHealthy")).isEqualTo(true);
    assertThat(health.getDetails().get("reason")).isEqualTo("Service is ready to process messages");
    assertThat(health.getDetails().get("readinessNote"))
        .isEqualTo("This probe indicates service readiness for traffic");
  }

  @Test
  @DisplayName("should_ReturnUp_When_NoMessagesConsumedYet")
  void should_ReturnUp_When_NoMessagesConsumedYet() {
    // Given: Service is ready but no messages have been consumed yet
    when(config.getConsumptionTimeoutMinutes()).thenReturn(10);
    when(config.getMinimumConsumptionRate()).thenReturn(0.0);
    when(kafkaMonitoringService.isConsumerConnected()).thenReturn(true);
    when(kafkaMonitoringService.isConsumerGroupActive()).thenReturn(true);
    when(kafkaMonitoringService.areTopicsAccessible()).thenReturn(true);
    when(kafkaMonitoringService.isMessageConsumptionHealthy(10, 0.0)).thenReturn(false);
    when(kafkaMonitoringService.getMessageConsumptionRate()).thenReturn(0.0);
    when(kafkaMonitoringService.getMetrics()).thenReturn(metrics);
    when(metrics.getSuccessRate()).thenReturn(0.0);
    when(metrics.getTotalMessagesConsumed()).thenReturn(new AtomicLong(0));
    when(metrics.getTotalMessagesProcessed()).thenReturn(new AtomicLong(0));

    // When
    Health health = healthIndicator.health();

    // Then: Should be UP because no messages consumed yet (allowable condition)
    assertThat(health.getStatus()).isEqualTo(Status.UP);
    assertThat(health.getDetails().get("totalMessagesConsumed")).isEqualTo(0L);
    assertThat(health.getDetails().get("reason")).isEqualTo("Service is ready to process messages");
  }

  @Test
  @DisplayName("should_ReturnDown_When_ConsumerNotConnected")
  void should_ReturnDown_When_ConsumerNotConnected() {
    // Given
    when(config.getConsumptionTimeoutMinutes()).thenReturn(10);
    when(config.getMinimumConsumptionRate()).thenReturn(0.0);
    when(kafkaMonitoringService.isConsumerConnected()).thenReturn(false);
    when(kafkaMonitoringService.isConsumerGroupActive()).thenReturn(false);
    when(kafkaMonitoringService.areTopicsAccessible()).thenReturn(true);
    when(kafkaMonitoringService.isMessageConsumptionHealthy(10, 0.0)).thenReturn(false);
    when(kafkaMonitoringService.getMessageConsumptionRate()).thenReturn(0.0);
    when(kafkaMonitoringService.getMetrics()).thenReturn(metrics);
    when(metrics.getSuccessRate()).thenReturn(0.0);
    when(metrics.getTotalMessagesConsumed()).thenReturn(new AtomicLong(0));
    when(metrics.getTotalMessagesProcessed()).thenReturn(new AtomicLong(0));

    // When
    Health health = healthIndicator.health();

    // Then
    assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    assertThat(health.getDetails().get("consumerConnected")).isEqualTo(false);
    assertThat(health.getDetails().get("reason"))
        .isEqualTo("Consumer cannot connect to Kafka cluster");
  }

  @Test
  @DisplayName("should_ReturnDown_When_TopicsNotAccessible")
  void should_ReturnDown_When_TopicsNotAccessible() {
    // Given
    when(config.getConsumptionTimeoutMinutes()).thenReturn(10);
    when(config.getMinimumConsumptionRate()).thenReturn(0.0);
    when(kafkaMonitoringService.isConsumerConnected()).thenReturn(true);
    when(kafkaMonitoringService.isConsumerGroupActive()).thenReturn(true);
    when(kafkaMonitoringService.areTopicsAccessible()).thenReturn(false);
    when(kafkaMonitoringService.isMessageConsumptionHealthy(10, 0.0)).thenReturn(true);
    when(kafkaMonitoringService.getMessageConsumptionRate()).thenReturn(0.0);
    when(kafkaMonitoringService.getMetrics()).thenReturn(metrics);
    when(metrics.getSuccessRate()).thenReturn(0.0);
    when(metrics.getTotalMessagesConsumed()).thenReturn(new AtomicLong(0));
    when(metrics.getTotalMessagesProcessed()).thenReturn(new AtomicLong(0));

    // When
    Health health = healthIndicator.health();

    // Then
    assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    assertThat(health.getDetails().get("topicsAccessible")).isEqualTo(false);
    assertThat(health.getDetails().get("reason")).isEqualTo("Configured topics are not accessible");
  }

  @Test
  @DisplayName("should_ReturnDown_When_MessageProcessingDegraded")
  void should_ReturnDown_When_MessageProcessingDegraded() {
    // Given: Service has consumed messages but processing is degraded
    when(config.getConsumptionTimeoutMinutes()).thenReturn(10);
    when(config.getMinimumConsumptionRate()).thenReturn(0.0);
    when(kafkaMonitoringService.isConsumerConnected()).thenReturn(true);
    when(kafkaMonitoringService.isConsumerGroupActive()).thenReturn(true);
    when(kafkaMonitoringService.areTopicsAccessible()).thenReturn(true);
    when(kafkaMonitoringService.isMessageConsumptionHealthy(10, 0.0)).thenReturn(false);
    when(kafkaMonitoringService.getMessageConsumptionRate()).thenReturn(1.0);
    when(kafkaMonitoringService.getMetrics()).thenReturn(metrics);
    when(metrics.getSuccessRate()).thenReturn(50.0);
    when(metrics.getTotalMessagesConsumed()).thenReturn(new AtomicLong(100));
    when(metrics.getTotalMessagesProcessed()).thenReturn(new AtomicLong(50));

    // When
    Health health = healthIndicator.health();

    // Then
    assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    assertThat(health.getDetails().get("consumptionHealthy")).isEqualTo(false);
    assertThat(health.getDetails().get("totalMessagesConsumed")).isEqualTo(100L);
    assertThat(health.getDetails().get("reason")).isEqualTo("Message processing is degraded");
  }

  @Test
  @DisplayName("should_ReturnDown_When_ConsumerGroupNotActive")
  void should_ReturnDown_When_ConsumerGroupNotActive() {
    // Given
    when(config.getConsumptionTimeoutMinutes()).thenReturn(10);
    when(config.getMinimumConsumptionRate()).thenReturn(0.0);
    when(kafkaMonitoringService.isConsumerConnected()).thenReturn(true);
    when(kafkaMonitoringService.isConsumerGroupActive()).thenReturn(false);
    when(kafkaMonitoringService.areTopicsAccessible()).thenReturn(true);
    when(kafkaMonitoringService.isMessageConsumptionHealthy(10, 0.0)).thenReturn(true);
    when(kafkaMonitoringService.getMessageConsumptionRate()).thenReturn(0.0);
    when(kafkaMonitoringService.getMetrics()).thenReturn(metrics);
    when(metrics.getSuccessRate()).thenReturn(0.0);
    when(metrics.getTotalMessagesConsumed()).thenReturn(new AtomicLong(0));
    when(metrics.getTotalMessagesProcessed()).thenReturn(new AtomicLong(0));

    // When
    Health health = healthIndicator.health();

    // Then
    assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    assertThat(health.getDetails().get("consumerGroupActive")).isEqualTo(false);
    assertThat(health.getDetails().get("reason"))
        .isEqualTo("Consumer is not active in consumer group");
  }

  @Test
  @DisplayName("should_ReturnDown_When_ExceptionOccurs")
  void should_ReturnDown_When_ExceptionOccurs() {
    // Given
    when(kafkaMonitoringService.isConsumerConnected())
        .thenThrow(new RuntimeException("Readiness check failed"));

    // When
    Health health = healthIndicator.health();

    // Then
    assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    assertThat(health.getDetails().get("error")).isEqualTo("Readiness check failed");
    assertThat(health.getDetails().get("reason"))
        .isEqualTo("Readiness check failed due to exception");
  }

  @Test
  @DisplayName("should_IncludeAllRequiredDetails_When_HealthChecked")
  void should_IncludeAllRequiredDetails_When_HealthChecked() {
    // Given
    when(config.getConsumptionTimeoutMinutes()).thenReturn(10);
    when(config.getMinimumConsumptionRate()).thenReturn(0.0);
    when(kafkaMonitoringService.isConsumerConnected()).thenReturn(true);
    when(kafkaMonitoringService.isConsumerGroupActive()).thenReturn(true);
    when(kafkaMonitoringService.areTopicsAccessible()).thenReturn(true);
    when(kafkaMonitoringService.isMessageConsumptionHealthy(10, 0.0)).thenReturn(true);
    when(kafkaMonitoringService.getMessageConsumptionRate()).thenReturn(3.5);
    when(kafkaMonitoringService.getMetrics()).thenReturn(metrics);
    when(metrics.getSuccessRate()).thenReturn(95.0);
    when(metrics.getTotalMessagesConsumed()).thenReturn(new AtomicLong(75));
    when(metrics.getTotalMessagesProcessed()).thenReturn(new AtomicLong(71));

    // When
    Health health = healthIndicator.health();

    // Then
    assertThat(health.getDetails())
        .containsKeys(
            "consumerConnected",
            "consumerGroupActive",
            "topicsAccessible",
            "consumptionHealthy",
            "consumptionRate",
            "successRate",
            "totalMessagesConsumed",
            "totalMessagesProcessed",
            "readinessNote",
            "reason",
            "checkTimestamp");
  }

  @Test
  @DisplayName("should_HandleHighVolumeProcessing_When_ServiceIsHealthy")
  void should_HandleHighVolumeProcessing_When_ServiceIsHealthy() {
    // Given: High volume processing scenario
    when(config.getConsumptionTimeoutMinutes()).thenReturn(10);
    when(config.getMinimumConsumptionRate()).thenReturn(0.0);
    when(kafkaMonitoringService.isConsumerConnected()).thenReturn(true);
    when(kafkaMonitoringService.isConsumerGroupActive()).thenReturn(true);
    when(kafkaMonitoringService.areTopicsAccessible()).thenReturn(true);
    when(kafkaMonitoringService.isMessageConsumptionHealthy(10, 0.0)).thenReturn(true);
    when(kafkaMonitoringService.getMessageConsumptionRate()).thenReturn(50.0);
    when(kafkaMonitoringService.getMetrics()).thenReturn(metrics);
    when(metrics.getSuccessRate()).thenReturn(99.5);
    when(metrics.getTotalMessagesConsumed()).thenReturn(new AtomicLong(10000));
    when(metrics.getTotalMessagesProcessed()).thenReturn(new AtomicLong(9950));

    // When
    Health health = healthIndicator.health();

    // Then
    assertThat(health.getStatus()).isEqualTo(Status.UP);
    assertThat(health.getDetails().get("consumptionRate")).isEqualTo(50.0);
    assertThat(health.getDetails().get("successRate")).isEqualTo(99.5);
    assertThat(health.getDetails().get("totalMessagesConsumed")).isEqualTo(10000L);
    assertThat(health.getDetails().get("totalMessagesProcessed")).isEqualTo(9950L);
  }
}
