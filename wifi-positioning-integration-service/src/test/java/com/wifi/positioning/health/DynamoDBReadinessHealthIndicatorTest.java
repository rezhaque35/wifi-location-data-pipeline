package com.wifi.positioning.health;

import com.wifi.positioning.repository.WifiAccessPointRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;
import software.amazon.awssdk.services.dynamodb.model.InternalServerErrorException;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DynamoDBReadinessHealthIndicator using repository-based approach.
 * Tests follow TDD principles with comprehensive coverage of success and error scenarios.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DynamoDB Readiness Health Indicator Tests")
class DynamoDBReadinessHealthIndicatorTest {

    // Test Constants
    private static final String TEST_TABLE_NAME = "test_wifi_access_points";
    private static final long TEST_ITEM_COUNT = 1000L;
    private static final long HEALTHY_RESPONSE_TIME_MS = 500L;
    private static final long UNHEALTHY_RESPONSE_TIME_MS = 1500L;

    @Mock
    private WifiAccessPointRepository mockRepository;

    private DynamoDBReadinessHealthIndicator healthIndicator;

    @BeforeEach
    void setUp() {
        healthIndicator = new DynamoDBReadinessHealthIndicator(mockRepository);
    }

    @Test
    @DisplayName("should_ReturnUpStatus_When_RepositoryHealthy")
    void should_ReturnUpStatus_When_RepositoryHealthy() throws Exception {
        // Arrange
        WifiAccessPointRepository.HealthCheckResult healthyResult = 
                new WifiAccessPointRepository.HealthCheckResult(
                        true,
                        HEALTHY_RESPONSE_TIME_MS,
                        TEST_TABLE_NAME,
                        TEST_ITEM_COUNT,
                        "Table is accessible and healthy"
                );
        when(mockRepository.validateTableHealth()).thenReturn(healthyResult);

        // Act
        Health health = healthIndicator.health();

        // Assert
        assertEquals(Status.UP, health.getStatus());
        assertEquals("DynamoDB is accessible", health.getDetails().get("status"));
        assertEquals("DynamoDB", health.getDetails().get("database"));
        assertEquals(TEST_TABLE_NAME, health.getDetails().get("tableName"));
        assertEquals(HEALTHY_RESPONSE_TIME_MS, health.getDetails().get("responseTimeMs"));
        assertEquals(TEST_ITEM_COUNT, health.getDetails().get("itemCount"));
        assertNotNull(health.getDetails().get("lastChecked"));
        
        verify(mockRepository).validateTableHealth();
    }

    @Test
    @DisplayName("should_ReturnDownStatus_When_RepositoryUnhealthyDueToLatency")
    void should_ReturnDownStatus_When_RepositoryUnhealthyDueToLatency() throws Exception {
        // Arrange
        WifiAccessPointRepository.HealthCheckResult unhealthyResult = 
                new WifiAccessPointRepository.HealthCheckResult(
                        false,
                        UNHEALTHY_RESPONSE_TIME_MS,
                        TEST_TABLE_NAME,
                        TEST_ITEM_COUNT,
                        "Table response time exceeds threshold"
                );
        when(mockRepository.validateTableHealth()).thenReturn(unhealthyResult);

        // Act
        Health health = healthIndicator.health();

        // Assert
        assertEquals(Status.DOWN, health.getStatus());
        assertEquals("Table response time exceeds threshold", health.getDetails().get("status"));
        assertEquals("DynamoDB", health.getDetails().get("database"));
        assertEquals(TEST_TABLE_NAME, health.getDetails().get("tableName"));
        assertEquals(UNHEALTHY_RESPONSE_TIME_MS, health.getDetails().get("responseTimeMs"));
        assertEquals(TEST_ITEM_COUNT, health.getDetails().get("itemCount"));
        assertNotNull(health.getDetails().get("lastChecked"));
        
        verify(mockRepository).validateTableHealth();
    }

    @Test
    @DisplayName("should_ReturnDownStatus_When_TableNotFound")
    void should_ReturnDownStatus_When_TableNotFound() throws Exception {
        // Arrange
        ResourceNotFoundException exception = ResourceNotFoundException.builder()
                .message("Table not found")
                .build();
        when(mockRepository.validateTableHealth()).thenThrow(exception);

        // Act
        Health health = healthIndicator.health();

        // Assert
        assertEquals(Status.DOWN, health.getStatus());
        assertEquals("DynamoDB table not found", health.getDetails().get("status"));
        assertEquals("DynamoDB", health.getDetails().get("database"));
        assertEquals("Table not found", health.getDetails().get("error"));
        assertNotNull(health.getDetails().get("lastChecked"));
        assertNotNull(health.getDetails().get("responseTimeMs"));
        
        verify(mockRepository).validateTableHealth();
    }

    @Test
    @DisplayName("should_ReturnDownStatus_When_DynamoDbConnectionError")
    void should_ReturnDownStatus_When_DynamoDbConnectionError() throws Exception {
        // Arrange
        InternalServerErrorException exception = InternalServerErrorException.builder()
                .message("Connection error")
                .build();
        when(mockRepository.validateTableHealth()).thenThrow(exception);

        // Act
        Health health = healthIndicator.health();

        // Assert
        assertEquals(Status.DOWN, health.getStatus());
        assertEquals("DynamoDB is not accessible", health.getDetails().get("status"));
        assertEquals("DynamoDB", health.getDetails().get("database"));
        assertEquals("Connection error", health.getDetails().get("error"));
        assertNotNull(health.getDetails().get("lastChecked"));
        assertNotNull(health.getDetails().get("responseTimeMs"));
        
        verify(mockRepository).validateTableHealth();
    }

    @Test
    @DisplayName("should_ReturnOutOfServiceStatus_When_UnexpectedError")
    void should_ReturnOutOfServiceStatus_When_UnexpectedError() throws Exception {
        // Arrange
        RuntimeException exception = new RuntimeException("Unexpected error");
        when(mockRepository.validateTableHealth()).thenThrow(exception);

        // Act
        Health health = healthIndicator.health();

        // Assert
        assertEquals(Status.OUT_OF_SERVICE, health.getStatus());
        assertEquals("Unexpected error during health check", health.getDetails().get("status"));
        assertEquals("DynamoDB", health.getDetails().get("database"));
        assertEquals("Unexpected error", health.getDetails().get("error"));
        assertNotNull(health.getDetails().get("lastChecked"));
        assertNotNull(health.getDetails().get("responseTimeMs"));
        
        verify(mockRepository).validateTableHealth();
    }

    @Test
    @DisplayName("should_IncludeItemCountInHealthyResponse_When_Available")
    void should_IncludeItemCountInHealthyResponse_When_Available() throws Exception {
        // Arrange
        WifiAccessPointRepository.HealthCheckResult healthyResult = 
                new WifiAccessPointRepository.HealthCheckResult(
                        true,
                        HEALTHY_RESPONSE_TIME_MS,
                        TEST_TABLE_NAME,
                        TEST_ITEM_COUNT,
                        "Table is accessible and healthy"
                );
        when(mockRepository.validateTableHealth()).thenReturn(healthyResult);

        // Act
        Health health = healthIndicator.health();

        // Assert
        assertEquals(Status.UP, health.getStatus());
        assertEquals(TEST_ITEM_COUNT, health.getDetails().get("itemCount"));
        assertTrue((Long) health.getDetails().get("itemCount") > 0);
        
        verify(mockRepository).validateTableHealth();
    }

    @Test
    @DisplayName("should_MeasureResponseTime_When_HealthCheckPerformed")
    void should_MeasureResponseTime_When_HealthCheckPerformed() throws Exception {
        // Arrange
        WifiAccessPointRepository.HealthCheckResult healthyResult = 
                new WifiAccessPointRepository.HealthCheckResult(
                        true,
                        HEALTHY_RESPONSE_TIME_MS,
                        TEST_TABLE_NAME,
                        TEST_ITEM_COUNT,
                        "Table is accessible and healthy"
                );
        when(mockRepository.validateTableHealth()).thenReturn(healthyResult);

        // Act
        Health health = healthIndicator.health();

        // Assert
        assertNotNull(health.getDetails().get("responseTimeMs"));
        assertTrue((Long) health.getDetails().get("responseTimeMs") >= 0);
        assertEquals(HEALTHY_RESPONSE_TIME_MS, health.getDetails().get("responseTimeMs"));
        
        verify(mockRepository).validateTableHealth();
    }

    @Test
    @DisplayName("should_IncludeTimestampInResponse_When_HealthCheckPerformed")
    void should_IncludeTimestampInResponse_When_HealthCheckPerformed() throws Exception {
        // Arrange
        WifiAccessPointRepository.HealthCheckResult healthyResult = 
                new WifiAccessPointRepository.HealthCheckResult(
                        true,
                        HEALTHY_RESPONSE_TIME_MS,
                        TEST_TABLE_NAME,
                        TEST_ITEM_COUNT,
                        "Table is accessible and healthy"
                );
        when(mockRepository.validateTableHealth()).thenReturn(healthyResult);

        // Act
        Health health = healthIndicator.health();

        // Assert
        assertNotNull(health.getDetails().get("lastChecked"));
        // Verify the timestamp is recent (within last 5 seconds)
        Object lastChecked = health.getDetails().get("lastChecked");
        assertNotNull(lastChecked);
        
        verify(mockRepository).validateTableHealth();
    }

    @Test
    @DisplayName("should_HandleNullRepository_When_NotConfigured")
    void should_HandleNullRepository_When_NotConfigured() {
        // Arrange
        DynamoDBReadinessHealthIndicator indicatorWithNullRepo = 
                new DynamoDBReadinessHealthIndicator(null);

        // Act
        Health health = indicatorWithNullRepo.health();

        // Assert
        assertEquals(Status.DOWN, health.getStatus());
        assertEquals("Repository not configured", health.getDetails().get("status"));
        assertEquals("DynamoDB", health.getDetails().get("database"));
        assertEquals("WifiAccessPointRepository is not configured", health.getDetails().get("error"));
        assertNotNull(health.getDetails().get("lastChecked"));
    }
} 