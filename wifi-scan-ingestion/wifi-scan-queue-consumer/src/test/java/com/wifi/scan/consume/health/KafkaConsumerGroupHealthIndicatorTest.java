package com.wifi.scan.consume.health;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import com.wifi.scan.consume.service.KafkaMonitoringService;

/**
 * Unit tests for KafkaConsumerGroupHealthIndicator.
 *
 * <p>Tests health indicator functionality for Kafka consumer group monitoring,
 * including connection status, group membership, and error conditions.
 */
@ExtendWith(MockitoExtension.class)
class KafkaConsumerGroupHealthIndicatorTest {

  @Mock private KafkaMonitoringService kafkaMonitoringService;

  @InjectMocks private KafkaConsumerGroupHealthIndicator healthIndicator;

  @BeforeEach
  void setUp() {
    // Default setup - tests will override as needed
  }

  @Test
  @DisplayName("Should return UP when consumer is connected and group is active")
  void health_WhenConsumerConnectedAndGroupActive_ShouldReturnUp() {
    // Given
    when(kafkaMonitoringService.isConsumerConnected()).thenReturn(true);
    when(kafkaMonitoringService.isConsumerGroupActive()).thenReturn(true);
    when(kafkaMonitoringService.getClusterNodeCount()).thenReturn(3);

    // When
    Health health = healthIndicator.health();

    // Then
    assertEquals(Status.UP, health.getStatus());
    assertEquals(true, health.getDetails().get("consumerConnected"));
    assertEquals(true, health.getDetails().get("consumerGroupActive"));
    assertEquals(3, health.getDetails().get("clusterNodeCount"));
    assertNotNull(health.getDetails().get("checkTimestamp"));
  }

  @Test
  @DisplayName("Should return DOWN when consumer is not connected")
  void health_WhenConsumerNotConnected_ShouldReturnDown() {
    // Given
    when(kafkaMonitoringService.isConsumerConnected()).thenReturn(false);
    when(kafkaMonitoringService.isConsumerGroupActive()).thenReturn(true);
    when(kafkaMonitoringService.getClusterNodeCount()).thenReturn(3);

    // When
    Health health = healthIndicator.health();

    // Then
    assertEquals(Status.DOWN, health.getStatus());
    assertEquals(false, health.getDetails().get("consumerConnected"));
    assertEquals(true, health.getDetails().get("consumerGroupActive"));
    assertEquals("Consumer is not connected to Kafka cluster", health.getDetails().get("reason"));
    assertEquals(3, health.getDetails().get("clusterNodeCount"));
    assertNotNull(health.getDetails().get("checkTimestamp"));
  }

  @Test
  @DisplayName("Should return DOWN when consumer group is not active")
  void health_WhenConsumerGroupNotActive_ShouldReturnDown() {
    // Given
    when(kafkaMonitoringService.isConsumerConnected()).thenReturn(true);
    when(kafkaMonitoringService.isConsumerGroupActive()).thenReturn(false);
    when(kafkaMonitoringService.getClusterNodeCount()).thenReturn(3);

    // When
    Health health = healthIndicator.health();

    // Then
    assertEquals(Status.DOWN, health.getStatus());
    assertEquals(true, health.getDetails().get("consumerConnected"));
    assertEquals(false, health.getDetails().get("consumerGroupActive"));
    assertEquals("Consumer group is not active in Kafka cluster", health.getDetails().get("reason"));
    assertEquals(3, health.getDetails().get("clusterNodeCount"));
    assertNotNull(health.getDetails().get("checkTimestamp"));
  }

  @Test
  @DisplayName("Should return DOWN when both consumer and group are inactive")
  void health_WhenBothConsumerAndGroupInactive_ShouldReturnDown() {
    // Given
    when(kafkaMonitoringService.isConsumerConnected()).thenReturn(false);
    when(kafkaMonitoringService.isConsumerGroupActive()).thenReturn(false);
    when(kafkaMonitoringService.getClusterNodeCount()).thenReturn(0);

    // When
    Health health = healthIndicator.health();

    // Then
    assertEquals(Status.DOWN, health.getStatus());
    assertEquals(false, health.getDetails().get("consumerConnected"));
    assertEquals(false, health.getDetails().get("consumerGroupActive"));
    assertEquals("Consumer is not connected to Kafka cluster", health.getDetails().get("reason"));
    assertEquals(0, health.getDetails().get("clusterNodeCount"));
    assertNotNull(health.getDetails().get("checkTimestamp"));
  }

  @Test
  @DisplayName("Should return DOWN with error details when monitoring service throws exception")
  void health_WhenMonitoringServiceThrows_ShouldReturnDown() {
    // Given
    when(kafkaMonitoringService.isConsumerConnected()).thenThrow(new RuntimeException("Connection failed"));

    // When
    Health health = healthIndicator.health();

    // Then
    assertEquals(Status.DOWN, health.getStatus());
    assertEquals("Connection failed", health.getDetails().get("error"));
    assertNotNull(health.getDetails().get("checkTimestamp"));
  }

  @Test
  @DisplayName("Should include all required details in health response")
  void health_ShouldIncludeAllRequiredDetails() {
    // Given
    when(kafkaMonitoringService.isConsumerConnected()).thenReturn(true);
    when(kafkaMonitoringService.isConsumerGroupActive()).thenReturn(true);
    when(kafkaMonitoringService.getClusterNodeCount()).thenReturn(5);

    // When
    Health health = healthIndicator.health();

    // Then
    assertEquals(4, health.getDetails().size());
    assertTrue(health.getDetails().containsKey("consumerConnected"));
    assertTrue(health.getDetails().containsKey("consumerGroupActive"));
    assertTrue(health.getDetails().containsKey("clusterNodeCount"));
    assertTrue(health.getDetails().containsKey("checkTimestamp"));
  }
}
