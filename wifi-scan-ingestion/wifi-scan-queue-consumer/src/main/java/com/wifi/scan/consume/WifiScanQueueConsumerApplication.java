package com.wifi.scan.consume;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;

import lombok.extern.slf4j.Slf4j;

/**
 * Main Spring Boot application class for WiFi Scan Queue Consumer.
 *
 * <p>This application serves as the entry point for a high-performance Kafka consumer that
 * processes WiFi scan data with advanced features including:
 *
 * <p>Key Functionality: - Consumes messages from Apache Kafka with SSL/TLS support - Implements
 * batch processing with rate limiting - Provides comprehensive health monitoring and metrics -
 * Integrates with AWS Firehose for data delivery - Supports dynamic consumer pause/resume
 * operations
 *
 * <p>High-Level Steps: 1. Application startup with graceful shutdown hooks 2. Spring Boot
 * auto-configuration and component scanning 3. Kafka consumer initialization with SSL/TLS
 * configuration 4. Health indicators and metrics registration 5. Batch message listener activation
 * 6. Application ready event handling with startup logging
 *
 * <p>The application is designed for production use with robust error handling, comprehensive
 * monitoring, and scalable architecture for processing large volumes of WiFi scan data efficiently.
 *
 * @see com.wifi.scan.consume.listener.WifiScanBatchMessageListener
 * @see com.wifi.scan.consume.service.ConsumerManagementService
 * @see com.wifi.scan.consume.config.KafkaConsumerConfiguration
 */
@Slf4j
@SpringBootApplication
public class WifiScanQueueConsumerApplication {

  /**
   * Main application entry point.
   *
   * <p>Initializes and starts the Spring Boot application with proper error handling and graceful
   * shutdown capabilities. This method sets up the application context and starts all configured
   * components including Kafka consumers, health indicators, and monitoring services.
   *
   * <p>High-Level Steps: 1. Create SpringApplication instance with main class 2. Register shutdown
   * hook for graceful termination 3. Start the application context 4. Handle startup exceptions
   * with proper error logging 5. Exit with error code if startup fails
   *
   * @param args Command line arguments passed to the application
   */
  public static void main(String[] args) {
    // Create Spring Boot application instance
    SpringApplication app = new SpringApplication(WifiScanQueueConsumerApplication.class);

    // Add shutdown hook for graceful termination
    // This ensures proper cleanup of resources when the application is stopped
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  log.info("Shutting down WiFi Scan Queue Consumer application gracefully...");
                }));

    try {
      // Start the Spring Boot application
      // This will initialize all beans, start Kafka consumers, and activate health checks
      app.run(args);
    } catch (Exception e) {
      // Log startup failure and exit with error code
      log.error("Failed to start WiFi Scan Queue Consumer application", e);
      System.exit(1);
    }
  }

  /**
   * Application ready event handler that executes after all beans are initialized.
   *
   * <p>This method is triggered when the Spring application context is fully loaded and all
   * components are ready. It provides comprehensive startup logging including configuration
   * details, active profiles, and service endpoints.
   *
   * <p>High-Level Steps: 1. Extract environment configuration from Spring context 2. Log
   * application startup banner and configuration details 3. Display Kafka configuration (servers,
   * topic, consumer group) 4. Show SSL/TLS configuration status 5. Log management endpoints and
   * health check URLs 6. Confirm application readiness for message consumption
   *
   * @param event ApplicationReadyEvent containing the application context
   */
  @EventListener(ApplicationReadyEvent.class)
  public void onApplicationReady(ApplicationReadyEvent event) {
    // Get environment configuration for logging
    Environment env = event.getApplicationContext().getEnvironment();

    // Log comprehensive startup information
    log.info("=========================================");
    log.info("WiFi Scan Queue Consumer Started Successfully");
    log.info("=========================================");
    log.info("Application Name: {}", env.getProperty("spring.application.name"));
    log.info("Server Port: {}", env.getProperty("server.port", "8080"));
    log.info("Kafka Bootstrap Servers: {}", env.getProperty("kafka.bootstrap-servers"));
    log.info("Kafka Consumer Group: {}", env.getProperty("kafka.consumer.group-id"));
    log.info("Kafka Topic: {}", env.getProperty("kafka.topic.name"));
    log.info("SSL Enabled: {}", env.getProperty("kafka.ssl.enabled", "false"));
    log.info(
        "Management Endpoints: {}", env.getProperty("management.endpoints.web.exposure.include"));
    log.info("=========================================");
    log.info("Application is ready to consume messages from Kafka");
    log.info(
        "Health check available at: http://{}:{}/frisco-location-wifi-scan-vmb-consumer/health",
        env.getProperty("server.host", "localhost"),
        env.getProperty("server.port", "8080"));
    log.info("=========================================");
  }
}
