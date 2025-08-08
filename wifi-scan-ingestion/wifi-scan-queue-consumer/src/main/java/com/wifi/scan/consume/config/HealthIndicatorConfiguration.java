package com.wifi.scan.consume.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

/**
 * Enhanced configuration properties for health indicators and monitoring systems.
 *
 * <p>This class provides comprehensive configuration for all health monitoring aspects of the WiFi
 * scan queue consumer application. It controls timeouts, thresholds, behavior of custom health
 * indicators, and integration with external monitoring systems like CloudWatch and Kubernetes.
 *
 * <p>Key Functionality: - General health indicator settings and timeouts - Enhanced SSL certificate
 * monitoring with multi-threshold warnings - CloudWatch metrics integration and configuration -
 * Kubernetes event generation settings - Readiness probe optimization and operational guidance -
 * Memory and consumption rate monitoring thresholds
 *
 * <p>High-Level Configuration Areas: 1. General health monitoring settings (timeouts, thresholds,
 * caching) 2. SSL certificate monitoring with progressive warning levels 3. CloudWatch integration
 * for metrics and alerting 4. Kubernetes readiness probe optimization 5. Enhanced operational
 * guidance and health scoring
 *
 * <p>Monitoring Integration: - Supports multiple monitoring platforms (CloudWatch, Kubernetes,
 * Prometheus) - Provides configurable thresholds for different alert levels - Enables caching for
 * performance optimization - Supports operational guidance for troubleshooting
 *
 * <p>SSL Certificate Management: - Multi-stage expiration warnings (30/15/7 days) - Certificate
 * chain validation - Enhanced SSL monitoring capabilities - Health scoring for certificate status
 *
 * @see org.springframework.boot.actuate.health.HealthIndicator
 * @see com.wifi.scan.consume.health
 */
@Data
@Component
@ConfigurationProperties(prefix = "health.indicator")
public class HealthIndicatorConfiguration {

  // General health indicator settings

  /** Timeout in seconds for health check operations */
  private int timeoutSeconds = 5;

  /** Memory usage threshold percentage for health warnings */
  private int memoryThresholdPercentage = 90;

  /** Timeout in minutes for consumption activity checks */
  private int consumptionTimeoutMinutes = 5;

  /** Minimum required consumption rate for healthy status */
  private double minimumConsumptionRate = 0.0;

  /** Number of retry attempts for health check operations */
  private int retryAttempts = 3;

  /** Whether to enable caching for health check results */
  private boolean enableCaching = true;

  /** Cache TTL in seconds for health check results */
  private int cacheTtlSeconds = 30;

  // Enhanced SSL Certificate Readiness Configuration

  /** Days before certificate expiration to trigger warning alerts */
  private int certificateExpirationWarningDays = 30;

  /** Days before certificate expiration to trigger urgent alerts */
  private int certificateExpirationUrgentDays = 15;

  /** Days before certificate expiration to trigger critical alerts */
  private int certificateExpirationCriticalDays = 7;

  /** Timeout in seconds for certificate validation operations */
  private int certificateValidationTimeoutSeconds = 10;

  /** Whether to enable full certificate chain validation */
  private boolean enableCertificateChainValidation = true;

  /** Whether to enable enhanced SSL monitoring features */
  private boolean enableEnhancedSslMonitoring = true;

  // CloudWatch Integration Configuration

  /** Whether to enable CloudWatch metrics export */
  private boolean enableCloudWatchMetrics = true;

  /** CloudWatch metric namespace for SSL-related metrics */
  private String cloudWatchMetricNamespace = "KafkaConsumer/SSL";

  /** CloudWatch metric name for certificate expiry tracking */
  private String certificateExpiryMetricName = "ssl_certificate_expiry_days";

  /** Whether to enable Kubernetes event generation for health issues */
  private boolean enableKubernetesEventGeneration = true;

  // Enhanced Readiness Specific Settings

  /** Whether to enable readiness probe optimization */
  private boolean enableReadinessOptimization = true;

  /** Whether to enable operational guidance in health responses */
  private boolean enableOperationalGuidance = true;

  /** Whether to enable certificate health scoring */
  private boolean enableCertificateHealthScore = true;

  /** Frequency in seconds for readiness probe checks */
  private int readinessProbeFrequencySeconds = 10;

  /**
   * Get the warning threshold for the specified alert stage.
   *
   * <p>This method provides a centralized way to retrieve certificate expiration thresholds based
   * on the alert stage. It supports progressive warning levels to provide early notification of
   * certificate expiration issues.
   *
   * <p>Alert Stages: - WARNING: Early notification (default 30 days) - URGENT: Immediate attention
   * required (default 15 days) - CRITICAL: Critical action required (default 7 days)
   *
   * <p>Processing Steps: 1. Convert stage parameter to uppercase for case-insensitive matching 2.
   * Match stage to corresponding threshold configuration 3. Return appropriate threshold value 4.
   * Fall back to warning threshold for unknown stages
   *
   * <p>Use Cases: - Health indicator configuration - Alert system threshold management -
   * Certificate monitoring automation - Operational dashboard configuration
   *
   * @param stage the alert stage (WARNING, URGENT, CRITICAL)
   * @return the number of days for the threshold
   */
  public int getCertificateWarningThreshold(String stage) {
    return switch (stage.toUpperCase()) {
      case "CRITICAL" -> certificateExpirationCriticalDays;
      case "URGENT" -> certificateExpirationUrgentDays;
      case "WARNING" -> certificateExpirationWarningDays;
      default -> certificateExpirationWarningDays;
    };
  }

  /**
   * Check if enhanced SSL monitoring features are enabled.
   *
   * <p>This method determines whether the application should use enhanced SSL monitoring
   * capabilities. It requires both the general enhanced monitoring flag and readiness optimization
   * to be enabled.
   *
   * <p>Enhanced SSL Monitoring Features: - Certificate chain validation - Progressive expiration
   * warnings - Health scoring and operational guidance - CloudWatch metrics integration -
   * Kubernetes event generation
   *
   * <p>Processing Steps: 1. Check if enhanced SSL monitoring is enabled 2. Check if readiness
   * optimization is enabled 3. Return true only if both conditions are met
   *
   * <p>Use Cases: - Conditional feature enablement - Health indicator configuration - Monitoring
   * system integration - Performance optimization decisions
   *
   * @return true if enhanced monitoring is enabled
   */
  public boolean isEnhancedSslMonitoringEnabled() {
    return enableEnhancedSslMonitoring && enableReadinessOptimization;
  }

  /**
   * Check if CloudWatch integration is fully enabled.
   *
   * <p>This method determines whether CloudWatch integration features are fully operational. It
   * requires both CloudWatch metrics export and Kubernetes event generation to be enabled.
   *
   * <p>CloudWatch Integration Features: - SSL certificate expiry metrics - Health status metrics -
   * Kubernetes event generation - Automated alerting and monitoring
   *
   * <p>Processing Steps: 1. Check if CloudWatch metrics export is enabled 2. Check if Kubernetes
   * event generation is enabled 3. Return true only if both conditions are met
   *
   * <p>Use Cases: - CloudWatch integration configuration - Monitoring system setup - Alert and
   * notification management - Infrastructure automation
   *
   * @return true if CloudWatch integration is enabled
   */
  public boolean isCloudWatchIntegrationEnabled() {
    return enableCloudWatchMetrics && enableKubernetesEventGeneration;
  }
}
