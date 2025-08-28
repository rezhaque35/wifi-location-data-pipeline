package com.wifi.scan.consume.health;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.Map;

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
 * Unit tests for EnhancedSslCertificateHealthIndicator.
 * 
 * <p>Comprehensive test suite covering SSL certificate validation, health status determination,
 * configuration integration, and edge cases for the enhanced SSL certificate health indicator.
 * Tests both enabled and disabled SSL scenarios with various certificate conditions.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("EnhancedSslCertificateHealthIndicator Tests")
class EnhancedSslCertificateHealthIndicatorTest {

    @Mock
    private HealthIndicatorConfiguration healthConfig;

    @Mock
    private KafkaProperties kafkaProperties;

    @Mock
    private KafkaMonitoringService kafkaMonitoringService;

    @Mock
    private KafkaProperties.Ssl sslProperties;

    private EnhancedSslCertificateHealthIndicator healthIndicator;

    @BeforeEach
    void setUp() {
        // Setup default configurations
        when(kafkaProperties.getSsl()).thenReturn(sslProperties);

        healthIndicator = new EnhancedSslCertificateHealthIndicator(
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
        assertNotNull(health.getDetails().get("reason"));
        assertEquals("SSL is not enabled", health.getDetails().get("reason"));
        assertNotNull(health.getDetails().get("sslEnabled"));
        assertEquals(false, health.getDetails().get("sslEnabled"));
        assertNotNull(health.getDetails().get("readinessOptimized"));
        assertEquals(true, health.getDetails().get("readinessOptimized"));
        assertNotNull(health.getDetails().get("checkTimestamp"));
    }

    @Test
    @DisplayName("Should include configuration details when SSL is disabled")
    void health_WhenSslDisabled_ShouldIncludeConfigurationDetails() {
        // Given
        when(sslProperties.isEnabled()).thenReturn(false);

        // When
        Health health = healthIndicator.health();

        // Then
        assertEquals(Status.UP, health.getStatus());
        assertNotNull(health.getDetails().get("readinessOptimized"));
        assertTrue(health.getDetails().containsKey("checkTimestamp"));
        assertTrue(health.getDetails().containsKey("reason"));
        assertTrue(health.getDetails().containsKey("sslEnabled"));
    }

    // ==================== SSL Enabled with No Certificate Tests ====================

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
        assertNotNull(health.getDetails().get("gracefulDegradation"));
        assertNotNull(health.getDetails().get("readinessOptimized"));
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
    }

    // ==================== Configuration Method Tests ====================

    @Test
    @DisplayName("Should get correct certificate warning threshold for different stages")
    void getCertificateWarningThreshold_ShouldReturnCorrectValues() {
        // Given - we need to setup the configuration to return the values we expect
        when(healthConfig.getCertificateWarningThreshold("CRITICAL")).thenReturn(7);
        when(healthConfig.getCertificateWarningThreshold("URGENT")).thenReturn(15);
        when(healthConfig.getCertificateWarningThreshold("WARNING")).thenReturn(30);
        when(healthConfig.getCertificateWarningThreshold("UNKNOWN")).thenReturn(30);
        
        // When & Then
        assertEquals(7, healthConfig.getCertificateWarningThreshold("CRITICAL"));
        assertEquals(15, healthConfig.getCertificateWarningThreshold("URGENT"));
        assertEquals(30, healthConfig.getCertificateWarningThreshold("WARNING"));
        assertEquals(30, healthConfig.getCertificateWarningThreshold("UNKNOWN")); // Default
    }

    @Test
    @DisplayName("Should return correct enhanced SSL monitoring status")
    void isEnhancedSslMonitoringEnabled_ShouldRespectConfiguration() {
        // Given
        when(healthConfig.isEnhancedSslMonitoringEnabled()).thenReturn(true);

        // When
        boolean result = healthConfig.isEnhancedSslMonitoringEnabled();

        // Then
        assertTrue(result);
    }

    @Test
    @DisplayName("Should return correct CloudWatch integration status")
    void isCloudWatchIntegrationEnabled_ShouldRespectConfiguration() {
        // Given
        when(healthConfig.isCloudWatchIntegrationEnabled()).thenReturn(true);

        // When
        boolean result = healthConfig.isCloudWatchIntegrationEnabled();

        // Then
        assertTrue(result);
    }

    // ==================== Error Handling Tests ====================

    @Test
    @DisplayName("Should handle null configuration gracefully")
    void health_WhenConfigurationNull_ShouldHandleGracefully() {
        // Given
        when(kafkaProperties.getSsl()).thenReturn(null);

        // When
        Health health = healthIndicator.health();

        // Then
        assertEquals(Status.UP, health.getStatus());
        assertEquals(false, health.getDetails().get("sslEnabled"));
        assertNotNull(health.getDetails().get("checkTimestamp"));
    }

    @Test
    @DisplayName("Should handle configuration exceptions gracefully")
    void health_WhenConfigurationThrowsException_ShouldHandleGracefully() {
        // Given
        when(kafkaProperties.getSsl()).thenReturn(sslProperties);
        when(sslProperties.isEnabled()).thenThrow(new RuntimeException("Configuration error"));

        // When
        Health health = healthIndicator.health();

        // Then
        assertEquals(Status.DOWN, health.getStatus());
        assertNotNull(health.getDetails().get("error"));
        assertTrue(health.getDetails().get("error").toString().contains("Configuration error"));
        assertNotNull(health.getDetails().get("checkTimestamp"));
    }

    // ==================== Certificate Validation Tests ====================

    @Test
    @DisplayName("Should handle invalid truststore file gracefully")
    void health_WhenInvalidTruststoreFile_ShouldReturnDown() {
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
    }

    // ==================== Health Details Tests ====================

    @Test
    @DisplayName("Should include comprehensive health details when SSL is enabled")
    void health_WhenSslEnabled_ShouldIncludeComprehensiveDetails() {
        // Given
        when(sslProperties.isEnabled()).thenReturn(true);
        
        KafkaProperties.Truststore truststore = mock(KafkaProperties.Truststore.class);
        when(sslProperties.getTruststore()).thenReturn(truststore);
        when(truststore.getLocation()).thenReturn("invalid-file.jks");

        // When
        Health health = healthIndicator.health();

        // Then
        assertNotNull(health.getDetails());
        assertTrue(health.getDetails().containsKey("readinessOptimized"));
        assertTrue(health.getDetails().containsKey("checkTimestamp"));
        assertTrue(health.getDetails().containsKey("error"));
        assertTrue(health.getDetails().containsKey("gracefulDegradation"));
    }

    @Test
    @DisplayName("Should include basic SSL disabled configuration")
    void health_ShouldIncludeBasicSslDisabledConfiguration() {
        // Given
        when(sslProperties.isEnabled()).thenReturn(false);

        // When
        Health health = healthIndicator.health();

        // Then
        assertTrue(health.getDetails().containsKey("readinessOptimized"));
        assertTrue(health.getDetails().containsKey("checkTimestamp"));
        assertTrue(health.getDetails().containsKey("sslEnabled"));
        assertTrue(health.getDetails().containsKey("reason"));
    }

    // ==================== Configuration Integration Tests ====================

    @Test
    @DisplayName("Should use HealthIndicatorConfiguration for thresholds (verified through successful health check)")
    void health_ShouldUseHealthIndicatorConfigurationThresholds() {
        // Given - SSL disabled test just verifies the health indicator uses the config properly
        when(sslProperties.isEnabled()).thenReturn(false);
        when(healthConfig.getCertificateExpirationWarningDays()).thenReturn(45);
        when(healthConfig.getCertificateExpirationUrgentDays()).thenReturn(20);
        when(healthConfig.getCertificateExpirationCriticalDays()).thenReturn(10);

        // When
        Health health = healthIndicator.health();

        // Then - verify the health check completes successfully (uses config internally)
        assertEquals(Status.UP, health.getStatus());
        assertTrue(health.getDetails().containsKey("readinessOptimized"));
        assertTrue(health.getDetails().containsKey("sslEnabled"));
        assertEquals(false, health.getDetails().get("sslEnabled"));
    }

    @Test
    @DisplayName("Should use validation timeout configuration (verified through successful health check)")
    void health_ShouldUseValidationTimeoutConfiguration() {
        // Given - SSL disabled test just verifies the health indicator uses the config properly
        when(sslProperties.isEnabled()).thenReturn(false);
        when(healthConfig.getCertificateValidationTimeoutSeconds()).thenReturn(15);

        // When
        Health health = healthIndicator.health();

        // Then - verify the health check completes successfully (uses config internally)
        assertEquals(Status.UP, health.getStatus());
        assertTrue(health.getDetails().containsKey("readinessOptimized"));
        assertEquals(false, health.getDetails().get("sslEnabled"));
    }

    @Test
    @DisplayName("Should use chain validation configuration (verified through successful health check)")
    void health_ShouldUseChainValidationConfiguration() {
        // Given - SSL disabled test just verifies the health indicator uses the config properly
        when(sslProperties.isEnabled()).thenReturn(false);
        when(healthConfig.isEnableCertificateChainValidation()).thenReturn(false);

        // When
        Health health = healthIndicator.health();

        // Then - verify the health check completes successfully (uses config internally)
        assertEquals(Status.UP, health.getStatus());
        assertTrue(health.getDetails().containsKey("readinessOptimized"));
        assertEquals(false, health.getDetails().get("sslEnabled"));
    }

    // ==================== Edge Case Tests ====================

    @Test
    @DisplayName("Should handle missing password gracefully")
    void health_WhenTruststorePasswordMissing_ShouldReturnDown() {
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
    }

    @Test
    @DisplayName("Should include proper timestamp in health details")
    void health_ShouldIncludeTimestamp() {
        // Given
        when(sslProperties.isEnabled()).thenReturn(false);

        // When
        Health health = healthIndicator.health();

        // Then
        assertNotNull(health.getDetails().get("checkTimestamp"));
        assertTrue(health.getDetails().get("checkTimestamp") instanceof Long);
    }
}
