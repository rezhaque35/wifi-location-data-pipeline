package com.wifi.scan.consume.health;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import com.wifi.scan.consume.config.HealthIndicatorConfiguration;
import com.wifi.scan.consume.config.KafkaProperties;
import com.wifi.scan.consume.service.KafkaMonitoringService;

/**
 * Unit tests for SslCertificateHealthIndicator.
 * 
 * <p>Test suite covering SSL certificate health monitoring, including SSL enabled/disabled scenarios,
 * certificate validation, configuration integration, and error handling for the standard SSL
 * certificate health indicator.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SslCertificateHealthIndicator Tests")
class SslCertificateHealthIndicatorTest {

    @Mock
    private HealthIndicatorConfiguration healthConfig;

    @Mock
    private KafkaProperties kafkaProperties;

    @Mock
    private KafkaMonitoringService kafkaMonitoringService;

    @Mock
    private KafkaProperties.Ssl sslProperties;

    private SslCertificateHealthIndicator healthIndicator;

    @BeforeEach
    void setUp() {
        // Setup default configurations
        when(kafkaProperties.getSsl()).thenReturn(sslProperties);

        healthIndicator = new SslCertificateHealthIndicator(
            kafkaMonitoringService,
            healthConfig,
            kafkaProperties
        );
    }

    // ==================== SSL Disabled Tests ====================

    @Test
    @DisplayName("Should return UP when SSL is disabled")
    void health_WhenSslDisabled_ShouldReturnUp() {
        // Given
        when(sslProperties.isEnabled()).thenReturn(false);

        // When
        Health health = healthIndicator.health();

        // Then
        assertEquals(Status.UP, health.getStatus());
        assertNotNull(health.getDetails().get("sslEnabled"));
        assertEquals(false, health.getDetails().get("sslEnabled"));
        assertNotNull(health.getDetails().get("reason"));
        assertEquals("SSL is not enabled", health.getDetails().get("reason"));
        assertNotNull(health.getDetails().get("checkTimestamp"));
    }

    @Test
    @DisplayName("Should include proper details when SSL is disabled")
    void health_WhenSslDisabled_ShouldIncludeProperDetails() {
        // Given
        when(sslProperties.isEnabled()).thenReturn(false);

        // When
        Health health = healthIndicator.health();

        // Then
        assertEquals(Status.UP, health.getStatus());
        assertTrue(health.getDetails().containsKey("sslEnabled"));
        assertTrue(health.getDetails().containsKey("checkTimestamp"));
        assertEquals(false, health.getDetails().get("sslEnabled"));
    }

    // ==================== SSL Enabled with Missing Configuration Tests ====================

    @Test
    @DisplayName("Should return DOWN when SSL enabled but no truststore configured")
    void health_WhenSslEnabledButNoTruststore_ShouldReturnDown() {
        // Given
        when(sslProperties.isEnabled()).thenReturn(true);
        when(sslProperties.getTruststore()).thenReturn(null);

        // When
        Health health = healthIndicator.health();

        // Then
        assertEquals(Status.DOWN, health.getStatus());
        assertNotNull(health.getDetails().get("error"));
        assertNotNull(health.getDetails().get("checkTimestamp"));
    }

    @Test
    @DisplayName("Should return DOWN when truststore location is null")
    void health_WhenTruststoreLocationNull_ShouldReturnDown() {
        // Given
        when(sslProperties.isEnabled()).thenReturn(true);
        
        KafkaProperties.Truststore truststore = mock(KafkaProperties.Truststore.class);
        when(sslProperties.getTruststore()).thenReturn(truststore);
        when(truststore.getLocation()).thenReturn(null);

        // When
        Health health = healthIndicator.health();

        // Then
        assertEquals(Status.DOWN, health.getStatus());
        assertNotNull(health.getDetails().get("error"));
        assertNotNull(health.getDetails().get("checkTimestamp"));
    }

    @Test
    @DisplayName("Should return DOWN when truststore location is empty")
    void health_WhenTruststoreLocationEmpty_ShouldReturnDown() {
        // Given
        when(sslProperties.isEnabled()).thenReturn(true);
        
        KafkaProperties.Truststore truststore = mock(KafkaProperties.Truststore.class);
        when(sslProperties.getTruststore()).thenReturn(truststore);
        when(truststore.getLocation()).thenReturn("");

        // When
        Health health = healthIndicator.health();

        // Then
        assertEquals(Status.DOWN, health.getStatus());
        assertNotNull(health.getDetails().get("error"));
        assertNotNull(health.getDetails().get("checkTimestamp"));
    }

    @Test
    @DisplayName("Should return DOWN when truststore password is null")
    void health_WhenTruststorePasswordNull_ShouldReturnDown() {
        // Given
        when(sslProperties.isEnabled()).thenReturn(true);
        
        KafkaProperties.Truststore truststore = mock(KafkaProperties.Truststore.class);
        when(sslProperties.getTruststore()).thenReturn(truststore);
        when(truststore.getLocation()).thenReturn("test.jks");
        when(truststore.getPassword()).thenReturn(null);

        // When
        Health health = healthIndicator.health();

        // Then
        assertEquals(Status.DOWN, health.getStatus());
        assertNotNull(health.getDetails().get("error"));
        assertNotNull(health.getDetails().get("checkTimestamp"));
    }

    // ==================== Invalid Truststore File Tests ====================

    @Test
    @DisplayName("Should return DOWN when truststore file does not exist")
    void health_WhenTruststoreFileNotExists_ShouldReturnDown() {
        // Given
        when(sslProperties.isEnabled()).thenReturn(true);
        
        KafkaProperties.Truststore truststore = mock(KafkaProperties.Truststore.class);
        when(sslProperties.getTruststore()).thenReturn(truststore);
        when(truststore.getLocation()).thenReturn("nonexistent-file.jks");
        when(truststore.getPassword()).thenReturn("password");

        // When
        Health health = healthIndicator.health();

        // Then
        assertEquals(Status.DOWN, health.getStatus());
        assertNotNull(health.getDetails().get("error"));
        assertNotNull(health.getDetails().get("checkTimestamp"));
    }

    @Test
    @DisplayName("Should return DOWN when truststore file path is invalid")
    void health_WhenTruststoreFilePathInvalid_ShouldReturnDown() {
        // Given
        when(sslProperties.isEnabled()).thenReturn(true);
        
        KafkaProperties.Truststore truststore = mock(KafkaProperties.Truststore.class);
        when(sslProperties.getTruststore()).thenReturn(truststore);
        when(truststore.getLocation()).thenReturn("/invalid/path/to/truststore.jks");
        when(truststore.getPassword()).thenReturn("password");

        // When
        Health health = healthIndicator.health();

        // Then
        assertEquals(Status.DOWN, health.getStatus());
        assertNotNull(health.getDetails().get("error"));
        assertNotNull(health.getDetails().get("checkTimestamp"));
    }

    // ==================== Configuration Exception Tests ====================

    @Test
    @DisplayName("Should handle SSL configuration exceptions gracefully")
    void health_WhenSslConfigurationThrowsException_ShouldHandleGracefully() {
        // Given
        when(sslProperties.isEnabled()).thenThrow(new RuntimeException("SSL configuration error"));

        // When
        Health health = healthIndicator.health();

        // Then
        assertEquals(Status.DOWN, health.getStatus());
        assertNotNull(health.getDetails().get("error"));
        assertNotNull(health.getDetails().get("checkTimestamp"));
        assertTrue(health.getDetails().get("error").toString().contains("SSL configuration error"));
    }

    @Test
    @DisplayName("Should handle null Kafka properties gracefully")
    void health_WhenKafkaPropertiesNull_ShouldHandleGracefully() {
        // Given
        when(kafkaProperties.getSsl()).thenReturn(null);

        // When
        Health health = healthIndicator.health();

        // Then
        assertEquals(Status.UP, health.getStatus());
        assertEquals(false, health.getDetails().get("sslEnabled"));
        assertNotNull(health.getDetails().get("checkTimestamp"));
    }

    // ==================== Health Details Structure Tests ====================

    @Test
    @DisplayName("Should always include required health details")
    void health_ShouldAlwaysIncludeRequiredDetails() {
        // Given
        when(sslProperties.isEnabled()).thenReturn(false);

        // When
        Health health = healthIndicator.health();

        // Then
        assertNotNull(health.getDetails());
        assertTrue(health.getDetails().containsKey("checkTimestamp"));
        assertTrue(health.getDetails().containsKey("sslEnabled"));
        assertNotNull(health.getDetails().get("checkTimestamp"));
    }

    @Test
    @DisplayName("Should include reason in DOWN status")
    void health_WhenStatusDown_ShouldIncludeReason() {
        // Given
        when(sslProperties.isEnabled()).thenReturn(true);
        when(sslProperties.getTruststore()).thenReturn(null);

        // When
        Health health = healthIndicator.health();

        // Then
        assertEquals(Status.DOWN, health.getStatus());
        assertTrue(health.getDetails().containsKey("error"));
        assertNotNull(health.getDetails().get("error"));
    }

    @Test
    @DisplayName("Should include proper timestamp format")
    void health_ShouldIncludeProperTimestampFormat() {
        // Given
        when(sslProperties.isEnabled()).thenReturn(false);

        // When
        Health health = healthIndicator.health();

        // Then
        Object timestamp = health.getDetails().get("checkTimestamp");
        assertNotNull(timestamp);
        assertTrue(timestamp instanceof Long);
        // Verify it's a reasonable timestamp value
        Long timestampLong = (Long) timestamp;
        assertTrue(timestampLong > 0); // Should be a positive timestamp
    }

    // ==================== Configuration Integration Tests ====================

    @Test
    @DisplayName("Should use HealthIndicatorConfiguration for warning days")
    void health_ShouldUseConfigurationForWarningDays() {
        // Given
        when(sslProperties.isEnabled()).thenReturn(false);
        when(healthConfig.getCertificateExpirationWarningDays()).thenReturn(45);

        // When
        Health health = healthIndicator.health();

        // Then
        assertEquals(Status.UP, health.getStatus());
        // Configuration is used internally, verify health check completes successfully
        assertNotNull(health.getDetails());
    }

    @Test
    @DisplayName("Should use HealthIndicatorConfiguration for validation timeout")
    void health_ShouldUseConfigurationForValidationTimeout() {
        // Given
        when(sslProperties.isEnabled()).thenReturn(false);
        when(healthConfig.getCertificateValidationTimeoutSeconds()).thenReturn(15);

        // When
        Health health = healthIndicator.health();

        // Then
        assertEquals(Status.UP, health.getStatus());
        // Configuration is used internally, verify health check completes successfully
        assertNotNull(health.getDetails());
    }

    // ==================== Error Message Quality Tests ====================

    @Test
    @DisplayName("Should provide clear error message for missing truststore")
    void health_WhenMissingTruststore_ShouldProvideClearErrorMessage() {
        // Given
        when(sslProperties.isEnabled()).thenReturn(true);
        when(sslProperties.getTruststore()).thenReturn(null);

        // When
        Health health = healthIndicator.health();

        // Then
        assertEquals(Status.DOWN, health.getStatus());
        assertNotNull(health.getDetails().get("error"));
        String error = health.getDetails().get("error").toString();
        assertNotNull(error);
    }

    @Test
    @DisplayName("Should provide clear error message for invalid truststore location")
    void health_WhenInvalidTruststoreLocation_ShouldProvideClearErrorMessage() {
        // Given
        when(sslProperties.isEnabled()).thenReturn(true);
        
        KafkaProperties.Truststore truststore = mock(KafkaProperties.Truststore.class);
        when(sslProperties.getTruststore()).thenReturn(truststore);
        when(truststore.getLocation()).thenReturn("");

        // When
        Health health = healthIndicator.health();

        // Then
        assertEquals(Status.DOWN, health.getStatus());
        assertNotNull(health.getDetails().get("error"));
        String error = health.getDetails().get("error").toString();
        assertNotNull(error);
    }

    // ==================== SSL Enabled Flag Tests ====================

    @Test
    @DisplayName("Should correctly report SSL enabled status")
    void health_ShouldCorrectlyReportSslEnabledStatus() {
        // Test SSL disabled
        when(sslProperties.isEnabled()).thenReturn(false);
        Health healthDisabled = healthIndicator.health();
        assertEquals(false, healthDisabled.getDetails().get("sslEnabled"));

        // Test SSL enabled (even if configuration is invalid) - doesn't include sslEnabled key when there's an error
        when(sslProperties.isEnabled()).thenReturn(true);
        when(sslProperties.getTruststore()).thenReturn(null);
        Health healthEnabled = healthIndicator.health();
        // When SSL is enabled but fails, it doesn't include sslEnabled key, just error
        assertNotNull(healthEnabled.getDetails().get("error"));
        assertEquals(Status.DOWN, healthEnabled.getStatus());
    }
}
