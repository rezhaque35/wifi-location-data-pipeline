package com.wifi.positioning.health;

import java.time.Instant;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import com.wifi.positioning.repository.CellTowerRepository;
import com.wifi.positioning.repository.WifiAccessPointRepository;

import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;

/**
 * Health indicator for DynamoDB readiness using repository abstraction.
 *
 * <p>This health indicator checks if the service is ready to handle requests by verifying that both
 * DynamoDB dependencies (WiFi access points and cell towers) are accessible and functional through
 * their respective repository layers.
 *
 * <p>Benefits of Repository-Based Approach: - Separation of concerns: Health checking logic is
 * separated from data access - Testability: Easier to mock and test without actual DynamoDB
 * dependencies - Consistency: Uses the same data access patterns as the rest of the application -
 * Maintainability: Changes to DynamoDB access patterns are centralized in the repository
 *
 * <p>The health check provides information about: - DynamoDB connection status through repository
 * validation for both tables - Table accessibility and read permissions for WiFi and cell tower
 * tables - Response time measurements from repository operations - Item count validation for data
 * availability (WiFi table) - Last check timestamp for monitoring - Error details when connection
 * fails
 *
 * <p>The readiness check follows Kubernetes readiness probe best practices: - Returns UP when both
 * repositories report healthy status and good performance - Returns DOWN when any repository reports
 * unhealthy status or performance issues - Returns OUT_OF_SERVICE for unexpected errors or
 * configuration issues
 *
 * <p>Mathematical Formula for Response Time Measurement: The response time is measured by the
 * repository layer using: response_time_ms = (end_time_nanos - start_time_nanos) / 1,000,000
 *
 * <p>Where: - start_time_nanos: System.nanoTime() before repository operation - end_time_nanos:
 * System.nanoTime() after repository operation - response_time_ms: Difference converted to
 * milliseconds for readability
 *
 * <p>The response time helps monitor DynamoDB performance and can indicate network latency,
 * DynamoDB throttling, or other performance issues.
 */
@Component("dynamoDBReadiness")
public class DynamoDBReadinessHealthIndicator implements HealthIndicator {

  private static final Logger logger =
      LoggerFactory.getLogger(DynamoDBReadinessHealthIndicator.class);

  // Database Information Constants
  /**
   * Database type identifier for DynamoDB. Used in health check responses to identify the database
   * type.
   */
  private static final String DATABASE_TYPE = "DynamoDB";

  // Health Status Messages
  /**
   * Status message indicating DynamoDB is accessible. Used when the DynamoDB readiness health check
   * passes.
   */
  private static final String DYNAMODB_ACCESSIBLE_MESSAGE = "DynamoDB is accessible";

  /**
   * Status message indicating DynamoDB is not accessible. Used when the DynamoDB readiness health
   * check fails due to connection issues.
   */
  private static final String DYNAMODB_NOT_ACCESSIBLE_MESSAGE = "DynamoDB is not accessible";

  /**
   * Status message indicating DynamoDB table was not found. Used when the DynamoDB readiness health
   * check fails due to missing table.
   */
  private static final String DYNAMODB_TABLE_NOT_FOUND_MESSAGE = "DynamoDB table not found";

  /**
   * Status message indicating repository is not configured. Used when the DynamoDB readiness health
   * check fails due to missing repository configuration.
   */
  private static final String REPOSITORY_NOT_CONFIGURED_MESSAGE = "Repository not configured";

  /**
   * Status message indicating an unexpected error occurred during health check. Used when the
   * health check encounters an unexpected exception.
   */
  private static final String UNEXPECTED_ERROR_MESSAGE = "Unexpected error during health check";

  // Health Check Detail Keys
  /** Key for the status detail in health check responses. */
  private static final String STATUS_KEY = "status";

  /** Key for the database type detail in health check responses. */
  private static final String DATABASE_KEY = "database";

  /** Key for the table name detail in health check responses. */
  private static final String TABLE_NAME_KEY = "tableName";

  /** Key for the last checked timestamp detail in health check responses. */
  private static final String LAST_CHECKED_KEY = "lastChecked";

  /** Key for the response time detail in health check responses. */
  private static final String RESPONSE_TIME_KEY = "responseTimeMs";

  /** Key for the item count detail in health check responses. */
  private static final String ITEM_COUNT_KEY = "itemCount";

  /** Key for the error detail in health check responses. */
  private static final String ERROR_KEY = "error";

  /** Key for WiFi access point repository health details. */
  private static final String WIFI_REPOSITORY_KEY = "wifiAccessPointRepository";

  /** Key for cell tower repository health details. */
  private static final String CELL_TOWER_REPOSITORY_KEY = "cellTowerRepository";

  // Time Constants
  /**
   * Conversion factor from nanoseconds to milliseconds. Used for converting System.nanoTime()
   * measurements to milliseconds. 1 millisecond = 1,000,000 nanoseconds
   */
  private static final long NANOS_TO_MILLIS = 1_000_000L;

  /**
   * Repository for accessing WiFi access point data and performing health checks. This repository
   * abstracts the DynamoDB access and provides health validation methods.
   */
  private final WifiAccessPointRepository wifiAccessPointRepository;

  /**
   * Repository for accessing cell tower data and performing health checks. This repository
   * abstracts the DynamoDB access and provides health validation methods.
   */
  private final CellTowerRepository cellTowerRepository;

  /**
   * Constructor for DynamoDB readiness health indicator.
   *
   * @param wifiAccessPointRepository The WiFi access point repository for health validation
   * @param cellTowerRepository The cell tower repository for health validation
   */
  public DynamoDBReadinessHealthIndicator(
      WifiAccessPointRepository wifiAccessPointRepository,
      CellTowerRepository cellTowerRepository) {
    this.wifiAccessPointRepository = wifiAccessPointRepository;
    this.cellTowerRepository = cellTowerRepository;
    logger.info(
        "Initialized DynamoDB readiness health indicator with WiFi and cell tower repository-based approach");
  }

  /**
   * Performs the DynamoDB readiness health check using repository abstraction for both WiFi access
   * points and cell towers.
   *
   * <p>This method delegates health checking to both repository layers, which provide: 1. Table
   * accessibility validation for both tables 2. Response time measurement for both repositories 3.
   * Item count verification for read permissions (WiFi table) 4. Comprehensive error handling
   *
   * <p>The health check process: 1. Validate both repository configurations 2. Delegate to both
   * repository.validateTableHealth() and repository.validateHealth() 3. Evaluate both repository
   * health results 4. Return appropriate Spring Boot Actuator Health status based on combined
   * results
   *
   * <p>Repository Health Evaluation Logic: - UP: Both repositories report healthy status (good
   * performance and accessibility) - DOWN: Any repository reports unhealthy status (poor performance
   * or accessibility issues) - DOWN: Any repository throws ResourceNotFoundException (table not
   * found) - DOWN: Any repository throws DynamoDbException (connection/permission issues) -
   * OUT_OF_SERVICE: Any repository throws unexpected exceptions
   *
   * <p>Response Time Measurement: The repository layers handle response time measurement using
   * high-precision timing. This health indicator adds minimal overhead by measuring the total time
   * including repository method call overhead.
   *
   * @return Health object with appropriate status and details from both repositories
   */
  @Override
  public Health health() {
    Instant lastChecked = Instant.now();

    // Validate repository configurations first
    if (wifiAccessPointRepository == null) {
      logger.error("WifiAccessPointRepository is null");
      return Health.down()
          .withDetail(STATUS_KEY, REPOSITORY_NOT_CONFIGURED_MESSAGE)
          .withDetail(DATABASE_KEY, DATABASE_TYPE)
          .withDetail(LAST_CHECKED_KEY, lastChecked)
          .withDetail(ERROR_KEY, "WifiAccessPointRepository is not configured")
          .build();
    }

    if (cellTowerRepository == null) {
      logger.error("CellTowerRepository is null");
      return Health.down()
          .withDetail(STATUS_KEY, REPOSITORY_NOT_CONFIGURED_MESSAGE)
          .withDetail(DATABASE_KEY, DATABASE_TYPE)
          .withDetail(LAST_CHECKED_KEY, lastChecked)
          .withDetail(ERROR_KEY, "CellTowerRepository is not configured")
          .build();
    }

    // Measure total response time including repository call overhead
    long startTime = System.nanoTime();

    // Check WiFi access point repository
    WifiAccessPointRepository.HealthCheckResult wifiResult = null;
    Exception wifiException = null;
    try {
      wifiResult = wifiAccessPointRepository.validateTableHealth();
      logger.debug(
          "WiFi repository health check completed - Table: {}, Response time: {}ms, Item count: {}, Healthy: {}",
          wifiResult.tableName(),
          wifiResult.responseTimeMs(),
          wifiResult.itemCount(),
          wifiResult.isHealthy());
    } catch (ResourceNotFoundException e) {
      logger.warn("WiFi access point table not found during health check", e);
      wifiException = e;
    } catch (DynamoDbException e) {
      logger.error("DynamoDB connection error during WiFi repository health check", e);
      wifiException = e;
    } catch (Exception e) {
      logger.error("Unexpected error during WiFi repository health check", e);
      wifiException = e;
    }

    // Check cell tower repository
    CellTowerRepository.HealthCheckResult cellTowerResult = null;
    Exception cellTowerException = null;
    try {
      cellTowerResult = cellTowerRepository.validateHealth();
      logger.debug(
          "Cell tower repository health check completed - Response time: {}ms, Healthy: {}, Status: {}",
          cellTowerResult.responseTimeMs(),
          cellTowerResult.isHealthy(),
          cellTowerResult.statusMessage());
    } catch (ResourceNotFoundException e) {
      logger.warn("Cell tower table not found during health check", e);
      cellTowerException = e;
    } catch (DynamoDbException e) {
      logger.error("DynamoDB connection error during cell tower repository health check", e);
      cellTowerException = e;
    } catch (Exception e) {
      logger.error("Unexpected error during cell tower repository health check", e);
      cellTowerException = e;
    }

    // Calculate total response time
    long endTime = System.nanoTime();
    long totalResponseTimeMs = (endTime - startTime) / NANOS_TO_MILLIS;

    // Build health details with both repository results
    Health.Builder healthBuilder = Health.up();
    boolean allHealthy = true;
    String combinedStatus = DYNAMODB_ACCESSIBLE_MESSAGE;

    // Add WiFi repository details
    if (wifiException != null) {
      allHealthy = false;
      if (wifiException instanceof ResourceNotFoundException) {
        combinedStatus = "WiFi table not found";
        healthBuilder = Health.down();
      } else if (wifiException instanceof DynamoDbException) {
        combinedStatus = "WiFi table connection error";
        healthBuilder = Health.down();
      } else {
        combinedStatus = "WiFi repository unexpected error";
        healthBuilder = Health.outOfService();
      }
      healthBuilder.withDetail(
          WIFI_REPOSITORY_KEY,
          Map.of(
              "status", "DOWN",
              "error", wifiException.getMessage()));
    } else if (wifiResult != null) {
      if (!wifiResult.isHealthy()) {
        allHealthy = false;
        combinedStatus = wifiResult.statusMessage();
        healthBuilder = Health.down();
      }
      healthBuilder.withDetail(
          WIFI_REPOSITORY_KEY,
          Map.of(
              "status", wifiResult.isHealthy() ? "UP" : "DOWN",
              "tableName", wifiResult.tableName(),
              "responseTimeMs", wifiResult.responseTimeMs(),
              "itemCount", wifiResult.itemCount(),
              "statusMessage", wifiResult.statusMessage()));
    }

    // Add cell tower repository details
    if (cellTowerException != null) {
      allHealthy = false;
      boolean wifiHadIssue = wifiException != null || (wifiResult != null && !wifiResult.isHealthy());
      String cellTowerError;
      if (cellTowerException instanceof ResourceNotFoundException) {
        cellTowerError = "Cell tower table not found";
        healthBuilder = Health.down();
      } else if (cellTowerException instanceof DynamoDbException) {
        cellTowerError = "Cell tower table connection error";
        healthBuilder = Health.down();
      } else {
        cellTowerError = "Cell tower repository unexpected error";
        healthBuilder = Health.outOfService();
      }
      combinedStatus = wifiHadIssue ? combinedStatus + "; " + cellTowerError : cellTowerError;
      healthBuilder.withDetail(
          CELL_TOWER_REPOSITORY_KEY,
          Map.of(
              "status", "DOWN",
              "error", cellTowerException.getMessage()));
    } else if (cellTowerResult != null) {
      boolean wifiHadIssue = wifiException != null || (wifiResult != null && !wifiResult.isHealthy());
      if (!cellTowerResult.isHealthy()) {
        allHealthy = false;
        combinedStatus = wifiHadIssue ? combinedStatus + "; " + cellTowerResult.statusMessage() : cellTowerResult.statusMessage();
        healthBuilder = Health.down();
      }
      healthBuilder.withDetail(
          CELL_TOWER_REPOSITORY_KEY,
          Map.of(
              "status", cellTowerResult.isHealthy() ? "UP" : "DOWN",
              "responseTimeMs", cellTowerResult.responseTimeMs(),
              "statusMessage", cellTowerResult.statusMessage()));
    }

    // Build final health response
    return healthBuilder
        .withDetail(STATUS_KEY, allHealthy ? DYNAMODB_ACCESSIBLE_MESSAGE : combinedStatus)
        .withDetail(DATABASE_KEY, DATABASE_TYPE)
        .withDetail(LAST_CHECKED_KEY, lastChecked)
        .withDetail(RESPONSE_TIME_KEY, totalResponseTimeMs)
        .build();
  }
}
