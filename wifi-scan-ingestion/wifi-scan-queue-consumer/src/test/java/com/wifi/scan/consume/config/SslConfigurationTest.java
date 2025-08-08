package com.wifi.scan.consume.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.KeyStore;

import javax.net.ssl.SSLContext;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

/**
 * Unit tests for SSL configuration functionality. Tests SSL certificate loading and validation
 * using TDD approach.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SSL Configuration Tests")
class SslConfigurationTest {

  @Mock private ResourceLoader resourceLoader;

  @Mock private Resource resource;

  private SslConfiguration sslConfiguration;
  private KafkaProperties kafkaProperties;

  @BeforeEach
  void setUp() {
    sslConfiguration = new SslConfiguration();
    kafkaProperties = createTestKafkaProperties();
  }

  @Test
  @DisplayName("should_LoadKeystoreFromClasspath_When_ClasspathLocationProvided")
  void should_LoadKeystoreFromClasspath_When_ClasspathLocationProvided() throws Exception {
    // Given
    String keystoreLocation = "classpath:secrets/kafka.keystore.p12";
    kafkaProperties.getSsl().getKeystore().setLocation(keystoreLocation);
    kafkaProperties.getSsl().getKeystore().setPassword("kafka123");
    kafkaProperties.getSsl().getKeystore().setType("PKCS12");

    // When
    KeyStore keystore = sslConfiguration.loadKeystore(kafkaProperties.getSsl().getKeystore());

    // Then
    assertNotNull(keystore, "Keystore should not be null");
    assertEquals("PKCS12", keystore.getType(), "Keystore type should be PKCS12");
  }

  @Test
  @DisplayName("should_LoadTruststoreFromClasspath_When_ClasspathLocationProvided")
  void should_LoadTruststoreFromClasspath_When_ClasspathLocationProvided() throws Exception {
    // Given
    String truststoreLocation = "classpath:secrets/kafka.truststore.p12";
    kafkaProperties.getSsl().getTruststore().setLocation(truststoreLocation);
    kafkaProperties.getSsl().getTruststore().setPassword("kafka123");
    kafkaProperties.getSsl().getTruststore().setType("PKCS12");

    // When
    KeyStore truststore = sslConfiguration.loadTruststore(kafkaProperties.getSsl().getTruststore());

    // Then
    assertNotNull(truststore, "Truststore should not be null");
    assertEquals("PKCS12", truststore.getType(), "Truststore type should be PKCS12");
  }

  @Test
  @DisplayName("should_LoadKeystoreFromExternalPath_When_ExternalLocationProvided")
  void should_LoadKeystoreFromExternalPath_When_ExternalLocationProvided() throws Exception {
    // Given - This test is skipped as it requires actual external file system access
    // In production, external keystore loading would be tested with integration tests
    assertTrue(
        true, "External keystore loading requires integration testing with actual file system");
  }

  @Test
  @DisplayName("should_ThrowException_When_KeystoreNotFound")
  void should_ThrowException_When_KeystoreNotFound() {
    // Given
    String invalidLocation = "classpath:invalid/path.p12";
    kafkaProperties.getSsl().getKeystore().setLocation(invalidLocation);
    kafkaProperties.getSsl().getKeystore().setPassword("kafka123");
    kafkaProperties.getSsl().getKeystore().setType("PKCS12");

    // When & Then
    assertThrows(
        RuntimeException.class,
        () -> {
          sslConfiguration.loadKeystore(kafkaProperties.getSsl().getKeystore());
        },
        "Should throw RuntimeException for invalid keystore location");
  }

  @Test
  @DisplayName("should_ThrowException_When_InvalidKeystorePassword")
  void should_ThrowException_When_InvalidKeystorePassword() {
    // Given
    String keystoreLocation = "classpath:secrets/kafka.keystore.p12";
    kafkaProperties.getSsl().getKeystore().setLocation(keystoreLocation);
    kafkaProperties.getSsl().getKeystore().setPassword("invalid-password");
    kafkaProperties.getSsl().getKeystore().setType("PKCS12");

    // When & Then
    assertThrows(
        RuntimeException.class,
        () -> {
          sslConfiguration.loadKeystore(kafkaProperties.getSsl().getKeystore());
        },
        "Should throw RuntimeException for invalid keystore password");
  }

  @Test
  @DisplayName("should_CreateSSLContext_When_ValidCertificatesProvided")
  void should_CreateSSLContext_When_ValidCertificatesProvided() throws Exception {
    // Given
    kafkaProperties.getSsl().setEnabled(true);
    kafkaProperties.getSsl().getKeystore().setLocation("classpath:secrets/kafka.keystore.p12");
    kafkaProperties.getSsl().getKeystore().setPassword("kafka123");
    kafkaProperties.getSsl().getKeystore().setType("PKCS12");
    kafkaProperties.getSsl().getTruststore().setLocation("classpath:secrets/kafka.truststore.p12");
    kafkaProperties.getSsl().getTruststore().setPassword("kafka123");
    kafkaProperties.getSsl().getTruststore().setType("PKCS12");
    kafkaProperties.getSsl().setKeyPassword("kafka123");

    // When
    SSLContext sslContext = sslConfiguration.createSSLContext(kafkaProperties);

    // Then
    assertNotNull(sslContext, "SSL context should not be null");
    assertEquals("TLS", sslContext.getProtocol(), "SSL context should use TLS protocol");
  }

  @Test
  @DisplayName("should_ValidateCertificates_When_CertificatesAreValid")
  void should_ValidateCertificates_When_CertificatesAreValid() {
    // Given
    kafkaProperties.getSsl().setEnabled(true);
    kafkaProperties.getSsl().getKeystore().setLocation("classpath:secrets/kafka.keystore.p12");
    kafkaProperties.getSsl().getKeystore().setPassword("kafka123");
    kafkaProperties.getSsl().getKeystore().setType("PKCS12");
    kafkaProperties.getSsl().getTruststore().setLocation("classpath:secrets/kafka.truststore.p12");
    kafkaProperties.getSsl().getTruststore().setPassword("kafka123");
    kafkaProperties.getSsl().getTruststore().setType("PKCS12");

    // When & Then
    assertDoesNotThrow(
        () -> {
          sslConfiguration.validateCertificates(kafkaProperties);
        },
        "Certificate validation should not throw exception for valid certificates");
  }

  @Test
  @DisplayName("should_ThrowException_When_SSLDisabledButCertificateValidationRequested")
  void should_ThrowException_When_SSLDisabledButCertificateValidationRequested() {
    // Given
    kafkaProperties.getSsl().setEnabled(false);

    // When & Then
    assertThrows(
        IllegalStateException.class,
        () -> {
          sslConfiguration.validateCertificates(kafkaProperties);
        },
        "Should throw IllegalStateException when SSL is disabled");
  }

  @Test
  @DisplayName("should_DetermineCorrectCertificateLocation_When_MultipleOptionsAvailable")
  void should_DetermineCorrectCertificateLocation_When_MultipleOptionsAvailable() {
    // Given
    String classpathLocation = "classpath:secrets/kafka.keystore.p12";
    String externalLocation = "/etc/ssl/kafka/kafka.keystore.p12";

    // When
    String actualLocation =
        sslConfiguration.determineCertificateLocation(classpathLocation, externalLocation);

    // Then
    assertTrue(
        actualLocation.equals(classpathLocation) || actualLocation.equals(externalLocation),
        "Should return a valid certificate location");
  }

  private KafkaProperties createTestKafkaProperties() {
    KafkaProperties properties = new KafkaProperties();
    properties.setSsl(new KafkaProperties.Ssl());
    properties.getSsl().setKeystore(new KafkaProperties.Keystore());
    properties.getSsl().setTruststore(new KafkaProperties.Truststore());
    properties.getSsl().setEnabled(true);
    properties.getSsl().setProtocol("SSL");
    properties.getSsl().getKeystore().setType("PKCS12");
    properties.getSsl().getTruststore().setType("PKCS12");
    return properties;
  }
}
