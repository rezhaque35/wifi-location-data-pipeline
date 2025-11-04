package com.wifi.positioning.health;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import com.wifi.positioning.repository.CellTowerRepository;
import com.wifi.positioning.repository.WifiAccessPointRepository;

import software.amazon.awssdk.services.dynamodb.model.InternalServerErrorException;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;

import java.util.Map;

/**
 * Unit tests for DynamoDBReadinessHealthIndicator using repository-based approach. Tests follow TDD
 * principles with comprehensive coverage of success and error scenarios for both WiFi access point
 * and cell tower repositories.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DynamoDB Readiness Health Indicator Tests")
class DynamoDBReadinessHealthIndicatorTest {

  // Test Constants
  private static final String TEST_TABLE_NAME = "test_wifi_access_points";
  private static final long TEST_ITEM_COUNT = 1000L;
  private static final long HEALTHY_RESPONSE_TIME_MS = 500L;
  private static final long UNHEALTHY_RESPONSE_TIME_MS = 1500L;

  @Mock private WifiAccessPointRepository mockWifiRepository;
  @Mock private CellTowerRepository mockCellTowerRepository;

  private DynamoDBReadinessHealthIndicator healthIndicator;

  @BeforeEach
  void setUp() {
    healthIndicator =
        new DynamoDBReadinessHealthIndicator(mockWifiRepository, mockCellTowerRepository);
  }

  @Test
  @DisplayName("should_ReturnUpStatus_When_BothRepositoriesHealthy")
  void should_ReturnUpStatus_When_BothRepositoriesHealthy() throws Exception {
    // Arrange
    WifiAccessPointRepository.HealthCheckResult wifiHealthyResult =
        new WifiAccessPointRepository.HealthCheckResult(
            true,
            HEALTHY_RESPONSE_TIME_MS,
            TEST_TABLE_NAME,
            TEST_ITEM_COUNT,
            "Table is accessible and healthy");
    CellTowerRepository.HealthCheckResult cellTowerHealthyResult =
        new CellTowerRepository.HealthCheckResult(
            true, HEALTHY_RESPONSE_TIME_MS, "Cell tower repository is accessible and healthy");
    when(mockWifiRepository.validateTableHealth()).thenReturn(wifiHealthyResult);
    when(mockCellTowerRepository.validateHealth()).thenReturn(cellTowerHealthyResult);

    // Act
    Health health = healthIndicator.health();

    // Assert
    assertEquals(Status.UP, health.getStatus());
    assertEquals("DynamoDB is accessible", health.getDetails().get("status"));
    assertEquals("DynamoDB", health.getDetails().get("database"));
    assertNotNull(health.getDetails().get("lastChecked"));
    assertNotNull(health.getDetails().get("responseTimeMs"));

    // Verify WiFi repository details
    @SuppressWarnings("unchecked")
    Map<String, Object> wifiDetails =
        (Map<String, Object>) health.getDetails().get("wifiAccessPointRepository");
    assertNotNull(wifiDetails);
    assertEquals("UP", wifiDetails.get("status"));
    assertEquals(TEST_TABLE_NAME, wifiDetails.get("tableName"));
    assertEquals(HEALTHY_RESPONSE_TIME_MS, wifiDetails.get("responseTimeMs"));
    assertEquals(TEST_ITEM_COUNT, wifiDetails.get("itemCount"));

    // Verify Cell Tower repository details
    @SuppressWarnings("unchecked")
    Map<String, Object> cellTowerDetails =
        (Map<String, Object>) health.getDetails().get("cellTowerRepository");
    assertNotNull(cellTowerDetails);
    assertEquals("UP", cellTowerDetails.get("status"));
    assertEquals(HEALTHY_RESPONSE_TIME_MS, cellTowerDetails.get("responseTimeMs"));

    verify(mockWifiRepository).validateTableHealth();
    verify(mockCellTowerRepository).validateHealth();
  }

  @Test
  @DisplayName("should_ReturnDownStatus_When_WifiRepositoryUnhealthyDueToLatency")
  void should_ReturnDownStatus_When_WifiRepositoryUnhealthyDueToLatency() throws Exception {
    // Arrange
    WifiAccessPointRepository.HealthCheckResult wifiUnhealthyResult =
        new WifiAccessPointRepository.HealthCheckResult(
            false,
            UNHEALTHY_RESPONSE_TIME_MS,
            TEST_TABLE_NAME,
            TEST_ITEM_COUNT,
            "Table response time exceeds threshold");
    CellTowerRepository.HealthCheckResult cellTowerHealthyResult =
        new CellTowerRepository.HealthCheckResult(
            true, HEALTHY_RESPONSE_TIME_MS, "Cell tower repository is accessible and healthy");
    when(mockWifiRepository.validateTableHealth()).thenReturn(wifiUnhealthyResult);
    when(mockCellTowerRepository.validateHealth()).thenReturn(cellTowerHealthyResult);

    // Act
    Health health = healthIndicator.health();

    // Assert
    assertEquals(Status.DOWN, health.getStatus());
    assertEquals("Table response time exceeds threshold", health.getDetails().get("status"));
    assertEquals("DynamoDB", health.getDetails().get("database"));
    assertNotNull(health.getDetails().get("lastChecked"));

    // Verify WiFi repository details show DOWN
    @SuppressWarnings("unchecked")
    Map<String, Object> wifiDetails =
        (Map<String, Object>) health.getDetails().get("wifiAccessPointRepository");
    assertNotNull(wifiDetails);
    assertEquals("DOWN", wifiDetails.get("status"));

    verify(mockWifiRepository).validateTableHealth();
    verify(mockCellTowerRepository).validateHealth();
  }

  @Test
  @DisplayName("should_ReturnDownStatus_When_WifiTableNotFound")
  void should_ReturnDownStatus_When_WifiTableNotFound() throws Exception {
    // Arrange
    ResourceNotFoundException exception =
        ResourceNotFoundException.builder().message("Table not found").build();
    CellTowerRepository.HealthCheckResult cellTowerHealthyResult =
        new CellTowerRepository.HealthCheckResult(
            true, HEALTHY_RESPONSE_TIME_MS, "Cell tower repository is accessible and healthy");
    when(mockWifiRepository.validateTableHealth()).thenThrow(exception);
    when(mockCellTowerRepository.validateHealth()).thenReturn(cellTowerHealthyResult);

    // Act
    Health health = healthIndicator.health();

    // Assert
    assertEquals(Status.DOWN, health.getStatus());
    assertEquals("WiFi table not found", health.getDetails().get("status"));
    assertEquals("DynamoDB", health.getDetails().get("database"));
    assertNotNull(health.getDetails().get("lastChecked"));
    assertNotNull(health.getDetails().get("responseTimeMs"));

    // Verify WiFi repository details show error
    @SuppressWarnings("unchecked")
    Map<String, Object> wifiDetails =
        (Map<String, Object>) health.getDetails().get("wifiAccessPointRepository");
    assertNotNull(wifiDetails);
    assertEquals("DOWN", wifiDetails.get("status"));
    assertEquals("Table not found", wifiDetails.get("error"));

    verify(mockWifiRepository).validateTableHealth();
    verify(mockCellTowerRepository).validateHealth();
  }

  @Test
  @DisplayName("should_ReturnDownStatus_When_WifiDynamoDbConnectionError")
  void should_ReturnDownStatus_When_WifiDynamoDbConnectionError() throws Exception {
    // Arrange
    InternalServerErrorException exception =
        InternalServerErrorException.builder().message("Connection error").build();
    CellTowerRepository.HealthCheckResult cellTowerHealthyResult =
        new CellTowerRepository.HealthCheckResult(
            true, HEALTHY_RESPONSE_TIME_MS, "Cell tower repository is accessible and healthy");
    when(mockWifiRepository.validateTableHealth()).thenThrow(exception);
    when(mockCellTowerRepository.validateHealth()).thenReturn(cellTowerHealthyResult);

    // Act
    Health health = healthIndicator.health();

    // Assert
    assertEquals(Status.DOWN, health.getStatus());
    assertEquals("WiFi table connection error", health.getDetails().get("status"));
    assertEquals("DynamoDB", health.getDetails().get("database"));
    assertNotNull(health.getDetails().get("lastChecked"));
    assertNotNull(health.getDetails().get("responseTimeMs"));

    // Verify WiFi repository details show error
    @SuppressWarnings("unchecked")
    Map<String, Object> wifiDetails =
        (Map<String, Object>) health.getDetails().get("wifiAccessPointRepository");
    assertNotNull(wifiDetails);
    assertEquals("DOWN", wifiDetails.get("status"));
    assertEquals("Connection error", wifiDetails.get("error"));

    verify(mockWifiRepository).validateTableHealth();
    verify(mockCellTowerRepository).validateHealth();
  }

  @Test
  @DisplayName("should_ReturnOutOfServiceStatus_When_WifiUnexpectedError")
  void should_ReturnOutOfServiceStatus_When_WifiUnexpectedError() throws Exception {
    // Arrange
    RuntimeException exception = new RuntimeException("Unexpected error");
    CellTowerRepository.HealthCheckResult cellTowerHealthyResult =
        new CellTowerRepository.HealthCheckResult(
            true, HEALTHY_RESPONSE_TIME_MS, "Cell tower repository is accessible and healthy");
    when(mockWifiRepository.validateTableHealth()).thenThrow(exception);
    when(mockCellTowerRepository.validateHealth()).thenReturn(cellTowerHealthyResult);

    // Act
    Health health = healthIndicator.health();

    // Assert
    assertEquals(Status.OUT_OF_SERVICE, health.getStatus());
    assertEquals("WiFi repository unexpected error", health.getDetails().get("status"));
    assertEquals("DynamoDB", health.getDetails().get("database"));
    assertNotNull(health.getDetails().get("lastChecked"));
    assertNotNull(health.getDetails().get("responseTimeMs"));

    // Verify WiFi repository details show error
    @SuppressWarnings("unchecked")
    Map<String, Object> wifiDetails =
        (Map<String, Object>) health.getDetails().get("wifiAccessPointRepository");
    assertNotNull(wifiDetails);
    assertEquals("DOWN", wifiDetails.get("status"));
    assertEquals("Unexpected error", wifiDetails.get("error"));

    verify(mockWifiRepository).validateTableHealth();
    verify(mockCellTowerRepository).validateHealth();
  }

  @Test
  @DisplayName("should_IncludeItemCountInHealthyResponse_When_Available")
  void should_IncludeItemCountInHealthyResponse_When_Available() throws Exception {
    // Arrange
    WifiAccessPointRepository.HealthCheckResult wifiHealthyResult =
        new WifiAccessPointRepository.HealthCheckResult(
            true,
            HEALTHY_RESPONSE_TIME_MS,
            TEST_TABLE_NAME,
            TEST_ITEM_COUNT,
            "Table is accessible and healthy");
    CellTowerRepository.HealthCheckResult cellTowerHealthyResult =
        new CellTowerRepository.HealthCheckResult(
            true, HEALTHY_RESPONSE_TIME_MS, "Cell tower repository is accessible and healthy");
    when(mockWifiRepository.validateTableHealth()).thenReturn(wifiHealthyResult);
    when(mockCellTowerRepository.validateHealth()).thenReturn(cellTowerHealthyResult);

    // Act
    Health health = healthIndicator.health();

    // Assert
    assertEquals(Status.UP, health.getStatus());
    @SuppressWarnings("unchecked")
    Map<String, Object> wifiDetails =
        (Map<String, Object>) health.getDetails().get("wifiAccessPointRepository");
    assertNotNull(wifiDetails);
    assertEquals(TEST_ITEM_COUNT, wifiDetails.get("itemCount"));
    assertTrue((Long) wifiDetails.get("itemCount") > 0);

    verify(mockWifiRepository).validateTableHealth();
    verify(mockCellTowerRepository).validateHealth();
  }

  @Test
  @DisplayName("should_MeasureResponseTime_When_HealthCheckPerformed")
  void should_MeasureResponseTime_When_HealthCheckPerformed() throws Exception {
    // Arrange
    WifiAccessPointRepository.HealthCheckResult wifiHealthyResult =
        new WifiAccessPointRepository.HealthCheckResult(
            true,
            HEALTHY_RESPONSE_TIME_MS,
            TEST_TABLE_NAME,
            TEST_ITEM_COUNT,
            "Table is accessible and healthy");
    CellTowerRepository.HealthCheckResult cellTowerHealthyResult =
        new CellTowerRepository.HealthCheckResult(
            true, HEALTHY_RESPONSE_TIME_MS, "Cell tower repository is accessible and healthy");
    when(mockWifiRepository.validateTableHealth()).thenReturn(wifiHealthyResult);
    when(mockCellTowerRepository.validateHealth()).thenReturn(cellTowerHealthyResult);

    // Act
    Health health = healthIndicator.health();

    // Assert
    assertNotNull(health.getDetails().get("responseTimeMs"));
    assertTrue((Long) health.getDetails().get("responseTimeMs") >= 0);

    // Verify individual repository response times
    @SuppressWarnings("unchecked")
    Map<String, Object> wifiDetails =
        (Map<String, Object>) health.getDetails().get("wifiAccessPointRepository");
    assertNotNull(wifiDetails);
    assertEquals(HEALTHY_RESPONSE_TIME_MS, wifiDetails.get("responseTimeMs"));

    @SuppressWarnings("unchecked")
    Map<String, Object> cellTowerDetails =
        (Map<String, Object>) health.getDetails().get("cellTowerRepository");
    assertNotNull(cellTowerDetails);
    assertEquals(HEALTHY_RESPONSE_TIME_MS, cellTowerDetails.get("responseTimeMs"));

    verify(mockWifiRepository).validateTableHealth();
    verify(mockCellTowerRepository).validateHealth();
  }

  @Test
  @DisplayName("should_IncludeTimestampInResponse_When_HealthCheckPerformed")
  void should_IncludeTimestampInResponse_When_HealthCheckPerformed() throws Exception {
    // Arrange
    WifiAccessPointRepository.HealthCheckResult wifiHealthyResult =
        new WifiAccessPointRepository.HealthCheckResult(
            true,
            HEALTHY_RESPONSE_TIME_MS,
            TEST_TABLE_NAME,
            TEST_ITEM_COUNT,
            "Table is accessible and healthy");
    CellTowerRepository.HealthCheckResult cellTowerHealthyResult =
        new CellTowerRepository.HealthCheckResult(
            true, HEALTHY_RESPONSE_TIME_MS, "Cell tower repository is accessible and healthy");
    when(mockWifiRepository.validateTableHealth()).thenReturn(wifiHealthyResult);
    when(mockCellTowerRepository.validateHealth()).thenReturn(cellTowerHealthyResult);

    // Act
    Health health = healthIndicator.health();

    // Assert
    assertNotNull(health.getDetails().get("lastChecked"));
    // Verify the timestamp is recent (within last 5 seconds)
    Object lastChecked = health.getDetails().get("lastChecked");
    assertNotNull(lastChecked);

    verify(mockWifiRepository).validateTableHealth();
    verify(mockCellTowerRepository).validateHealth();
  }

  @Test
  @DisplayName("should_HandleNullWifiRepository_When_NotConfigured")
  void should_HandleNullWifiRepository_When_NotConfigured() {
    // Arrange
    DynamoDBReadinessHealthIndicator indicatorWithNullWifiRepo =
        new DynamoDBReadinessHealthIndicator(null, mockCellTowerRepository);

    // Act
    Health health = indicatorWithNullWifiRepo.health();

    // Assert
    assertEquals(Status.DOWN, health.getStatus());
    assertEquals("Repository not configured", health.getDetails().get("status"));
    assertEquals("DynamoDB", health.getDetails().get("database"));
    assertEquals("WifiAccessPointRepository is not configured", health.getDetails().get("error"));
    assertNotNull(health.getDetails().get("lastChecked"));
  }

  @Test
  @DisplayName("should_HandleNullCellTowerRepository_When_NotConfigured")
  void should_HandleNullCellTowerRepository_When_NotConfigured() {
    // Arrange
    DynamoDBReadinessHealthIndicator indicatorWithNullCellTowerRepo =
        new DynamoDBReadinessHealthIndicator(mockWifiRepository, null);

    // Act
    Health health = indicatorWithNullCellTowerRepo.health();

    // Assert
    assertEquals(Status.DOWN, health.getStatus());
    assertEquals("Repository not configured", health.getDetails().get("status"));
    assertEquals("DynamoDB", health.getDetails().get("database"));
    assertEquals("CellTowerRepository is not configured", health.getDetails().get("error"));
    assertNotNull(health.getDetails().get("lastChecked"));
  }

  @Test
  @DisplayName("should_ReturnDownStatus_When_CellTowerRepositoryUnhealthy")
  void should_ReturnDownStatus_When_CellTowerRepositoryUnhealthy() throws Exception {
    // Arrange
    WifiAccessPointRepository.HealthCheckResult wifiHealthyResult =
        new WifiAccessPointRepository.HealthCheckResult(
            true,
            HEALTHY_RESPONSE_TIME_MS,
            TEST_TABLE_NAME,
            TEST_ITEM_COUNT,
            "Table is accessible and healthy");
    CellTowerRepository.HealthCheckResult cellTowerUnhealthyResult =
        new CellTowerRepository.HealthCheckResult(
            false,
            UNHEALTHY_RESPONSE_TIME_MS,
            "Cell tower repository response time (1500 ms) exceeds threshold (1000 ms)");
    when(mockWifiRepository.validateTableHealth()).thenReturn(wifiHealthyResult);
    when(mockCellTowerRepository.validateHealth()).thenReturn(cellTowerUnhealthyResult);

    // Act
    Health health = healthIndicator.health();

    // Assert
    assertEquals(Status.DOWN, health.getStatus());
    assertEquals(
        "Cell tower repository response time (1500 ms) exceeds threshold (1000 ms)",
        health.getDetails().get("status"));
    assertEquals("DynamoDB", health.getDetails().get("database"));

    // Verify Cell Tower repository details show DOWN
    @SuppressWarnings("unchecked")
    Map<String, Object> cellTowerDetails =
        (Map<String, Object>) health.getDetails().get("cellTowerRepository");
    assertNotNull(cellTowerDetails);
    assertEquals("DOWN", cellTowerDetails.get("status"));

    verify(mockWifiRepository).validateTableHealth();
    verify(mockCellTowerRepository).validateHealth();
  }

  @Test
  @DisplayName("should_ReturnDownStatus_When_CellTowerTableNotFound")
  void should_ReturnDownStatus_When_CellTowerTableNotFound() throws Exception {
    // Arrange
    WifiAccessPointRepository.HealthCheckResult wifiHealthyResult =
        new WifiAccessPointRepository.HealthCheckResult(
            true,
            HEALTHY_RESPONSE_TIME_MS,
            TEST_TABLE_NAME,
            TEST_ITEM_COUNT,
            "Table is accessible and healthy");
    ResourceNotFoundException exception =
        ResourceNotFoundException.builder().message("Cell tower table not found").build();
    when(mockWifiRepository.validateTableHealth()).thenReturn(wifiHealthyResult);
    when(mockCellTowerRepository.validateHealth()).thenThrow(exception);

    // Act
    Health health = healthIndicator.health();

    // Assert
    assertEquals(Status.DOWN, health.getStatus());
    assertEquals("Cell tower table not found", health.getDetails().get("status"));
    assertEquals("DynamoDB", health.getDetails().get("database"));

    // Verify Cell Tower repository details show error
    @SuppressWarnings("unchecked")
    Map<String, Object> cellTowerDetails =
        (Map<String, Object>) health.getDetails().get("cellTowerRepository");
    assertNotNull(cellTowerDetails);
    assertEquals("DOWN", cellTowerDetails.get("status"));
    assertEquals("Cell tower table not found", cellTowerDetails.get("error"));

    verify(mockWifiRepository).validateTableHealth();
    verify(mockCellTowerRepository).validateHealth();
  }

  @Test
  @DisplayName("should_ReturnDownStatus_When_BothRepositoriesFail")
  void should_ReturnDownStatus_When_BothRepositoriesFail() throws Exception {
    // Arrange
    ResourceNotFoundException wifiException =
        ResourceNotFoundException.builder().message("WiFi table not found").build();
    ResourceNotFoundException cellTowerException =
        ResourceNotFoundException.builder().message("Cell tower table not found").build();
    when(mockWifiRepository.validateTableHealth()).thenThrow(wifiException);
    when(mockCellTowerRepository.validateHealth()).thenThrow(cellTowerException);

    // Act
    Health health = healthIndicator.health();

    // Assert
    assertEquals(Status.DOWN, health.getStatus());
    String status = (String) health.getDetails().get("status");
    assertTrue(status.contains("WiFi table not found") || status.contains("Cell tower table not found"));
    assertEquals("DynamoDB", health.getDetails().get("database"));

    verify(mockWifiRepository).validateTableHealth();
    verify(mockCellTowerRepository).validateHealth();
  }
}
