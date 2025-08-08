package com.wifi.scan.consume.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
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

import com.wifi.scan.consume.metrics.KafkaConsumerMetrics;
import com.wifi.scan.consume.service.KafkaMonitoringService;

/**
 * Unit tests for MessageConsumptionActivityHealthIndicator (Liveness). Tests the liveness behavior
 * that checks connectivity, responsiveness, and consumption activity.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Message Consumption Activity Health Indicator Tests (Liveness)")
class MessageConsumptionActivityHealthIndicatorTest {

  @Mock(lenient = true)
  private KafkaMonitoringService kafkaMonitoringService;

  @Mock(lenient = true)
  private KafkaConsumerMetrics metrics;

  private MessageConsumptionActivityHealthIndicator healthIndicator;

  private static final long POLL_TIMEOUT_THRESHOLD = 5; // 5 minutes
  private static final double CONSUMPTION_RATE_THRESHOLD = 0.1;

  @BeforeEach
  void setUp() {
    // Use constructor with dependency injection for proper testing
    healthIndicator =
        new MessageConsumptionActivityHealthIndicator(
            kafkaMonitoringService, POLL_TIMEOUT_THRESHOLD, CONSUMPTION_RATE_THRESHOLD);
  }

  @Test
  @DisplayName("should_ReturnUp_When_ConsumerConnectedAndGroupActive")
  void should_ReturnUp_When_ConsumerConnectedAndGroupActive() {
    // Given
    when(kafkaMonitoringService.isConsumerConnected()).thenReturn(true);
    when(kafkaMonitoringService.isConsumerGroupActive()).thenReturn(true);
    when(kafkaMonitoringService.getMessageConsumptionRate()).thenReturn(1.5);
    when(kafkaMonitoringService.getMetrics()).thenReturn(metrics);
    when(metrics.getTotalMessagesConsumed()).thenReturn(new AtomicLong(10));
    when(metrics.getTotalMessagesProcessed()).thenReturn(new AtomicLong(10));
    when(metrics.getSuccessRate()).thenReturn(100.0);
    when(kafkaMonitoringService.getTimeSinceLastPoll()).thenReturn(100L); // Recent poll
    when(kafkaMonitoringService.isConsumerStuck()).thenReturn(false);

    // When
    Health health = healthIndicator.health();

    // Then
    assertThat(health.getStatus()).isEqualTo(Status.UP);
    assertThat(health.getDetails().get("consumerConnected")).isEqualTo(true);
    assertThat(health.getDetails().get("consumerGroupActive")).isEqualTo(true);
    assertThat(health.getDetails().get("reason"))
        .isEqualTo("Consumer is healthy - actively polling for messages");
  }

  @Test
  @DisplayName("should_ReturnUp_When_ConsumerConnectedButNoMessages")
  void should_ReturnUp_When_ConsumerConnectedButNoMessages() {
    // Given: Consumer is connected but no messages have been processed
    when(kafkaMonitoringService.isConsumerConnected()).thenReturn(true);
    when(kafkaMonitoringService.isConsumerGroupActive()).thenReturn(true);
    when(kafkaMonitoringService.getMessageConsumptionRate()).thenReturn(0.0);
    when(kafkaMonitoringService.getMetrics()).thenReturn(metrics);
    when(metrics.getTotalMessagesConsumed()).thenReturn(new AtomicLong(0));
    when(metrics.getTotalMessagesProcessed()).thenReturn(new AtomicLong(0));
    when(metrics.getSuccessRate()).thenReturn(0.0);
    when(kafkaMonitoringService.getTimeSinceLastPoll()).thenReturn(100L); // Recent poll
    when(kafkaMonitoringService.isConsumerStuck()).thenReturn(false);

    // When
    Health health = healthIndicator.health();

    // Then: Should still be UP because consumer is connected and not stuck
    assertThat(health.getStatus()).isEqualTo(Status.UP);
    assertThat(health.getDetails().get("consumptionRate")).isEqualTo(0.0);
    assertThat(health.getDetails().get("totalMessagesConsumed")).isEqualTo(0L);
    assertThat(health.getDetails().get("reason"))
        .isEqualTo("Consumer is healthy - actively polling for messages");
  }

  @Test
  @DisplayName("should_ReturnDown_When_ConsumerNotConnected")
  void should_ReturnDown_When_ConsumerNotConnected() {
    // Given - only setup what's actually needed for this test path
    when(kafkaMonitoringService.isConsumerConnected()).thenReturn(false);

    // When
    Health health = healthIndicator.health();

    // Then
    assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    assertThat(health.getDetails().get("consumerConnected")).isEqualTo(false);
    assertThat(health.getDetails().get("reason"))
        .isEqualTo("Consumer cannot connect to Kafka cluster");
  }

  @Test
  @DisplayName("should_ReturnDown_When_ConsumerGroupNotActive")
  void should_ReturnDown_When_ConsumerGroupNotActive() {
    // Given - only setup what's actually needed for this test path
    when(kafkaMonitoringService.isConsumerConnected()).thenReturn(true);
    when(kafkaMonitoringService.isConsumerGroupActive()).thenReturn(false);

    // When
    Health health = healthIndicator.health();

    // Then
    assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    assertThat(health.getDetails().get("consumerConnected")).isEqualTo(true);
    assertThat(health.getDetails().get("consumerGroupActive")).isEqualTo(false);
    assertThat(health.getDetails().get("reason"))
        .isEqualTo("Consumer is not active in consumer group");
  }

  @Test
  @DisplayName("should_ReturnDown_When_ConsumerStuck")
  void should_ReturnDown_When_ConsumerStuck() {
    // Given - only setup what's needed for this test path
    when(kafkaMonitoringService.isConsumerConnected()).thenReturn(true);
    when(kafkaMonitoringService.isConsumerGroupActive()).thenReturn(true);
    when(kafkaMonitoringService.getTimeSinceLastPoll()).thenReturn(100L); // Within threshold
    when(kafkaMonitoringService.isConsumerStuck()).thenReturn(true);

    // When
    Health health = healthIndicator.health();

    // Then
    assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    assertThat(health.getDetails().get("reason"))
        .isEqualTo("Consumer is stuck - polling but not advancing position");
  }

  @Test
  @DisplayName("should_ReturnDown_When_PollTimeoutExceeded")
  void should_ReturnDown_When_PollTimeoutExceeded() {
    // Given - only setup what's needed for this test path
    when(kafkaMonitoringService.isConsumerConnected()).thenReturn(true);
    when(kafkaMonitoringService.isConsumerGroupActive()).thenReturn(true);
    when(kafkaMonitoringService.getTimeSinceLastPoll())
        .thenReturn(400000L); // 400 seconds > 5 minute (300 second) threshold

    // When
    Health health = healthIndicator.health();

    // Then
    assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    assertThat(health.getDetails().get("reason").toString())
        .contains("Consumer hasn't received messages in");
  }

  @Test
  @DisplayName("should_ReturnUp_When_ConsumerConnectedAndProcessingMessages")
  void should_ReturnUp_When_ConsumerConnectedAndProcessingMessages() {
    // Given: Consumer is connected and has processed messages
    when(kafkaMonitoringService.isConsumerConnected()).thenReturn(true);
    when(kafkaMonitoringService.isConsumerGroupActive()).thenReturn(true);
    when(kafkaMonitoringService.getMessageConsumptionRate()).thenReturn(5.5);
    when(kafkaMonitoringService.getMetrics()).thenReturn(metrics);
    when(metrics.getTotalMessagesConsumed()).thenReturn(new AtomicLong(100));
    when(metrics.getTotalMessagesProcessed()).thenReturn(new AtomicLong(98));
    when(metrics.getSuccessRate()).thenReturn(98.0);
    when(kafkaMonitoringService.getTimeSinceLastPoll()).thenReturn(100L); // Recent poll
    when(kafkaMonitoringService.isConsumerStuck()).thenReturn(false);

    // When
    Health health = healthIndicator.health();

    // Then
    assertThat(health.getStatus()).isEqualTo(Status.UP);
    assertThat(health.getDetails().get("consumptionRate")).isEqualTo(5.5);
    assertThat(health.getDetails().get("totalMessagesConsumed")).isEqualTo(100L);
    assertThat(health.getDetails().get("totalMessagesProcessed")).isEqualTo(98L);
    assertThat(health.getDetails().get("successRate")).isEqualTo(98.0);
  }

  @Test
  @DisplayName("should_ReturnDown_When_ExceptionOccurs")
  void should_ReturnDown_When_ExceptionOccurs() {
    // Given
    when(kafkaMonitoringService.isConsumerConnected())
        .thenThrow(new RuntimeException("Connection failed"));

    // When
    Health health = healthIndicator.health();

    // Then
    assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    assertThat(health.getDetails().get("error")).isEqualTo("Connection failed");
    assertThat(health.getDetails().get("reason")).isEqualTo("Health check failed due to exception");
  }

  @Test
  @DisplayName("should_IncludeAllRequiredDetails_When_HealthChecked")
  void should_IncludeAllRequiredDetails_When_HealthChecked() {
    // Given
    when(kafkaMonitoringService.isConsumerConnected()).thenReturn(true);
    when(kafkaMonitoringService.isConsumerGroupActive()).thenReturn(true);
    when(kafkaMonitoringService.getMessageConsumptionRate()).thenReturn(2.5);
    when(kafkaMonitoringService.getMetrics()).thenReturn(metrics);
    when(metrics.getTotalMessagesConsumed()).thenReturn(new AtomicLong(50));
    when(metrics.getTotalMessagesProcessed()).thenReturn(new AtomicLong(48));
    when(metrics.getSuccessRate()).thenReturn(96.0);
    when(kafkaMonitoringService.getTimeSinceLastPoll()).thenReturn(0L);
    when(kafkaMonitoringService.isConsumerStuck()).thenReturn(false);

    // When
    Health health = healthIndicator.health();

    // Then
    assertThat(health.getDetails())
        .containsKeys(
            "consumerConnected",
            "consumerGroupActive",
            "consumptionRate",
            "totalMessagesConsumed",
            "totalMessagesProcessed",
            "successRate",
            "timeSinceLastMessageReceivedMs",
            "messageTimeoutThresholdMs",
            "consumerStuck",
            "healthyConsumption",
            "reason",
            "checkTimestamp");
  }
}
