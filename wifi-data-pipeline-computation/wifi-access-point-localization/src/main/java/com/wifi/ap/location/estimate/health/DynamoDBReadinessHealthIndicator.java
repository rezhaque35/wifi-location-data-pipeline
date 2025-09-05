package com.wifi.positioning.health;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import com.wifi.positioning.repository.WifiAccessPointRepository;

import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;

/**
 * Health indicator for DynamoDB readiness using repository abstraction.
 *
 * <p>This health indicator checks if the service is ready to handle requests by verifying that the
 * DynamoDB dependency is accessible and functional through the WifiAccessPointRepository layer.
 *
 * <p>Benefits of Repository-Based Approach: - Separation of concerns: Health checking logic is
 * separated from data access - Testability: Easier to mock and test without actual DynamoDB
 * dependencies - Consistency: Uses the same data access patterns as the rest of the application -
 * Maintainability: Changes to DynamoDB access patterns are centralized in the repository
 *
 * <p>The health check provides information about: - DynamoDB connection status through repository
 * validation - Table accessibility and read permissions - Response time measurements from
 * repository operations - Item count validation for data availability - Last check timestamp for
 * monitoring - Error details when connection fails
 *
 * <p>The readiness check follows Kubernetes readiness probe best practices: - Returns UP when
 * repository reports healthy status and good performance - Returns DOWN when repository reports
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
  private final WifiAccessPointRepository repository;

  /**
   * Constructor for DynamoDB readiness health indicator.
   *
   * @param repository The WiFi access point repository for health validation
   */
  public DynamoDBReadinessHealthIndicator(WifiAccessPointRepository repository) {
    this.repository = repository;
    logger.info("Initialized DynamoDB readiness health indicator with repository-based approach");
  }

  /**
   * Performs the DynamoDB readiness health check using repository abstraction.
   *
   * <p>This method delegates health checking to the repository layer, which provides: 1. Table
   * accessibility validation 2. Response time measurement 3. Item count verification for read
   * permissions 4. Comprehensive error handling
   *
   * <p>The health check process: 1. Validate repository configuration 2. Delegate to
   * repository.validateTableHealth() 3. Evaluate repository health result 4. Return appropriate
   * Spring Boot Actuator Health status
   *
   * <p>Repository Health Evaluation Logic: - UP: Repository reports healthy status (good
   * performance and accessibility) - DOWN: Repository reports unhealthy status (poor performance or
   * accessibility issues) - DOWN: Repository throws ResourceNotFoundException (table not found) -
   * DOWN: Repository throws DynamoDbException (connection/permission issues) - OUT_OF_SERVICE:
   * Repository throws unexpected exceptions
   *
   * <p>Response Time Measurement: The repository layer handles response time measurement using
   * high-precision timing. This health indicator adds minimal overhead by measuring the total time
   * including repository method call overhead.
   *
   * @return Health object with appropriate status and details
   */
  @Override
  public Health health() {
    Instant lastChecked = Instant.now();

    // Validate repository configuration first
    if (repository == null) {
      logger.error("WifiAccessPointRepository is null");
      return Health.down()
          .withDetail(STATUS_KEY, REPOSITORY_NOT_CONFIGURED_MESSAGE)
          .withDetail(DATABASE_KEY, DATABASE_TYPE)
          .withDetail(LAST_CHECKED_KEY, lastChecked)
          .withDetail(ERROR_KEY, "WifiAccessPointRepository is not configured")
          .build();
    }

    // Measure total response time including repository call overhead
    long startTime = System.nanoTime();

    try {
      // Delegate health checking to repository layer
      WifiAccessPointRepository.HealthCheckResult result = repository.validateTableHealth();

      // Calculate total response time (repository time + overhead)
      long endTime = System.nanoTime();
      long totalResponseTimeMs = (endTime - startTime) / NANOS_TO_MILLIS;

      // Evaluate repository health result
      if (result.isHealthy()) {
        logger.debug(
            "Repository health check successful - Table: {}, Response time: {}ms, Item count: {}",
            result.tableName(),
            result.responseTimeMs(),
            result.itemCount());

        return Health.up()
            .withDetail(STATUS_KEY, DYNAMODB_ACCESSIBLE_MESSAGE)
            .withDetail(DATABASE_KEY, DATABASE_TYPE)
            .withDetail(TABLE_NAME_KEY, result.tableName())
            .withDetail(LAST_CHECKED_KEY, lastChecked)
            .withDetail(RESPONSE_TIME_KEY, result.responseTimeMs())
            .withDetail(ITEM_COUNT_KEY, result.itemCount())
            .build();
      } else {
        logger.warn(
            "Repository health check indicates poor performance - Table: {}, Response time: {}ms, Status: {}",
            result.tableName(),
            result.responseTimeMs(),
            result.statusMessage());

        return Health.down()
            .withDetail(STATUS_KEY, result.statusMessage())
            .withDetail(DATABASE_KEY, DATABASE_TYPE)
            .withDetail(TABLE_NAME_KEY, result.tableName())
            .withDetail(LAST_CHECKED_KEY, lastChecked)
            .withDetail(RESPONSE_TIME_KEY, result.responseTimeMs())
            .withDetail(ITEM_COUNT_KEY, result.itemCount())
            .build();
      }

    } catch (ResourceNotFoundException e) {
      // Table not found - specific error case
      long endTime = System.nanoTime();
      long responseTimeMs = (endTime - startTime) / NANOS_TO_MILLIS;

      logger.warn(
          "DynamoDB table not found during repository health check (response time: {}ms)",
          responseTimeMs);

      return Health.down()
          .withDetail(STATUS_KEY, DYNAMODB_TABLE_NOT_FOUND_MESSAGE)
          .withDetail(DATABASE_KEY, DATABASE_TYPE)
          .withDetail(LAST_CHECKED_KEY, lastChecked)
          .withDetail(RESPONSE_TIME_KEY, responseTimeMs)
          .withDetail(ERROR_KEY, e.getMessage())
          .build();

    } catch (DynamoDbException e) {
      // DynamoDB-specific error (connection, authentication, etc.)
      long endTime = System.nanoTime();
      long responseTimeMs = (endTime - startTime) / NANOS_TO_MILLIS;

      logger.error(
          "DynamoDB connection error during repository health check (response time: {}ms)",
          responseTimeMs,
          e);

      return Health.down()
          .withDetail(STATUS_KEY, DYNAMODB_NOT_ACCESSIBLE_MESSAGE)
          .withDetail(DATABASE_KEY, DATABASE_TYPE)
          .withDetail(LAST_CHECKED_KEY, lastChecked)
          .withDetail(RESPONSE_TIME_KEY, responseTimeMs)
          .withDetail(ERROR_KEY, e.getMessage())
          .build();

    } catch (Exception e) {
      // Unexpected error - should be investigated
      long endTime = System.nanoTime();
      long responseTimeMs = (endTime - startTime) / NANOS_TO_MILLIS;

      logger.error(
          "Unexpected error during repository health check (response time: {}ms)",
          responseTimeMs,
          e);

      return Health.outOfService()
          .withDetail(STATUS_KEY, UNEXPECTED_ERROR_MESSAGE)
          .withDetail(DATABASE_KEY, DATABASE_TYPE)
          .withDetail(LAST_CHECKED_KEY, lastChecked)
          .withDetail(RESPONSE_TIME_KEY, responseTimeMs)
          .withDetail(ERROR_KEY, e.getMessage())
          .build();
    }
  }
}
