package com.wifi.positioning.repository;

import java.util.Set;

/**
 * Exception thrown when DynamoDB throttles batch operations due to throughput limits.
 *
 * <p>This exception is thrown after exhausting all retry attempts when DynamoDB returns unprocessed
 * keys due to provisioned or on-demand throughput limits being exceeded.
 *
 * <p>The exception contains: - Set of MAC addresses that could not be processed - Number of retry
 * attempts made before failure
 *
 * <p>This exception does NOT include keys that simply don't exist in the database. It only tracks
 * keys that DynamoDB explicitly returned as "unprocessed" due to throttling.
 */
public class DynamoDBThrottlingException extends RuntimeException {

  private final Set<String> unprocessedKeys;
  private final int attemptedRetries;

  /**
   * Constructs a new DynamoDBThrottlingException.
   *
   * @param message Error message describing the throttling situation
   * @param unprocessedKeys Set of MAC addresses that remain unprocessed due to throttling
   * @param attemptedRetries Number of retry attempts made before giving up
   */
  public DynamoDBThrottlingException(
      String message, Set<String> unprocessedKeys, int attemptedRetries) {
    super(message);
    this.unprocessedKeys = Set.copyOf(unprocessedKeys);
    this.attemptedRetries = attemptedRetries;
  }

  /**
   * Returns the set of MAC addresses that could not be processed due to DynamoDB throttling.
   *
   * @return Immutable set of unprocessed MAC addresses
   */
  public Set<String> getUnprocessedKeys() {
    return unprocessedKeys;
  }

  /**
   * Returns the number of retry attempts made before throwing this exception.
   *
   * @return Number of retry attempts
   */
  public int getAttemptedRetries() {
    return attemptedRetries;
  }
}

