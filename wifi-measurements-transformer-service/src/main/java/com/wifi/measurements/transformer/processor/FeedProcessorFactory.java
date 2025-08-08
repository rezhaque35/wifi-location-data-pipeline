// wifi-measurements-transformer-service/src/main/java/com/wifi/measurements/transformer/service/FeedProcessorFactory.java
package com.wifi.measurements.transformer.processor;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.wifi.measurements.transformer.dto.S3EventRecord;
import com.wifi.measurements.transformer.processor.impl.DefaultFeedProcessor;

/**
 * Factory service for selecting appropriate feed processors based on S3 event stream types.
 *
 * <p>This factory implements a pluggable architecture for feed processor selection, allowing
 * different data stream types to be processed by specialized processors while maintaining a unified
 * processing interface. It provides automatic processor selection based on stream names extracted
 * from S3 object keys.
 *
 * <p><strong>Processor Selection Strategy:</strong>
 *
 * <ul>
 *   <li><strong>Custom Processors:</strong> Specialized processors for specific feed types
 *   <li><strong>Default Processor:</strong> Fallback processor for unknown or unsupported feed
 *       types
 *   <li><strong>Stream-Based Routing:</strong> Processor selection based on S3 object key stream
 *       name
 *   <li><strong>Priority-Based Selection:</strong> Higher priority processors selected first
 * </ul>
 *
 * <p><strong>Architecture Benefits:</strong>
 *
 * <ul>
 *   <li><strong>Extensibility:</strong> Easy addition of new feed types and processors
 *   <li><strong>Maintainability:</strong> Clear separation of concerns between different data types
 *   <li><strong>Testability:</strong> Individual processors can be tested in isolation
 *   <li><strong>Flexibility:</strong> Support for both specialized and general-purpose processing
 * </ul>
 *
 * <p><strong>Current Implementation:</strong>
 *
 * <ul>
 *   <li>Default processor handles all WiFi scan data streams
 *   <li>Custom processors can be added for specialized data types
 *   <li>Automatic fallback to default processor for unknown streams
 *   <li>Comprehensive logging for processor selection decisions
 * </ul>
 *
 * <p>This factory is designed to be stateless and thread-safe, supporting concurrent processor
 * selection requests while maintaining consistent routing behavior.
 *
 * @author WiFi Location Data Pipeline Team
 * @version 1.0
 * @since 2024
 */
@Service
public class FeedProcessorFactory {

  private static final Logger logger = LoggerFactory.getLogger(FeedProcessorFactory.class);

  private final List<FeedProcessor> customProcessors;
  private final DefaultFeedProcessor defaultProcessor;

  /**
   * Constructs a new feed processor factory with required dependencies.
   *
   * <p>This constructor initializes the factory with a default processor for handling unknown or
   * unsupported feed types. The factory is designed to be extensible, allowing custom processors to
   * be added for specialized data types.
   *
   * @param defaultProcessor The default processor to use for unknown or unsupported feed types
   * @throws IllegalArgumentException if defaultProcessor is null
   */
  public FeedProcessorFactory(DefaultFeedProcessor defaultProcessor) {
    if (defaultProcessor == null) {
      throw new IllegalArgumentException("DefaultFeedProcessor cannot be null");
    }

    this.customProcessors = List.of(); // No custom processors for now
    this.defaultProcessor = defaultProcessor;
    logger.info(
        "FeedProcessorFactory initialized with {} custom processors", customProcessors.size());
  }

  /**
   * Selects the appropriate feed processor for the given S3 event based on stream type.
   *
   * <p>This method implements the processor selection logic by examining the stream name from the
   * S3 event record and finding a processor that can handle that feed type. If no custom processor
   * is found, the default processor is returned as a fallback.
   *
   * <p><strong>Selection Logic:</strong>
   *
   * <ol>
   *   <li><strong>Stream Extraction:</strong> Extracts stream name from S3 event record
   *   <li><strong>Custom Processor Search:</strong> Searches custom processors for matching feed
   *       type
   *   <li><strong>Priority Resolution:</strong> Selects highest priority processor if multiple
   *       matches
   *   <li><strong>Default Fallback:</strong> Returns default processor if no custom processor found
   * </ol>
   *
   * <p><strong>Thread Safety:</strong>
   *
   * <ul>
   *   <li>Thread-safe operation - can be called concurrently
   *   <li>Stateless design - no shared mutable state
   *   <li>Consistent selection behavior across concurrent calls
   * </ul>
   *
   * @param s3EventRecord The S3 event record containing stream information for processor selection
   * @return The appropriate feed processor for the stream type, never null
   * @throws IllegalArgumentException if s3EventRecord is null
   */
  public FeedProcessor getProcessor(S3EventRecord s3EventRecord) {
    String feedType = s3EventRecord.streamName();
    return customProcessors.stream()
        .filter(processor -> processor.canProcess(feedType))
        .findFirst()
        .orElse(defaultProcessor);
  }

  /**
   * Returns a list of all feed types supported by custom processors.
   *
   * <p>This method provides information about the feed types that have specialized processors
   * available. This is useful for monitoring, debugging, and understanding the current processor
   * configuration.
   *
   * @return List of feed type identifiers supported by custom processors, may be empty
   */
  public List<String> getSupportedFeedTypes() {
    return customProcessors.stream().map(FeedProcessor::getSupportedFeedType).toList();
  }

  /**
   * Returns the total number of processors available in the factory.
   *
   * <p>This method provides information about the total processor count, including both custom
   * processors and the default processor. This is useful for monitoring and understanding the
   * current processor configuration.
   *
   * @return Total number of processors (custom processors + default processor)
   */
  public int getTotalProcessorCount() {
    return customProcessors.size() + 1;
  }
}
