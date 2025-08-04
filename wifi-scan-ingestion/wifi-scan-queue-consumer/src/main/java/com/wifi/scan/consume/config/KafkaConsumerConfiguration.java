package com.wifi.scan.consume.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka consumer configuration for SSL/TLS enabled Kafka connectivity.
 * 
 * This configuration class sets up the Kafka consumer infrastructure with advanced
 * features for production-ready message consumption. It provides both standard and
 * batch-optimized consumer configurations with comprehensive SSL/TLS support.
 * 
 * Key Functionality:
 * - Configures consumer factory with SSL/TLS support
 * - Sets up optimized listener container factories
 * - Implements batch processing capabilities
 * - Provides manual acknowledgment for message processing control
 * - Configures optimized polling parameters for high throughput
 * - Handles SSL certificate validation and trust store management
 * 
 * High-Level Steps:
 * 1. Build base consumer properties from configuration
 * 2. Merge SSL/TLS properties when SSL is enabled
 * 3. Create consumer factory with merged properties
 * 4. Configure listener container factory with optimized settings
 * 5. Set up batch processing container for high-volume consumption
 * 6. Configure error handling and recovery mechanisms
 * 
 * The configuration supports both standard and batch message processing modes,
 * with the batch mode optimized for processing large volumes of WiFi scan data
 * efficiently while maintaining message ordering and reliability.
 * 
 * @see com.wifi.scan.consume.config.KafkaProperties
 * @see com.wifi.scan.consume.config.SslConfiguration
 * @see com.wifi.scan.consume.listener.WifiScanBatchMessageListener
 */
@Slf4j
@Configuration
public class KafkaConsumerConfiguration {

    @Autowired
    private KafkaProperties kafkaProperties;

    @Autowired
    private SslConfiguration sslConfiguration;

    /**
     * Creates Kafka consumer factory with SSL/TLS configuration.
     * 
     * This bean method creates the primary consumer factory used by the application.
     * It automatically integrates SSL/TLS configuration when enabled and provides
     * the foundation for all Kafka consumer operations.
     * 
     * @return Configured ConsumerFactory for String key-value pairs
     */
    @Bean
    public ConsumerFactory<String, String> consumerFactory() {
        return consumerFactory(kafkaProperties, sslConfiguration);
    }

    /**
     * Creates Kafka consumer factory with provided properties and SSL configuration.
     * 
     * This method builds a complete consumer factory by combining base Kafka properties
     * with SSL/TLS configuration when enabled. It ensures proper serialization and
     * security settings for production message consumption.
     * 
     * High-Level Steps:
     * 1. Build base consumer properties from Kafka configuration
     * 2. Check if SSL is enabled in configuration
     * 3. Merge SSL properties with base properties if SSL is enabled
     * 4. Create and return DefaultKafkaConsumerFactory with merged properties
     * 5. Log configuration status for monitoring
     * 
     * @param kafkaProperties Kafka connection and consumer properties
     * @param sslConfiguration SSL/TLS configuration for secure connections
     * @return Configured ConsumerFactory ready for message consumption
     */
    public ConsumerFactory<String, String> consumerFactory(KafkaProperties kafkaProperties, SslConfiguration sslConfiguration) {
        log.debug("Creating Kafka consumer factory");
        
        // Build base consumer properties from configuration
        Map<String, Object> consumerProps = buildConsumerProperties(kafkaProperties);
        
        // Merge SSL properties if enabled
        if (kafkaProperties.getSsl().isEnabled()) {
            // Combine base properties with SSL configuration for secure connections
            Map<String, Object> mergedProps = mergeConsumerProperties(consumerProps, kafkaProperties, sslConfiguration);
            log.info("Kafka consumer factory created with SSL enabled");
            return new DefaultKafkaConsumerFactory<>(mergedProps);
        } else {
            // Use base properties for non-SSL connections
            log.info("Kafka consumer factory created without SSL");
            return new DefaultKafkaConsumerFactory<>(consumerProps);
        }
    }

    /**
     * Creates Kafka listener container factory.
     * 
     * This bean method creates the primary listener container factory used for
     * standard message processing. It configures the container with optimized
     * settings for reliable message consumption.
     * 
     * @return Configured ConcurrentKafkaListenerContainerFactory
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory() {
        return kafkaListenerContainerFactory(consumerFactory());
    }

    /**
     * Creates Kafka listener container factory with provided consumer factory.
     * 
     * This method configures a listener container factory with production-optimized
     * settings for high-performance message consumption. It sets up manual acknowledgment,
     * optimized polling parameters, and error handling mechanisms.
     * 
     * High-Level Steps:
     * 1. Create ConcurrentKafkaListenerContainerFactory instance
     * 2. Set consumer factory for message deserialization
     * 3. Configure container properties for manual acknowledgment
     * 4. Set optimized polling timeout and idle intervals
     * 5. Configure error handling and recovery settings
     * 6. Enable synchronous commits for reliability
     * 
     * @param consumerFactory The consumer factory to use for message processing
     * @return Configured listener container factory with optimized settings
     */
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory) {
        
        log.debug("Creating Kafka listener container factory");
        
        // Create the container factory with the provided consumer factory
        ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        
        // Configure container properties for optimized polling
        ContainerProperties containerProps = factory.getContainerProperties();
        
        // Manual acknowledgment for precise control over message processing
        containerProps.setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        
        // Synchronous commits for reliability and consistency
        containerProps.setSyncCommits(true);
        
        // Optimized polling configurations for responsive message consumption
        containerProps.setPollTimeout(1000);  // 1 second poll timeout for responsive polling
        containerProps.setIdleEventInterval(30000L);  // 30 seconds - emit idle events for monitoring
        containerProps.setIdleBetweenPolls(0);  // No delay between polls for continuous operation
        
        // Error handling and recovery - don't fail if topic doesn't exist initially
        containerProps.setMissingTopicsFatal(false);
        
        log.info("Kafka listener container factory created with optimized polling configuration");
        return factory;
    }

    /**
     * Builds base consumer properties from Kafka configuration.
     * 
     * This method creates the foundational properties map for Kafka consumer
     * configuration. It validates required properties and sets up essential
     * consumer settings for reliable message consumption.
     * 
     * High-Level Steps:
     * 1. Validate bootstrap servers configuration
     * 2. Create properties map with required consumer settings
     * 3. Configure deserializers for String key-value pairs
     * 4. Set consumer group ID and auto-offset reset behavior
     * 5. Configure session timeout and heartbeat intervals
     * 6. Set fetch and max poll settings for optimal performance
     * 
     * @param kafkaProperties Kafka configuration properties
     * @return Map of consumer properties ready for factory creation
     * @throws IllegalArgumentException if required properties are missing
     */
    public Map<String, Object> buildConsumerProperties(KafkaProperties kafkaProperties) {
        log.debug("Building Kafka consumer properties");
        
        // Validate bootstrap servers configuration
        if (kafkaProperties.getBootstrapServers() == null || kafkaProperties.getBootstrapServers().isEmpty()) {
            throw new IllegalArgumentException("Kafka bootstrap servers must be provided");
        }
        
        if (kafkaProperties.getConsumer().getGroupId() == null || kafkaProperties.getConsumer().getGroupId().isEmpty()) {
            throw new IllegalArgumentException("Kafka consumer group ID must be provided");
        }
        
        Map<String, Object> props = new HashMap<>();
        
        // Basic consumer configuration
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, kafkaProperties.getConsumer().getGroupId());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, kafkaProperties.getConsumer().getAutoOffsetReset());
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false); // Disabled for manual acknowledgment
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        
        // Timeout configurations
        if (kafkaProperties.getConsumer().getSessionTimeout() != null) {
            props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, parseTimeoutMilliseconds(kafkaProperties.getConsumer().getSessionTimeout()));
        }
        
        if (kafkaProperties.getConsumer().getHeartbeatInterval() != null) {
            props.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, parseTimeoutMilliseconds(kafkaProperties.getConsumer().getHeartbeatInterval()));
        }
        
        if (kafkaProperties.getConsumer().getAutoCommitInterval() != null) {
            props.put(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, parseTimeoutMilliseconds(kafkaProperties.getConsumer().getAutoCommitInterval()));
        }
        
        // Performance configurations
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, kafkaProperties.getConsumer().getMaxPollRecords());
        
        // Optimized polling configurations for continuous operation
        if (kafkaProperties.getConsumer().getMaxPollInterval() != null) {
            props.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, parseTimeoutMilliseconds(kafkaProperties.getConsumer().getMaxPollInterval()));
        }
        
        props.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, kafkaProperties.getConsumer().getFetchMinBytes());
        
        if (kafkaProperties.getConsumer().getFetchMaxWait() != null) {
            props.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, parseTimeoutMilliseconds(kafkaProperties.getConsumer().getFetchMaxWait()));
        }
        
        if (kafkaProperties.getConsumer().getRequestTimeout() != null) {
            props.put(ConsumerConfig.REQUEST_TIMEOUT_MS_CONFIG, parseTimeoutMilliseconds(kafkaProperties.getConsumer().getRequestTimeout()));
        }
        
        if (kafkaProperties.getConsumer().getRetryBackoff() != null) {
            props.put(ConsumerConfig.RETRY_BACKOFF_MS_CONFIG, parseTimeoutMilliseconds(kafkaProperties.getConsumer().getRetryBackoff()));
        }
        
        if (kafkaProperties.getConsumer().getConnectionsMaxIdle() != null) {
            props.put(ConsumerConfig.CONNECTIONS_MAX_IDLE_MS_CONFIG, parseTimeoutMilliseconds(kafkaProperties.getConsumer().getConnectionsMaxIdle()));
        }
        
        if (kafkaProperties.getConsumer().getMetadataMaxAge() != null) {
            props.put(ConsumerConfig.METADATA_MAX_AGE_CONFIG, parseTimeoutMilliseconds(kafkaProperties.getConsumer().getMetadataMaxAge()));
        }
        
        // Additional resilience configurations
        props.put(ConsumerConfig.RECONNECT_BACKOFF_MS_CONFIG, 1000);
        props.put(ConsumerConfig.RECONNECT_BACKOFF_MAX_MS_CONFIG, 10000);
        
        log.debug("Consumer properties built: bootstrap.servers={}, group.id={}, auto.offset.reset={}, max.poll.interval.ms={}", 
                kafkaProperties.getBootstrapServers(), 
                kafkaProperties.getConsumer().getGroupId(),
                kafkaProperties.getConsumer().getAutoOffsetReset(),
                props.get(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG));
        
        return props;
    }

    /**
     * Merges consumer properties with SSL properties.
     */
    public Map<String, Object> mergeConsumerProperties(Map<String, Object> baseProps, 
            KafkaProperties kafkaProperties, SslConfiguration sslConfiguration) {
        
        log.debug("Merging consumer properties with SSL configuration");
        
        Map<String, Object> mergedProps = new HashMap<>(baseProps);
        
        if (kafkaProperties.getSsl().isEnabled()) {
            Map<String, Object> sslProps = sslConfiguration.buildSslProperties(kafkaProperties);
            mergedProps.putAll(sslProps);
            
            log.debug("SSL properties merged: security.protocol={}", sslProps.get("security.protocol"));
        }
        
        return mergedProps;
    }

    /**
     * Parses timeout string (e.g., "30000ms") to milliseconds integer.
     */
    public int parseTimeoutMilliseconds(String timeoutString) {
        if (timeoutString == null || timeoutString.isEmpty()) {
            throw new IllegalArgumentException("Timeout string cannot be null or empty");
        }
        
        try {
            if (timeoutString.endsWith("ms")) {
                return Integer.parseInt(timeoutString.substring(0, timeoutString.length() - 2));
            } else if (timeoutString.endsWith("s")) {
                return Integer.parseInt(timeoutString.substring(0, timeoutString.length() - 1)) * 1000;
            } else {
                // Assume milliseconds if no unit specified
                return Integer.parseInt(timeoutString);
            }
        } catch (NumberFormatException e) {
            throw new NumberFormatException("Invalid timeout format: " + timeoutString + ". Expected format: '30000ms' or '30s'");
        }
    }

    /**
     * Creates batch Kafka listener container factory for processing message batches.
     * Configured for 150-message batches with manual acknowledgment.
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> batchKafkaListenerContainerFactory() {
        log.debug("Creating batch Kafka listener container factory");
        
        ConcurrentKafkaListenerContainerFactory<String, String> factory = 
                new ConcurrentKafkaListenerContainerFactory<>();
        
        // Use the same consumer factory with batch configuration
        factory.setConsumerFactory(consumerFactory());
        
        // Configure for batch listening
        factory.setBatchListener(true);
        
        // Configure container properties for batch processing
        ContainerProperties containerProps = factory.getContainerProperties();
        
        // Manual acknowledgment for batch processing (commit only after successful Firehose delivery)
        containerProps.setAckMode(ContainerProperties.AckMode.MANUAL);
        
        // Set poll timeout to match our 1.5 second poll interval
        containerProps.setPollTimeout(1500);
        
        // Configure shutdown timeout
        containerProps.setShutdownTimeout(30000);
        
        // Sync commits for reliability
        containerProps.setSyncCommits(true);
        
        // Log container configuration
        log.info("Batch Kafka listener container factory configured: batchListener=true, ackMode=MANUAL, pollTimeout=1500ms");
        
        return factory;
    }
} 