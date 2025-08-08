package com.wifi.scan.consume.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.wifi.scan.consume.service.BatchFirehoseMessageService;
import com.wifi.scan.consume.service.KafkaMonitoringService;
import com.wifi.scan.consume.service.MessageCompressionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * REST controller for exposing Kafka consumer metrics and operational information.
 *
 * <p>This controller provides comprehensive REST endpoints for monitoring the Kafka consumer
 * application's performance, health, and operational status. It exposes detailed metrics suitable
 * for integration with monitoring systems like Prometheus and Grafana, as well as operational
 * dashboards.
 *
 * <p>Key Functionality: - Exposes detailed Kafka consumer metrics via REST API - Provides
 * performance monitoring data (processing times, success rates) - Reports consumer state and
 * connectivity information - Offers memory usage and system resource metrics - Supports metrics
 * reset for testing and monitoring periods - Delivers operational status and health information -
 * Provides both JSON and plain text metric formats
 *
 * <p>High-Level Steps: 1. Collect metrics from Kafka monitoring service 2. Aggregate performance
 * and operational data 3. Format metrics for different consumption patterns (JSON, text) 4. Provide
 * operational status and health indicators 5. Support metrics reset for testing scenarios 6.
 * Include metadata and version information
 *
 * <p>The controller is separate from Spring Boot Actuator health endpoints and focuses on
 * operational metrics rather than basic health status. It provides the detailed information needed
 * for production monitoring and alerting.
 *
 * @see com.wifi.scan.consume.service.KafkaMonitoringService
 * @see org.springframework.web.bind.annotation.RestController
 */
@Slf4j
@RestController
@RequestMapping("/api/metrics")
@RequiredArgsConstructor
public class MetricsController {

  /** Service for Kafka monitoring and metrics collection */
  private final KafkaMonitoringService monitoringService;

  /** Service for compressing and encoding messages */
  private final MessageCompressionService compressionService;

  /** Service for delivering messages to Firehose */
  private final BatchFirehoseMessageService firehoseService;

  /**
   * Gets current Kafka consumer metrics for operational monitoring.
   *
   * <p>This endpoint provides comprehensive metrics data in JSON format suitable for monitoring
   * systems like Prometheus and Grafana. It includes performance metrics, operational status, and
   * system resource utilization.
   *
   * <p>High-Level Steps: 1. Collect message processing metrics (consumed, processed, failed) 2.
   * Gather performance metrics (processing times, success rates) 3. Retrieve activity metrics
   * (timestamps, polling status) 4. Collect consumer state information (connectivity, group status)
   * 5. Gather system resource metrics (memory usage) 6. Calculate consumption rate and health
   * indicators 7. Add metadata and version information 8. Return comprehensive metrics in JSON
   * format
   *
   * <p>The metrics include both real-time operational data and historical performance indicators
   * for comprehensive system monitoring.
   *
   * @return ResponseEntity containing comprehensive metrics data in JSON format
   */
  @GetMapping("/kafka")
  public ResponseEntity<Map<String, Object>> getKafkaMetrics() {
    log.debug("Fetching Kafka consumer metrics for operational monitoring");

    // Initialize metrics data container
    Map<String, Object> metricsData = new HashMap<>();

    // Message processing metrics for throughput monitoring
    metricsData.put("totalMessagesConsumed", monitoringService.getTotalMessagesConsumed());
    metricsData.put("totalMessagesProcessed", monitoringService.getTotalMessagesProcessed());
    metricsData.put("totalMessagesFailed", monitoringService.getTotalMessagesFailed());
    metricsData.put("successRate", monitoringService.getSuccessRate());
    metricsData.put("errorRate", monitoringService.getErrorRate());

    // Performance metrics for latency monitoring
    metricsData.put("averageProcessingTimeMs", monitoringService.getAverageProcessingTimeMs());
    metricsData.put("minProcessingTimeMs", monitoringService.getMinProcessingTimeMs());
    metricsData.put("maxProcessingTimeMs", monitoringService.getMaxProcessingTimeMs());

    // Activity metrics for operational monitoring
    metricsData.put("firstMessageTimestamp", monitoringService.getFirstMessageTimestamp());
    metricsData.put("lastMessageTimestamp", monitoringService.getLastMessageTimestamp());
    metricsData.put("lastPollTimestamp", monitoringService.getLastPollTimestamp());
    metricsData.put("isPollingActive", monitoringService.isPollingActive());

    // Consumer state metrics for health monitoring
    metricsData.put("isConsumerConnected", monitoringService.isConsumerConnected());
    metricsData.put("consumerGroupActive", monitoringService.isConsumerGroupActive());

    // Memory metrics for resource monitoring
    metricsData.put("memoryUsagePercentage", monitoringService.getMemoryUsagePercentage());
    metricsData.put("usedMemoryMB", monitoringService.getUsedMemoryMB());
    metricsData.put("totalMemoryMB", monitoringService.getTotalMemoryMB());
    metricsData.put("maxMemoryMB", monitoringService.getMaxMemoryMB());

    // Consumption rate metrics for performance monitoring
    metricsData.put("consumptionRate", monitoringService.getConsumptionRate());
    metricsData.put("isConsumptionHealthy", monitoringService.isConsumptionHealthy());

    // Metadata for monitoring system integration
    metricsData.put("timestamp", System.currentTimeMillis());
    metricsData.put("metricsVersion", "2.0.0");

    return ResponseEntity.ok(metricsData);
  }

  /**
   * Gets a detailed metrics summary as plain text for operational monitoring.
   *
   * <p>This endpoint provides a human-readable summary of key metrics in plain text format, useful
   * for quick debugging, log analysis, and simple monitoring dashboards that don't require JSON
   * parsing.
   *
   * <p>High-Level Steps: 1. Request metrics summary from monitoring service 2. Format response as
   * plain text with appropriate headers 3. Return human-readable metrics summary
   *
   * <p>The summary includes key performance indicators and operational status in a format that's
   * easy to read and parse for quick operational checks.
   *
   * @return ResponseEntity containing formatted metrics summary in plain text
   */
  @GetMapping("/kafka/summary")
  public ResponseEntity<String> getKafkaMetricsSummary() {
    log.debug("Fetching Kafka consumer metrics summary for operational monitoring");

    // Get formatted metrics summary from monitoring service
    String summary = monitoringService.getMetricsSummary();
    return ResponseEntity.ok().header("Content-Type", "text/plain").body(summary);
  }

  /**
   * Resets all Kafka consumer metrics.
   *
   * <p>This endpoint allows resetting all collected metrics, useful for testing scenarios, starting
   * fresh monitoring periods, or clearing historical data when needed for operational purposes.
   *
   * <p>High-Level Steps: 1. Reset all metrics in the monitoring service 2. Log the reset operation
   * for audit purposes 3. Return confirmation of successful reset
   *
   * <p>Use this endpoint carefully as it will clear all historical metrics data and start fresh
   * monitoring from the reset point.
   *
   * @return ResponseEntity confirming metrics reset operation
   */
  @PostMapping("/kafka/reset")
  public ResponseEntity<Map<String, String>> resetKafkaMetrics() {
    log.info("Resetting Kafka consumer metrics as requested");

    // Reset all metrics in the monitoring service
    monitoringService.resetMetrics();

    // Return confirmation of successful reset
    Map<String, String> response = new HashMap<>();
    response.put("status", "success");
    response.put("message", "Kafka consumer metrics reset successfully");
    response.put("timestamp", String.valueOf(System.currentTimeMillis()));

    return ResponseEntity.ok(response);
  }

  /**
   * Gets operational status information for the Kafka consumer.
   *
   * <p>This endpoint provides a focused view of the operational status, including connectivity,
   * health indicators, and key performance metrics for quick operational assessment.
   *
   * <p>High-Level Steps: 1. Collect operational status from monitoring service 2. Gather key health
   * and performance indicators 3. Include connectivity and consumer state information 4. Add
   * operational metadata and timestamps 5. Return focused operational status data
   *
   * <p>This endpoint is designed for operational dashboards and quick status checks without the
   * full metrics detail.
   *
   * @return ResponseEntity containing operational status information
   */
  @GetMapping("/kafka/status")
  public ResponseEntity<Map<String, Object>> getOperationalStatus() {
    log.debug("Fetching Kafka consumer operational status");

    Map<String, Object> status = new HashMap<>();

    // Core operational status
    status.put("isConsumerConnected", monitoringService.isConsumerConnected());
    status.put("isPollingActive", monitoringService.isPollingActive());
    status.put("isConsumptionHealthy", monitoringService.isConsumptionHealthy());

    // Key performance indicators
    status.put("successRate", monitoringService.getSuccessRate());
    status.put("errorRate", monitoringService.getErrorRate());
    status.put("consumptionRate", monitoringService.getConsumptionRate());

    // System health indicators
    status.put("memoryUsagePercentage", monitoringService.getMemoryUsagePercentage());
    status.put("lastMessageTimestamp", monitoringService.getLastMessageTimestamp());

    // Operational metadata
    status.put("timestamp", System.currentTimeMillis());
    status.put("statusVersion", "1.0.0");

    return ResponseEntity.ok(status);
  }

  /**
   * Accepts a WiFi scan message as JSON and delivers it to Firehose.
   *
   * <p>This endpoint provides a REST API for accepting individual WiFi scan messages and processing
   * them through the compression and Firehose delivery pipeline. It converts the single message to
   * a list format expected by the batch services and orchestrates the complete delivery process.
   *
   * <p>High-Level Steps: 1. Accept WiFi scan message as JSON in request body 2. Convert message to
   * JSON string format 3. Create single-element list for batch processing 4. Compress and encode
   * the message using MessageCompressionService 5. Deliver compressed message to Firehose using
   * BatchFirehoseMessageService 6. Return delivery status and metrics
   *
   * <p>Expected Message Format: { "wifiScanResults": [ { "macAddress": "aa:bb:cc:dd:ee:ff",
   * "signalStrength": -65.4, "frequency": 2437, "ssid": "OfficeWiFi", "linkSpeed": 866,
   * "channelWidth": 80 } ], "client": "wifi-scan-generator", "requestId": "req-1735820400-12345",
   * "application": "wifi-scan-test-suite", "calculationDetail": true }
   *
   * @param wifiScanMessage The WiFi scan message object to process
   * @return ResponseEntity containing delivery status and metrics
   */
  @PostMapping("/wifi-scan")
  public ResponseEntity<Map<String, Object>> processWifiScanMessage(
      @RequestBody Object wifiScanMessage) {
    log.info("Received WiFi scan message for direct processing");

    Map<String, Object> response = new HashMap<>();

    try {
      // Step 1: Convert message to JSON string
      String messageJson = convertToJsonString(wifiScanMessage);
      if (messageJson == null) {
        response.put("status", "error");
        response.put("message", "Failed to convert message to JSON format");
        response.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.badRequest().body(response);
      }

      // Step 2: Create single-element list for batch processing
      List<String> messageList = List.of(messageJson);
      log.debug("Created single-element message list for batch processing");

      // Step 3: Compress and encode the message
      List<String> compressedMessages = compressionService.compressAndEncodeBatch(messageList);
      if (compressedMessages.isEmpty()) {
        response.put("status", "error");
        response.put("message", "Failed to compress and encode message");
        response.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.internalServerError().body(response);
      }

      // Step 4: Deliver to Firehose
      boolean deliverySuccess = firehoseService.deliverBatch(compressedMessages);

      // Step 5: Build response
      response.put("status", deliverySuccess ? "success" : "error");
      response.put(
          "message",
          deliverySuccess
              ? "WiFi scan message delivered successfully to Firehose"
              : "Failed to deliver WiFi scan message to Firehose");
      response.put("originalMessageSize", messageJson.length());
      response.put("compressedMessageSize", compressedMessages.get(0).length());
      response.put(
          "compressionRatio",
          calculateCompressionRatio(messageJson.length(), compressedMessages.get(0).length()));
      response.put("timestamp", System.currentTimeMillis());

      if (deliverySuccess) {
        log.info("Successfully processed and delivered WiFi scan message to Firehose");
        return ResponseEntity.ok(response);
      } else {
        log.error("Failed to deliver WiFi scan message to Firehose");
        return ResponseEntity.internalServerError().body(response);
      }

    } catch (Exception e) {
      log.error("Error processing WiFi scan message: {}", e.getMessage(), e);
      response.put("status", "error");
      response.put("message", "Internal server error: " + e.getMessage());
      response.put("timestamp", System.currentTimeMillis());
      return ResponseEntity.internalServerError().body(response);
    }
  }

  /**
   * Convert object to JSON string using Jackson ObjectMapper.
   *
   * @param obj The object to convert
   * @return JSON string representation or null if conversion fails
   */
  private String convertToJsonString(Object obj) {
    try {
      com.fasterxml.jackson.databind.ObjectMapper mapper =
          new com.fasterxml.jackson.databind.ObjectMapper();
      return mapper.writeValueAsString(obj);
    } catch (Exception e) {
      log.error("Failed to convert object to JSON string: {}", e.getMessage());
      return null;
    }
  }

  /**
   * Calculate compression ratio as percentage.
   *
   * @param originalSize Original size in characters
   * @param compressedSize Compressed size in characters
   * @return Compression ratio as percentage (0-100)
   */
  private double calculateCompressionRatio(int originalSize, int compressedSize) {
    if (originalSize == 0) return 0.0;
    return ((double) (originalSize - compressedSize) / originalSize) * 100.0;
  }
}
