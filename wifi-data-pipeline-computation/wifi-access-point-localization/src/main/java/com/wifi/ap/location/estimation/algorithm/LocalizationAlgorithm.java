// wifi-measurements-transformer-service/src/main/java/com/wifi/measurements/transformer/service/FeedProcessor.java
package com.wifi.ap.location.estimation.algorithm;

import com.wifi.ap.location.APLocation;
import com.wifi.ap.location.measurements.WifiMeasurements;

/**
 * Interface for processing different types of data feeds from S3 events.
 *
 * <p>This interface provides a pluggable architecture for handling different data feed types (e.g.,
 * WiFi scans, GPS data, sensor data) with feed-specific processing logic while maintaining a common
 * processing contract.
 *
 * <p>Implementations should be stateless and thread-safe to support concurrent processing.
 */
public interface LocalizationAlgorithm {
  APLocation estimateLocation(WifiMeasurements locationMeasurements);
  APLocation estimateLocation(WifiMeasurements locationMeasurements, APLocation currentEstimation);

}
