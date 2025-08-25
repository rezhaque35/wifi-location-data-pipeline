package com.wifi.scan.consume.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.wifi.scan.consume.service.BatchFirehoseMessageService;
import com.wifi.scan.consume.service.KafkaMonitoringService;
import com.wifi.scan.consume.service.MessageTransformationService;

/**
 * Unit tests for MetricsController.
 *
 * <p>Tests all REST endpoints and operations for Kafka consumer metrics and operational status
 * monitoring.
 */
@ExtendWith(MockitoExtension.class)
class MetricsControllerTest {

  @Mock private KafkaMonitoringService monitoringService;

  @Mock private MessageTransformationService compressionService;

  @Mock private BatchFirehoseMessageService firehoseService;

  @InjectMocks private MetricsController metricsController;

  private void setupMetricsServiceForGetKafkaMetrics() {
    when(monitoringService.getTotalMessagesConsumed()).thenReturn(100L);
    when(monitoringService.getTotalMessagesProcessed()).thenReturn(95L);
    when(monitoringService.getTotalMessagesFailed()).thenReturn(5L);
    when(monitoringService.getSuccessRate()).thenReturn(0.95);
    when(monitoringService.getErrorRate()).thenReturn(0.05);
    when(monitoringService.getAverageProcessingTimeMs()).thenReturn(150.0);
    when(monitoringService.getMinProcessingTimeMs()).thenReturn(50L);
    when(monitoringService.getMaxProcessingTimeMs()).thenReturn(300L);
    when(monitoringService.getFirstMessageTimestamp()).thenReturn("2023-01-01T00:00:00");
    when(monitoringService.getLastMessageTimestamp()).thenReturn("2023-01-01T00:02:00");
    when(monitoringService.getLastPollTimestamp()).thenReturn("2023-01-01T00:01:50");
    when(monitoringService.isPollingActive()).thenReturn(true);
    when(monitoringService.isConsumerConnected()).thenReturn(true);
    when(monitoringService.isConsumerGroupActive()).thenReturn(true);
    when(monitoringService.getMemoryUsagePercentage()).thenReturn(75.5);
    when(monitoringService.getUsedMemoryMB()).thenReturn(512L);
    when(monitoringService.getTotalMemoryMB()).thenReturn(1024L);
    when(monitoringService.getMaxMemoryMB()).thenReturn(2048L);
    when(monitoringService.getConsumptionRate()).thenReturn(10.5);
    when(monitoringService.isConsumptionHealthy()).thenReturn(true);
  }

  private void setupMetricsServiceForOperationalStatus() {
    when(monitoringService.isConsumerConnected()).thenReturn(true);
    when(monitoringService.isPollingActive()).thenReturn(true);
    when(monitoringService.isConsumptionHealthy()).thenReturn(true);
    when(monitoringService.getSuccessRate()).thenReturn(0.95);
    when(monitoringService.getErrorRate()).thenReturn(0.05);
    when(monitoringService.getConsumptionRate()).thenReturn(10.5);
    when(monitoringService.getMemoryUsagePercentage()).thenReturn(75.5);
    when(monitoringService.getLastMessageTimestamp()).thenReturn("2023-01-01T00:02:00");
  }

  @Test
  @DisplayName("Should return comprehensive Kafka metrics")
  void getKafkaMetrics_ShouldReturnComprehensiveMetrics() {
    // Given
    setupMetricsServiceForGetKafkaMetrics();
    
    // When
    ResponseEntity<Map<String, Object>> response = metricsController.getKafkaMetrics();

    // Then
    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());

    Map<String, Object> metrics = response.getBody();

    // Verify message processing metrics
    assertEquals(100L, metrics.get("totalMessagesConsumed"));
    assertEquals(95L, metrics.get("totalMessagesProcessed"));
    assertEquals(5L, metrics.get("totalMessagesFailed"));
    assertEquals(0.95, metrics.get("successRate"));
    assertEquals(0.05, metrics.get("errorRate"));

    // Verify performance metrics
    assertEquals(150.0, metrics.get("averageProcessingTimeMs"));
    assertEquals(50L, metrics.get("minProcessingTimeMs"));
    assertEquals(300L, metrics.get("maxProcessingTimeMs"));

    // Verify activity metrics
    assertEquals("2023-01-01T00:00:00", metrics.get("firstMessageTimestamp"));
    assertEquals("2023-01-01T00:02:00", metrics.get("lastMessageTimestamp"));
    assertEquals("2023-01-01T00:01:50", metrics.get("lastPollTimestamp"));
    assertTrue((Boolean) metrics.get("isPollingActive"));

    // Verify consumer state metrics
    assertTrue((Boolean) metrics.get("isConsumerConnected"));
    assertTrue((Boolean) metrics.get("consumerGroupActive"));

    // Verify memory metrics
    assertEquals(75.5, metrics.get("memoryUsagePercentage"));
    assertEquals(512L, metrics.get("usedMemoryMB"));
    assertEquals(1024L, metrics.get("totalMemoryMB"));
    assertEquals(2048L, metrics.get("maxMemoryMB"));

    // Verify consumption rate metrics
    assertEquals(10.5, metrics.get("consumptionRate"));
    assertTrue((Boolean) metrics.get("isConsumptionHealthy"));

    // Verify metadata
    assertNotNull(metrics.get("timestamp"));
    assertEquals("2.0.0", metrics.get("metricsVersion"));
  }

  @Test
  @DisplayName("Should return Kafka metrics summary as plain text")
  void getKafkaMetricsSummary_ShouldReturnPlainTextSummary() {
    // Given
    when(monitoringService.getMetricsSummary()).thenReturn("Kafka metrics summary");
    
    // When
    ResponseEntity<String> response = metricsController.getKafkaMetricsSummary();

    // Then
    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals("Kafka metrics summary", response.getBody());
    assertEquals("text/plain", response.getHeaders().getContentType().toString());

    verify(monitoringService, times(1)).getMetricsSummary();
  }

  @Test
  @DisplayName("Should reset Kafka metrics successfully")
  void resetKafkaMetrics_ShouldResetMetricsSuccessfully() {
    // Given
    doNothing().when(monitoringService).resetMetrics();

    // When
    ResponseEntity<Map<String, String>> response = metricsController.resetKafkaMetrics();

    // Then
    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());

    Map<String, String> responseBody = response.getBody();
    assertEquals("success", responseBody.get("status"));
    assertEquals("Kafka consumer metrics reset successfully", responseBody.get("message"));
    assertNotNull(responseBody.get("timestamp"));

    verify(monitoringService, times(1)).resetMetrics();
  }

  @Test
  @DisplayName("Should return operational status")
  void getOperationalStatus_ShouldReturnStatus() {
    // Given
    setupMetricsServiceForOperationalStatus();
    
    // When
    ResponseEntity<Map<String, Object>> response = metricsController.getOperationalStatus();

    // Then
    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());

    Map<String, Object> status = response.getBody();

    // Core operational status
    assertTrue((Boolean) status.get("isConsumerConnected"));
    assertTrue((Boolean) status.get("isPollingActive"));
    assertTrue((Boolean) status.get("isConsumptionHealthy"));

    // Key performance indicators
    assertEquals(0.95, status.get("successRate"));
    assertEquals(0.05, status.get("errorRate"));
    assertEquals(10.5, status.get("consumptionRate"));

    // System health indicators
    assertEquals(75.5, status.get("memoryUsagePercentage"));
    assertEquals("2023-01-01T00:02:00", status.get("lastMessageTimestamp"));

    // Operational metadata
    assertNotNull(status.get("timestamp"));
    assertEquals("1.0.0", status.get("statusVersion"));
  }

  @Test
  @DisplayName("Should process WiFi scan message successfully")
  void processWifiScanMessage_ShouldProcessSuccessfully() {
    // Given
    Map<String, Object> wifiScanMessage = Map.of(
        "wifiScanResults", List.of(
            Map.of(
                "macAddress", "aa:bb:cc:dd:ee:ff",
                "signalStrength", -65.4,
                "frequency", 2437,
                "ssid", "OfficeWiFi"
            )
        ),
        "client", "wifi-scan-generator",
        "requestId", "req-1735820400-12345"
    );

    when(compressionService.transform(anyList())).thenReturn(List.of("compressed-message"));
    when(firehoseService.deliverBatch(anyList())).thenReturn(true);

    // When
    ResponseEntity<Map<String, Object>> response = 
        metricsController.processWifiScanMessage(wifiScanMessage);

    // Then
    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());

    Map<String, Object> responseBody = response.getBody();
    assertEquals("success", responseBody.get("status"));
    assertEquals("WiFi scan message delivered successfully to Firehose", responseBody.get("message"));
    assertNotNull(responseBody.get("originalMessageSize"));
    assertNotNull(responseBody.get("compressedMessageSize"));
    assertNotNull(responseBody.get("compressionRatio"));
    assertNotNull(responseBody.get("timestamp"));

    verify(compressionService, times(1)).transform(anyList());
    verify(firehoseService, times(1)).deliverBatch(anyList());
  }

  @Test
  @DisplayName("Should handle compression failure in WiFi scan message processing")
  void processWifiScanMessage_ShouldHandleCompressionFailure() {
    // Given
    Map<String, Object> wifiScanMessage = Map.of("test", "data");
    when(compressionService.transform(anyList())).thenReturn(List.of());

    // When
    ResponseEntity<Map<String, Object>> response = 
        metricsController.processWifiScanMessage(wifiScanMessage);

    // Then
    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    assertNotNull(response.getBody());

    Map<String, Object> responseBody = response.getBody();
    assertEquals("error", responseBody.get("status"));
    assertEquals("Failed to compress and encode message", responseBody.get("message"));
    assertNotNull(responseBody.get("timestamp"));
  }

  @Test
  @DisplayName("Should handle Firehose delivery failure in WiFi scan message processing")
  void processWifiScanMessage_ShouldHandleFirehoseFailure() {
    // Given
    Map<String, Object> wifiScanMessage = Map.of("test", "data");
    when(compressionService.transform(anyList())).thenReturn(List.of("compressed-message"));
    when(firehoseService.deliverBatch(anyList())).thenReturn(false);

    // When
    ResponseEntity<Map<String, Object>> response = 
        metricsController.processWifiScanMessage(wifiScanMessage);

    // Then
    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    assertNotNull(response.getBody());

    Map<String, Object> responseBody = response.getBody();
    assertEquals("error", responseBody.get("status"));
    assertEquals("Failed to deliver WiFi scan message to Firehose", responseBody.get("message"));
    assertNotNull(responseBody.get("originalMessageSize"));
    assertNotNull(responseBody.get("compressedMessageSize"));
    assertNotNull(responseBody.get("compressionRatio"));
    assertNotNull(responseBody.get("timestamp"));
  }

  @Test
  @DisplayName("Should handle exception in WiFi scan message processing")
  void processWifiScanMessage_ShouldHandleException() {
    // Given
    Map<String, Object> wifiScanMessage = Map.of("test", "data");
    when(compressionService.transform(anyList())).thenThrow(new RuntimeException("Service error"));

    // When
    ResponseEntity<Map<String, Object>> response = 
        metricsController.processWifiScanMessage(wifiScanMessage);

    // Then
    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    assertNotNull(response.getBody());

    Map<String, Object> responseBody = response.getBody();
    assertEquals("error", responseBody.get("status"));
    assertTrue(((String) responseBody.get("message")).contains("Internal server error"));
    assertNotNull(responseBody.get("timestamp"));
  }
}
