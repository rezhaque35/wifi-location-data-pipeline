package com.wifi.scan.consume.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for Kafka SSL/TLS settings and consumer configuration.
 * 
 * This class provides type-safe configuration mapping for Kafka-related properties
 * from application.yml files. It encapsulates all Kafka configuration including
 * consumer settings, topic configuration, and SSL/TLS security settings.
 * 
 * Key Functionality:
 * - Maps kafka.* properties from YAML to strongly-typed Java objects
 * - Provides default values for common Kafka configurations
 * - Supports SSL/TLS certificate management
 * - Configures optimized consumer polling behavior
 * 
 * Configuration Hierarchy:
 * 1. Bootstrap servers and basic connectivity
 * 2. Consumer group and polling configuration
 * 3. Topic-specific settings
 * 4. SSL/TLS security configuration
 * 
 * @see KafkaConsumerConfiguration
 * @see SslConfiguration
 */
@Data
@Component
@ConfigurationProperties(prefix = "kafka")
public class KafkaProperties {

    /** Kafka bootstrap servers for initial connection */
    private String bootstrapServers;
    
    /** Consumer-specific configuration settings */
    private Consumer consumer = new Consumer();
    
    /** Topic-specific configuration settings */
    private Topic topic = new Topic();
    
    /** SSL/TLS security configuration */
    private Ssl ssl = new Ssl();

    /**
     * Consumer configuration properties for Kafka consumer behavior.
     * 
     * This class defines all consumer-related settings including group management,
     * polling behavior, offset management, and performance optimizations.
     * 
     * Key Features:
     * - Consumer group management and rebalancing
     * - Optimized polling intervals for high-throughput scenarios
     * - Offset commit and reset strategies
     * - Connection and timeout management
     * 
     * High-Level Configuration Steps:
     * 1. Set consumer group ID for partition assignment
     * 2. Configure offset reset behavior (earliest/latest)
     * 3. Set polling intervals for optimal throughput
     * 4. Configure session and heartbeat timeouts
     * 5. Set fetch behavior for immediate or batched responses
     */
    @Data
    public static class Consumer {
        /** Consumer group ID for partition assignment and rebalancing */
        private String groupId;
        
        /** Offset reset strategy when no committed offset exists */
        private String autoOffsetReset = "latest";
        
        /** Deserializer class for message keys */
        private String keyDeserializer = "org.apache.kafka.common.serialization.StringDeserializer";
        
        /** Deserializer class for message values */
        private String valueDeserializer = "org.apache.kafka.common.serialization.StringDeserializer";
        
        /** Whether to enable automatic offset commits */
        private boolean enableAutoCommit = true;
        
        /** Interval between automatic offset commits */
        private String autoCommitInterval = "1000ms";
        
        /** Maximum number of records to poll in a single batch */
        private int maxPollRecords = 500;
        
        /** Session timeout for consumer group rebalancing */
        private String sessionTimeout = "30000ms";
        
        /** Heartbeat interval for consumer group coordination */
        private String heartbeatInterval = "3000ms";
        
        // Optimized polling configurations for continuous operation
        
        /** Maximum time between poll calls before consumer is considered dead */
        private String maxPollInterval = "300000ms";        // 5 minutes
        
        /** Minimum bytes to fetch from broker (1 byte for immediate response) */
        private int fetchMinBytes = 1;                       // 1 byte for immediate response
        
        /** Maximum wait time for fetch operation */
        private String fetchMaxWait = "500ms";              // Maximum wait time for fetch
        
        /** Request timeout for network operations */
        private String requestTimeout = "30000ms";          // Request timeout
        
        /** Backoff time between retry attempts */
        private String retryBackoff = "1000ms";             // Retry backoff time
        
        /** Maximum time connections can remain idle */
        private String connectionsMaxIdle = "540000ms";     // 9 minutes
        
        /** Maximum age of metadata before refresh */
        private String metadataMaxAge = "300000ms";         // 5 minutes
    }

    /**
     * Topic configuration properties for Kafka topic management.
     * 
     * This class defines topic-specific settings including partition count,
     * replication factor, and topic naming conventions.
     * 
     * Key Features:
     * - Topic naming and identification
     * - Partition count for parallel processing
     * - Replication factor for fault tolerance
     * 
     * High-Level Configuration Steps:
     * 1. Define topic name for message consumption
     * 2. Set partition count based on parallelism requirements
     * 3. Configure replication factor for availability
     */
    @Data
    public static class Topic {
        /** Name of the Kafka topic to consume from */
        private String name;
        
        /** Number of partitions for parallel processing */
        private int partitions = 3;
        
        /** Replication factor for fault tolerance */
        private int replicationFactor = 1;
    }

    /**
     * SSL/TLS security configuration for encrypted Kafka communication.
     * 
     * This class manages SSL/TLS settings including protocol selection,
     * certificate management, and security parameters.
     * 
     * Key Features:
     * - SSL/TLS protocol selection
     * - Client certificate (keystore) configuration
     * - Server certificate (truststore) configuration
     * - Certificate password management
     * 
     * High-Level Configuration Steps:
     * 1. Enable/disable SSL/TLS encryption
     * 2. Select appropriate SSL protocol version
     * 3. Configure client certificate (keystore)
     * 4. Configure server certificate (truststore)
     * 5. Set certificate passwords for security
     */
    @Data
    public static class Ssl {
        /** Whether SSL/TLS encryption is enabled */
        private boolean enabled = false;
        
        /** SSL/TLS protocol version to use */
        private String protocol = "SSL";
        
        /** Client certificate (keystore) configuration */
        private Keystore keystore = new Keystore();
        
        /** Server certificate (truststore) configuration */
        private Truststore truststore = new Truststore();
        
        /** Password for the private key in the keystore */
        private String keyPassword;
    }

    /**
     * Keystore configuration for client certificate management.
     * 
     * This class manages the client-side certificate store used for
     * SSL/TLS client authentication.
     * 
     * Key Features:
     * - Keystore file location and type
     * - Keystore password management
     * - Support for different keystore formats (JKS, PKCS12)
     * 
     * High-Level Configuration Steps:
     * 1. Specify keystore file location
     * 2. Set keystore password for access
     * 3. Choose appropriate keystore type
     */
    @Data
    public static class Keystore {
        /** File path to the keystore containing client certificates */
        private String location;
        
        /** Password to access the keystore */
        private String password;
        
        /** Type of keystore (JKS, PKCS12, etc.) */
        private String type = "JKS";
    }

    /**
     * Truststore configuration for server certificate validation.
     * 
     * This class manages the server-side certificate store used for
     * SSL/TLS server certificate validation.
     * 
     * Key Features:
     * - Truststore file location and type
     * - Truststore password management
     * - Support for different truststore formats (JKS, PKCS12)
     * 
     * High-Level Configuration Steps:
     * 1. Specify truststore file location
     * 2. Set truststore password for access
     * 3. Choose appropriate truststore type
     */
    @Data
    public static class Truststore {
        /** File path to the truststore containing server certificates */
        private String location;
        
        /** Password to access the truststore */
        private String password;
        
        /** Type of truststore (JKS, PKCS12, etc.) */
        private String type = "JKS";
    }
} 