package com.wifi.scan.consume.health;

import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Enumeration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import com.wifi.scan.consume.config.HealthIndicatorConfiguration;
import com.wifi.scan.consume.config.KafkaProperties;
import com.wifi.scan.consume.service.KafkaMonitoringService;

import lombok.extern.slf4j.Slf4j;

/**
 * Health indicator for SSL certificate validation. Checks SSL/TLS configuration, certificate
 * accessibility, and expiration dates.
 */
@Slf4j
@Component("sslCertificate")
public class SslCertificateHealthIndicator implements HealthIndicator {

  private static final String CHECK_TIMESTAMP_KEY = "checkTimestamp";
  private static final String REASON_KEY = "reason";

  private final KafkaMonitoringService kafkaMonitoringService;
  private final HealthIndicatorConfiguration config;
  private final KafkaProperties kafkaProperties;

  @Autowired
  public SslCertificateHealthIndicator(
      KafkaMonitoringService kafkaMonitoringService,
      HealthIndicatorConfiguration config,
      KafkaProperties kafkaProperties) {
    this.kafkaMonitoringService = kafkaMonitoringService;
    this.config = config;
    this.kafkaProperties = kafkaProperties;
  }

  @Override
  public Health health() {
    if (!kafkaProperties.getSsl().isEnabled()) {
      return Health.up()
          .withDetail("sslEnabled", false)
          .withDetail(REASON_KEY, "SSL is not enabled")
          .withDetail(CHECK_TIMESTAMP_KEY, System.currentTimeMillis())
          .build();
    }

    try {
      log.debug("Checking SSL certificate health");

      boolean isSslHealthy = kafkaMonitoringService.isSslConnectionHealthy();
      Health.Builder healthBuilder = Health.up();

      // Check keystore accessibility and certificate expiration
      CertificateInfo keystoreInfo =
          checkCertificateStore(
              kafkaProperties.getSsl().getKeystore().getLocation(),
              kafkaProperties.getSsl().getKeystore().getPassword(),
              kafkaProperties.getSsl().getKeystore().getType());

      // Check truststore accessibility and certificate expiration
      CertificateInfo truststoreInfo =
          checkCertificateStore(
              kafkaProperties.getSsl().getTruststore().getLocation(),
              kafkaProperties.getSsl().getTruststore().getPassword(),
              kafkaProperties.getSsl().getTruststore().getType());

      boolean hasExpiringSoon = keystoreInfo.isExpiringSoon() || truststoreInfo.isExpiringSoon();
      boolean hasExpired = keystoreInfo.isExpired() || truststoreInfo.isExpired();

      // Determine the minimum days until expiry across all stores for CloudWatch metric & warning
      // level
      long minDaysUntilExpiry =
          Math.min(
              keystoreInfo.getDaysUntilExpiry() == -1
                  ? Long.MAX_VALUE
                  : keystoreInfo.getDaysUntilExpiry(),
              truststoreInfo.getDaysUntilExpiry() == -1
                  ? Long.MAX_VALUE
                  : truststoreInfo.getDaysUntilExpiry());

      String warningLevel = resolveWarningLevel(minDaysUntilExpiry);

      if (!isSslHealthy) {
        healthBuilder = Health.down().withDetail(REASON_KEY, "SSL connection is not healthy");
      } else if (hasExpired) {
        healthBuilder = Health.down().withDetail(REASON_KEY, "SSL certificates have expired");
      } else if (hasExpiringSoon) {
        healthBuilder = Health.up();
        if (warningLevel != null) {
          healthBuilder.withDetail(
              "warning",
              "SSL certificates are "
                  + warningLevel.toLowerCase()
                  + " ("
                  + minDaysUntilExpiry
                  + " days left)");
        }
      }

      healthBuilder
          .withDetail("sslEnabled", true)
          .withDetail("sslConnectionHealthy", isSslHealthy)
          .withDetail("keystoreAccessible", keystoreInfo.isAccessible())
          .withDetail("truststoreAccessible", truststoreInfo.isAccessible())
          .withDetail("keystoreExpired", keystoreInfo.isExpired())
          .withDetail("truststoreExpired", truststoreInfo.isExpired())
          .withDetail("keystoreExpiringSoon", keystoreInfo.isExpiringSoon())
          .withDetail("truststoreExpiringSoon", truststoreInfo.isExpiringSoon())
          .withDetail("keystoreDaysUntilExpiry", keystoreInfo.getDaysUntilExpiry())
          .withDetail("truststoreDaysUntilExpiry", truststoreInfo.getDaysUntilExpiry())
          .withDetail("minimumDaysUntilExpiry", minDaysUntilExpiry);

      // Avoid passing null to withDetail to prevent IllegalArgumentException
      if (warningLevel != null) {
        healthBuilder.withDetail("certificateExpiryWarningLevel", warningLevel);
      }

      return healthBuilder.withDetail(CHECK_TIMESTAMP_KEY, System.currentTimeMillis()).build();

    } catch (Exception e) {
      log.error("Error checking SSL certificate health", e);
      return Health.down()
          .withDetail("error", e.getMessage())
          .withDetail(CHECK_TIMESTAMP_KEY, System.currentTimeMillis())
          .build();
    }
  }

  private CertificateInfo checkCertificateStore(String location, String password, String type) {
    try {
      Resource resource =
          location.startsWith("classpath:")
              ? new ClassPathResource(location.substring("classpath:".length()))
              : new FileSystemResource(location);

      if (!resource.exists()) {
        return new CertificateInfo(false, true, true, -1);
      }

      KeyStore keyStore = KeyStore.getInstance(type);
      try (InputStream inputStream = resource.getInputStream()) {
        keyStore.load(inputStream, password.toCharArray());
      }

      long minDaysUntilExpiry = Long.MAX_VALUE;
      boolean hasExpired = false;

      Enumeration<String> aliases = keyStore.aliases();
      while (aliases.hasMoreElements()) {
        String alias = aliases.nextElement();
        Certificate cert = keyStore.getCertificate(alias);

        if (cert instanceof X509Certificate x509Cert) {
          LocalDateTime expiry =
              x509Cert.getNotAfter().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();

          long daysUntilExpiry = ChronoUnit.DAYS.between(LocalDateTime.now(), expiry);

          if (daysUntilExpiry < 0) {
            hasExpired = true;
          }

          minDaysUntilExpiry = Math.min(minDaysUntilExpiry, daysUntilExpiry);
        }
      }

      boolean expiringSoon = minDaysUntilExpiry <= config.getCertificateExpirationWarningDays();

      return new CertificateInfo(
          true,
          hasExpired,
          expiringSoon,
          minDaysUntilExpiry == Long.MAX_VALUE ? -1 : minDaysUntilExpiry);

    } catch (Exception e) {
      log.warn("Error checking certificate store: {}", location, e);
      return new CertificateInfo(false, true, true, -1);
    }
  }

  /**
   * Determine the warning level based on the minimum days until certificate expiry.
   *
   * <p>WARNING –&gt; &gt;= urgent threshold (default 30 days) &amp;&amp; &lt;= warning threshold
   * URGENT –&gt; &gt;= critical threshold (default 15 days) &amp;&amp; &lt; warning threshold
   * CRITICAL –&gt; &lt;= critical threshold (default 7 days) The method returns <code>null</code>
   * when the certificate is considered healthy (i.e., days until expiry is above the warning
   * threshold).
   *
   * @param daysUntilExpiry minimum days until any certificate expires
   * @return warning level string or {@code null} if no warning needed
   */
  private String resolveWarningLevel(long daysUntilExpiry) {
    if (daysUntilExpiry < 0) {
      return "EXPIRED";
    }
    if (daysUntilExpiry <= config.getCertificateExpirationCriticalDays()) {
      return "CRITICAL";
    }
    if (daysUntilExpiry <= config.getCertificateExpirationUrgentDays()) {
      return "URGENT";
    }
    if (daysUntilExpiry <= config.getCertificateExpirationWarningDays()) {
      return "WARNING";
    }
    return null;
  }

  private static class CertificateInfo {
    private final boolean accessible;
    private final boolean expired;
    private final boolean expiringSoon;
    private final long daysUntilExpiry;

    public CertificateInfo(
        boolean accessible, boolean expired, boolean expiringSoon, long daysUntilExpiry) {
      this.accessible = accessible;
      this.expired = expired;
      this.expiringSoon = expiringSoon;
      this.daysUntilExpiry = daysUntilExpiry;
    }

    public boolean isAccessible() {
      return accessible;
    }

    public boolean isExpired() {
      return expired;
    }

    public boolean isExpiringSoon() {
      return expiringSoon;
    }

    public long getDaysUntilExpiry() {
      return daysUntilExpiry;
    }
  }
}
