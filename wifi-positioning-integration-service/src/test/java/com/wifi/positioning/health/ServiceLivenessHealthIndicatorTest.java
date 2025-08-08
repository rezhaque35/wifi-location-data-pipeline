package com.wifi.positioning.health;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

/**
 * Unit tests for ServiceLivenessHealthIndicator. Tests the liveness health check functionality
 * following TDD principles.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ServiceLivenessHealthIndicator Tests")
class ServiceLivenessHealthIndicatorTest {

  private ServiceLivenessHealthIndicator healthIndicator;

  @BeforeEach
  void setUp() {
    healthIndicator = new ServiceLivenessHealthIndicator();
  }

  @Test
  @DisplayName("should_ReturnHealthyStatus_When_ServiceIsRunning")
  void should_ReturnHealthyStatus_When_ServiceIsRunning() {
    // Act
    Health health = healthIndicator.health();

    // Assert
    assertEquals(Status.UP, health.getStatus());
    assertNotNull(health.getDetails().get("status"));
    assertEquals("Service is alive and running", health.getDetails().get("status"));
    assertNotNull(health.getDetails().get("startupTime"));
    assertNotNull(health.getDetails().get("uptime"));
  }

  @Test
  @DisplayName("should_IncludeStartupTime_When_HealthCheckCalled")
  void should_IncludeStartupTime_When_HealthCheckCalled() {
    // Act
    Health health = healthIndicator.health();

    // Assert
    Object startupTime = health.getDetails().get("startupTime");
    assertNotNull(startupTime);
    assertTrue(startupTime instanceof Instant);

    // Startup time should be recent (within last few seconds)
    Instant startup = (Instant) startupTime;
    assertTrue(startup.isBefore(Instant.now()));
    assertTrue(startup.isAfter(Instant.now().minusSeconds(10)));
  }

  @Test
  @DisplayName("should_CalculateUptime_When_HealthCheckCalled")
  void should_CalculateUptime_When_HealthCheckCalled() throws InterruptedException {
    // Arrange - wait a small amount to ensure uptime > 0
    Thread.sleep(10);

    // Act
    Health health = healthIndicator.health();

    // Assert
    Object uptime = health.getDetails().get("uptime");
    assertNotNull(uptime);
    assertTrue(uptime instanceof String);

    String uptimeStr = (String) uptime;
    assertTrue(uptimeStr.contains("ms") || uptimeStr.contains("seconds"));
  }

  @Test
  @DisplayName("should_ReturnConsistentStartupTime_When_CalledMultipleTimes")
  void should_ReturnConsistentStartupTime_When_CalledMultipleTimes() {
    // Act
    Health health1 = healthIndicator.health();
    Health health2 = healthIndicator.health();

    // Assert
    Instant startupTime1 = (Instant) health1.getDetails().get("startupTime");
    Instant startupTime2 = (Instant) health2.getDetails().get("startupTime");

    assertEquals(startupTime1, startupTime2);
  }

  @Test
  @DisplayName("should_IncreaseUptime_When_CalledAfterDelay")
  void should_IncreaseUptime_When_CalledAfterDelay() throws InterruptedException {
    // Arrange
    Health health1 = healthIndicator.health();
    Thread.sleep(50); // Wait 50ms

    // Act
    Health health2 = healthIndicator.health();

    // Assert
    String uptime1 = (String) health1.getDetails().get("uptime");
    String uptime2 = (String) health2.getDetails().get("uptime");

    assertNotEquals(uptime1, uptime2);
  }

  @Test
  @DisplayName("should_IncludeServiceName_When_HealthCheckCalled")
  void should_IncludeServiceName_When_HealthCheckCalled() {
    // Act
    Health health = healthIndicator.health();

    // Assert
    Object serviceName = health.getDetails().get("serviceName");
    assertNotNull(serviceName);
    assertEquals("WiFi Positioning Service", serviceName);
  }

  @Test
  @DisplayName("should_IncludeVersion_When_HealthCheckCalled")
  void should_IncludeVersion_When_HealthCheckCalled() {
    // Act
    Health health = healthIndicator.health();

    // Assert
    Object version = health.getDetails().get("version");
    assertNotNull(version);
    assertTrue(version instanceof String);
  }
}
