package com.wifi.measurements.transformer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Main application class for WiFi Measurements Transformer Service.
 *
 * <p>This microservice is a critical component of the WiFi location data pipeline that processes
 * raw WiFi scan data from SQS events, transforms and validates the data according to business
 * rules, and writes the processed measurements to AWS S3 Tables using Kinesis Data Firehose.
 *
 * <p><strong>Key Responsibilities:</strong>
 *
 * <ul>
 *   <li>Consumes WiFi scan data from SQS queues triggered by S3 file uploads
 *   <li>Transforms raw scan data into normalized WiFi measurement records
 *   <li>Applies data validation, filtering, and quality assessment rules
 *   <li>Implements feed-based processing for cost-effective SQS operations
 *   <li>Writes processed data to AWS S3 Tables via Kinesis Data Firehose
 * </ul>
 *
 * <p><strong>Architecture Features:</strong>
 *
 * <ul>
 *   <li>Asynchronous processing enabled for improved throughput
 *   <li>Configuration properties scanning for externalized configuration
 *   <li>Spring Boot auto-configuration for AWS services
 *   <li>Comprehensive health monitoring and metrics collection
 * </ul>
 *
 * <p><strong>Data Flow:</strong>
 *
 * <ol>
 *   <li>S3 file upload triggers SQS message
 *   <li>SQS message contains S3 event details
 *   <li>Service downloads and parses JSON data from S3
 *   <li>Data is transformed and validated
 *   <li>Processed measurements are batched and sent to Firehose
 *   <li>Firehose delivers data to S3 Tables for analytics
 * </ol>
 *
 * @author WiFi Location Data Pipeline Team
 * @version 1.0
 * @since 2024
 */
@SpringBootApplication
@EnableAsync
@ConfigurationPropertiesScan("com.wifi.measurements.transformer.config.properties")
public class WifiMeasurementsTransformerApplication {

  /**
   * Main entry point for the WiFi Measurements Transformer Service.
   *
   * <p>This method initializes the Spring Boot application context and starts the service. The
   * application will begin processing SQS messages and transforming WiFi scan data according to the
   * configured business rules.
   *
   * <p>The service runs continuously until terminated, processing messages asynchronously to
   * maintain high throughput and responsiveness.
   *
   * @param args Command line arguments passed to the application
   * @throws Exception if the application fails to start
   */
  public static void main(String[] args) {
    SpringApplication.run(WifiMeasurementsTransformerApplication.class, args);
  }
}
