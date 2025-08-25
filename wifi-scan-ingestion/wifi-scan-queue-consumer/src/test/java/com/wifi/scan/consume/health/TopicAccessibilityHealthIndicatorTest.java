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
 * Unit tests for TopicAccessibilityHealthIndicator.
 *
 * <p>Tests health indicator functionality for Kafka topic accessibility monitoring,
 * including topic availability checks and error handling.
 */
@ExtendWith(MockitoExtension.class)
class TopicAccessibilityHealthIndicatorTest {

  @Mock private KafkaMonitoringService kafkaMonitoringService;

  @InjectMocks private TopicAccessibilityHealthIndicator healthIndicator;

  @BeforeEach
  void setUp() {
    // Default setup - tests will override as needed
  }

  @Test
  @DisplayName("Should return UP when topics are accessible")
  void health_WhenTopicsAccessible_ShouldReturnUp() {
    // Given
    when(kafkaMonitoringService.areTopicsAccessible()).thenReturn(true);

    // When
    Health health = healthIndicator.health();

    // Then
    assertEquals(Status.UP, health.getStatus());
    assertEquals(true, health.getDetails().get("topicsAccessible"));
    assertNotNull(health.getDetails().get("checkTimestamp"));
  }

  @Test
  @DisplayName("Should return DOWN when topics are not accessible")
  void health_WhenTopicsNotAccessible_ShouldReturnDown() {
    // Given
    when(kafkaMonitoringService.areTopicsAccessible()).thenReturn(false);

    // When
    Health health = healthIndicator.health();

    // Then
    assertEquals(Status.DOWN, health.getStatus());
    assertEquals(false, health.getDetails().get("topicsAccessible"));
    assertEquals("Configured topics are not accessible", health.getDetails().get("reason"));
    assertNotNull(health.getDetails().get("checkTimestamp"));
  }

  @Test
  @DisplayName("Should return DOWN with error details when monitoring service throws exception")
  void health_WhenMonitoringServiceThrows_ShouldReturnDown() {
    // Given
    when(kafkaMonitoringService.areTopicsAccessible()).thenThrow(new RuntimeException("Topic check failed"));

    // When
    Health health = healthIndicator.health();

    // Then
    assertEquals(Status.DOWN, health.getStatus());
    assertEquals("Topic check failed", health.getDetails().get("error"));
    assertNotNull(health.getDetails().get("checkTimestamp"));
  }

  @Test
  @DisplayName("Should include required details when topics are accessible")
  void health_WhenTopicsAccessible_ShouldIncludeRequiredDetails() {
    // Given
    when(kafkaMonitoringService.areTopicsAccessible()).thenReturn(true);

    // When
    Health health = healthIndicator.health();

    // Then
    assertEquals(2, health.getDetails().size());
    assertTrue(health.getDetails().containsKey("topicsAccessible"));
    assertTrue(health.getDetails().containsKey("checkTimestamp"));
  }

  @Test
  @DisplayName("Should include required details when topics are not accessible")
  void health_WhenTopicsNotAccessible_ShouldIncludeRequiredDetails() {
    // Given
    when(kafkaMonitoringService.areTopicsAccessible()).thenReturn(false);

    // When
    Health health = healthIndicator.health();

    // Then
    assertEquals(3, health.getDetails().size());
    assertTrue(health.getDetails().containsKey("topicsAccessible"));
    assertTrue(health.getDetails().containsKey("reason"));
    assertTrue(health.getDetails().containsKey("checkTimestamp"));
  }

  @Test
  @DisplayName("Should include error details when exception occurs")
  void health_WhenExceptionOccurs_ShouldIncludeErrorDetails() {
    // Given
    when(kafkaMonitoringService.areTopicsAccessible()).thenThrow(new RuntimeException("Connection error"));

    // When
    Health health = healthIndicator.health();

    // Then
    assertEquals(2, health.getDetails().size());
    assertTrue(health.getDetails().containsKey("error"));
    assertTrue(health.getDetails().containsKey("checkTimestamp"));
    assertEquals("Connection error", health.getDetails().get("error"));
  }
}
