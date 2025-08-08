package com.wifi.scan.consume.config;

import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import lombok.extern.slf4j.Slf4j;

/**
 * Kafka Admin client configuration for health monitoring and administrative operations.
 *
 * <p>This class provides configuration for the Kafka AdminClient, which is used for administrative
 * operations such as topic management, consumer group monitoring, and health checks. It integrates
 * with SSL configuration for secure connections and provides optimized settings for administrative
 * operations.
 *
 * <p>Key Functionality: - Kafka AdminClient bean creation and configuration - SSL/TLS integration
 * for secure administrative operations - Optimized timeout settings for health monitoring -
 * Integration with KafkaProperties and SslConfiguration - Comprehensive logging for administrative
 * operations
 *
 * <p>High-Level Configuration Steps: 1. Configure basic AdminClient properties (bootstrap servers,
 * timeouts) 2. Integrate SSL configuration if SSL is enabled 3. Create and configure AdminClient
 * instance 4. Provide logging and monitoring capabilities
 *
 * <p>Administrative Operations Supported: - Topic metadata retrieval and management - Consumer
 * group monitoring and management - Cluster health and status checking - Partition and replica
 * management - Configuration management
 *
 * <p>SSL Integration: - Automatic SSL property injection when SSL is enabled - Secure
 * administrative operations over encrypted connections - Certificate validation and trust
 * management - Comprehensive SSL configuration support
 *
 * <p>Health Monitoring: - Topic accessibility verification - Consumer group status monitoring -
 * Cluster connectivity testing - Administrative operation validation
 *
 * @see AdminClient
 * @see KafkaProperties
 * @see SslConfiguration
 * @see com.wifi.scan.consume.health
 */
@Slf4j
@Configuration
public class KafkaAdminConfiguration {

  /** Kafka configuration properties for connection and SSL settings */
  @Autowired private KafkaProperties kafkaProperties;

  /** SSL configuration for secure administrative connections */
  @Autowired private SslConfiguration sslConfiguration;

  /**
   * Creates Kafka AdminClient for health monitoring and administrative operations.
   *
   * <p>This method creates and configures a Kafka AdminClient bean that can be used throughout the
   * application for administrative operations, health monitoring, and topic management. It provides
   * optimized settings for administrative operations with proper SSL integration.
   *
   * <p>Processing Steps: 1. Initialize AdminClient configuration properties 2. Set bootstrap
   * servers from Kafka properties 3. Configure timeout settings for administrative operations 4.
   * Integrate SSL properties if SSL is enabled 5. Create and return configured AdminClient instance
   * 6. Log configuration results for monitoring
   *
   * <p>Configuration Properties: - Bootstrap servers: Primary Kafka cluster connection points -
   * Request timeout: 10 seconds for administrative operations - Socket connection setup timeout: 5
   * seconds for connection establishment - SSL properties: Integrated when SSL is enabled
   *
   * <p>SSL Integration: - Checks if SSL is enabled in Kafka properties - Builds SSL properties
   * using SslConfiguration - Integrates SSL properties into AdminClient configuration - Provides
   * secure administrative operations
   *
   * <p>Use Cases: - Health monitoring and connectivity testing - Topic metadata retrieval and
   * management - Consumer group monitoring and administration - Cluster status and health
   * verification - Administrative configuration management
   *
   * @return Configured AdminClient instance ready for administrative operations
   */
  @Bean
  public AdminClient kafkaAdminClient() {
    log.debug("Creating Kafka AdminClient");

    // Initialize AdminClient configuration properties
    Map<String, Object> adminProps = new HashMap<>();

    // Set bootstrap servers for cluster connection
    adminProps.put(
        AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.getBootstrapServers());

    // Configure timeout settings for administrative operations
    adminProps.put(
        AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 10000); // 10 seconds for admin operations
    adminProps.put(
        AdminClientConfig.SOCKET_CONNECTION_SETUP_TIMEOUT_MS_CONFIG,
        5000); // 5 seconds for connection setup

    // Add SSL properties if SSL is enabled for secure administrative operations
    if (kafkaProperties.getSsl().isEnabled()) {
      Map<String, Object> sslProps = sslConfiguration.buildSslProperties(kafkaProperties);
      adminProps.putAll(sslProps);
      log.debug("AdminClient configured with SSL enabled");
    }

    log.info("Kafka AdminClient created successfully");
    return AdminClient.create(adminProps);
  }
}
