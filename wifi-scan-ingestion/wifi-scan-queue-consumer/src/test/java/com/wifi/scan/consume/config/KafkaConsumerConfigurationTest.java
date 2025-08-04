package com.wifi.scan.consume.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for Kafka consumer configuration functionality.
 * Tests consumer factory and SSL properties configuration using TDD approach.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Kafka Consumer Configuration Tests")
class KafkaConsumerConfigurationTest {

    @Mock
    private SslConfiguration sslConfiguration;

    private KafkaConsumerConfiguration kafkaConsumerConfiguration;
    private KafkaProperties kafkaProperties;

    @BeforeEach
    void setUp() {
        kafkaConsumerConfiguration = new KafkaConsumerConfiguration();
        kafkaProperties = createTestKafkaProperties();
    }

    @Test
    @DisplayName("should_CreateConsumerFactory_When_SSLEnabled")
    void should_CreateConsumerFactory_When_SSLEnabled() {
        // Given
        kafkaProperties.getSsl().setEnabled(true);
        Map<String, Object> expectedSslProps = Map.of(
            "security.protocol", "SSL",
            "ssl.keystore.location", "classpath:secrets/kafka.keystore.jks",
            "ssl.keystore.password", "kafka123"
        );
        when(sslConfiguration.buildSslProperties(kafkaProperties)).thenReturn(expectedSslProps);

        // When
        ConsumerFactory<String, String> consumerFactory = kafkaConsumerConfiguration.consumerFactory(kafkaProperties, sslConfiguration);

        // Then
        assertNotNull(consumerFactory, "Consumer factory should not be null");
        Map<String, Object> config = consumerFactory.getConfigurationProperties();
        assertEquals("localhost:9093", config.get(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG));
        assertEquals("test-group", config.get(ConsumerConfig.GROUP_ID_CONFIG));
        assertEquals(StringDeserializer.class, config.get(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG));
        assertEquals(StringDeserializer.class, config.get(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG));
        assertEquals("SSL", config.get("security.protocol"));
    }

    @Test
    @DisplayName("should_CreateConsumerFactory_When_SSLDisabled")
    void should_CreateConsumerFactory_When_SSLDisabled() {
        // Given
        kafkaProperties.getSsl().setEnabled(false);
        kafkaProperties.setBootstrapServers("localhost:9092");

        // When
        ConsumerFactory<String, String> consumerFactory = kafkaConsumerConfiguration.consumerFactory(kafkaProperties, sslConfiguration);

        // Then
        assertNotNull(consumerFactory, "Consumer factory should not be null");
        Map<String, Object> config = consumerFactory.getConfigurationProperties();
        assertEquals("localhost:9092", config.get(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG));
        assertEquals("test-group", config.get(ConsumerConfig.GROUP_ID_CONFIG));
        assertNull(config.get("security.protocol"), "Security protocol should not be set for non-SSL");
    }

    @Test
    @DisplayName("should_CreateKafkaListenerContainerFactory_When_ValidConsumerFactoryProvided")
    void should_CreateKafkaListenerContainerFactory_When_ValidConsumerFactoryProvided() {
        // Given
        ConsumerFactory<String, String> consumerFactory = mock(ConsumerFactory.class);

        // When
        ConcurrentKafkaListenerContainerFactory<String, String> containerFactory = 
            kafkaConsumerConfiguration.kafkaListenerContainerFactory(consumerFactory);

        // Then
        assertNotNull(containerFactory, "Container factory should not be null");
        assertEquals(consumerFactory, containerFactory.getConsumerFactory());
    }

    @Test
    @DisplayName("should_ConfigureConsumerProperties_When_DefaultValuesUsed")
    void should_ConfigureConsumerProperties_When_DefaultValuesUsed() {
        // Given
        KafkaProperties defaultProperties = new KafkaProperties();
        defaultProperties.setBootstrapServers("localhost:9093");
        defaultProperties.getConsumer().setGroupId("default-group");

        // When
        Map<String, Object> consumerProps = kafkaConsumerConfiguration.buildConsumerProperties(defaultProperties);

        // Then
        assertNotNull(consumerProps, "Consumer properties should not be null");
        assertEquals("localhost:9093", consumerProps.get(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG));
        assertEquals("default-group", consumerProps.get(ConsumerConfig.GROUP_ID_CONFIG));
        assertEquals("latest", consumerProps.get(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG));
        assertEquals(false, consumerProps.get(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG)); // Updated to false for manual acknowledgment
        assertEquals(StringDeserializer.class, consumerProps.get(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG));
        assertEquals(StringDeserializer.class, consumerProps.get(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG));
    }

    @Test
    @DisplayName("should_ConfigureConsumerProperties_When_CustomValuesProvided")
    void should_ConfigureConsumerProperties_When_CustomValuesProvided() {
        // Given
        kafkaProperties.getConsumer().setAutoOffsetReset("earliest");
        kafkaProperties.getConsumer().setEnableAutoCommit(false);
        kafkaProperties.getConsumer().setMaxPollRecords(1000);
        kafkaProperties.getConsumer().setSessionTimeout("60000ms");
        kafkaProperties.getConsumer().setHeartbeatInterval("5000ms");
        
        // New optimized polling configurations
        kafkaProperties.getConsumer().setMaxPollInterval("300000ms");
        kafkaProperties.getConsumer().setFetchMinBytes(1);
        kafkaProperties.getConsumer().setFetchMaxWait("500ms");
        kafkaProperties.getConsumer().setRequestTimeout("30000ms");
        kafkaProperties.getConsumer().setRetryBackoff("1000ms");
        kafkaProperties.getConsumer().setConnectionsMaxIdle("540000ms");
        kafkaProperties.getConsumer().setMetadataMaxAge("300000ms");

        // When
        Map<String, Object> consumerProps = kafkaConsumerConfiguration.buildConsumerProperties(kafkaProperties);

        // Then
        assertEquals("earliest", consumerProps.get(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG));
        assertEquals(false, consumerProps.get(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG));
        assertEquals(1000, consumerProps.get(ConsumerConfig.MAX_POLL_RECORDS_CONFIG));
        assertEquals(60000, consumerProps.get(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG));
        assertEquals(5000, consumerProps.get(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG));
        
        // Verify new optimized polling configurations
        assertEquals(300000, consumerProps.get(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG));
        assertEquals(1, consumerProps.get(ConsumerConfig.FETCH_MIN_BYTES_CONFIG));
        assertEquals(500, consumerProps.get(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG));
        assertEquals(30000, consumerProps.get(ConsumerConfig.REQUEST_TIMEOUT_MS_CONFIG));
        assertEquals(1000, consumerProps.get(ConsumerConfig.RETRY_BACKOFF_MS_CONFIG));
        assertEquals(540000, consumerProps.get(ConsumerConfig.CONNECTIONS_MAX_IDLE_MS_CONFIG));
        assertEquals(300000, consumerProps.get(ConsumerConfig.METADATA_MAX_AGE_CONFIG));
        
        // Verify additional resilience configurations
        assertEquals(1000, consumerProps.get(ConsumerConfig.RECONNECT_BACKOFF_MS_CONFIG));
        assertEquals(10000, consumerProps.get(ConsumerConfig.RECONNECT_BACKOFF_MAX_MS_CONFIG));
    }

    @Test
    @DisplayName("should_ConfigureOptimizedPollingProperties_When_DefaultsUsed")
    void should_ConfigureOptimizedPollingProperties_When_DefaultsUsed() {
        // Given: Use default optimized polling configurations
        KafkaProperties defaultProperties = new KafkaProperties();
        defaultProperties.setBootstrapServers("localhost:9093");
        defaultProperties.getConsumer().setGroupId("default-group");

        // When
        Map<String, Object> consumerProps = kafkaConsumerConfiguration.buildConsumerProperties(defaultProperties);

        // Then: Should include default optimized polling configurations
        assertEquals(300000, consumerProps.get(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG));
        assertEquals(1, consumerProps.get(ConsumerConfig.FETCH_MIN_BYTES_CONFIG));
        assertEquals(500, consumerProps.get(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG));
        assertEquals(30000, consumerProps.get(ConsumerConfig.REQUEST_TIMEOUT_MS_CONFIG));
        assertEquals(1000, consumerProps.get(ConsumerConfig.RETRY_BACKOFF_MS_CONFIG));
        assertEquals(540000, consumerProps.get(ConsumerConfig.CONNECTIONS_MAX_IDLE_MS_CONFIG));
        assertEquals(300000, consumerProps.get(ConsumerConfig.METADATA_MAX_AGE_CONFIG));
        
        // Verify resilience configurations are included
        assertEquals(1000, consumerProps.get(ConsumerConfig.RECONNECT_BACKOFF_MS_CONFIG));
        assertEquals(10000, consumerProps.get(ConsumerConfig.RECONNECT_BACKOFF_MAX_MS_CONFIG));
    }

    @Test
    @DisplayName("should_ConfigureContainerProperties_When_OptimizedPollingEnabled")
    void should_ConfigureContainerProperties_When_OptimizedPollingEnabled() {
        // Given
        ConsumerFactory<String, String> consumerFactory = mock(ConsumerFactory.class);

        // When
        ConcurrentKafkaListenerContainerFactory<String, String> containerFactory = 
            kafkaConsumerConfiguration.kafkaListenerContainerFactory(consumerFactory);

        // Then
        assertNotNull(containerFactory, "Container factory should not be null");
        assertEquals(consumerFactory, containerFactory.getConsumerFactory());
        
        // Verify container properties for optimized polling
        assertNotNull(containerFactory.getContainerProperties());
    }
    
    @Test
    @DisplayName("should_LogOptimizedConfiguration_When_PropertiesBuilt")
    void should_LogOptimizedConfiguration_When_PropertiesBuilt() {
        // Given
        kafkaProperties.getConsumer().setMaxPollInterval("300000ms");
        kafkaProperties.getConsumer().setFetchMinBytes(1);
        kafkaProperties.getConsumer().setFetchMaxWait("500ms");

        // When
        Map<String, Object> consumerProps = kafkaConsumerConfiguration.buildConsumerProperties(kafkaProperties);

        // Then: Should include max.poll.interval.ms in debug log
        assertNotNull(consumerProps.get(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG));
        assertEquals(300000, consumerProps.get(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG));
    }

    @Test
    @DisplayName("should_ThrowException_When_BootstrapServersNotProvided")
    void should_ThrowException_When_BootstrapServersNotProvided() {
        // Given
        kafkaProperties.setBootstrapServers(null);

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            kafkaConsumerConfiguration.buildConsumerProperties(kafkaProperties);
        }, "Should throw IllegalArgumentException when bootstrap servers not provided");
    }

    @Test
    @DisplayName("should_ThrowException_When_ConsumerGroupIdNotProvided")
    void should_ThrowException_When_ConsumerGroupIdNotProvided() {
        // Given
        kafkaProperties.getConsumer().setGroupId(null);

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            kafkaConsumerConfiguration.buildConsumerProperties(kafkaProperties);
        }, "Should throw IllegalArgumentException when consumer group ID not provided");
    }

    @Test
    @DisplayName("should_MergeSSLProperties_When_SSLEnabledAndPropertiesProvided")
    void should_MergeSSLProperties_When_SSLEnabledAndPropertiesProvided() {
        // Given
        kafkaProperties.getSsl().setEnabled(true);
        Map<String, Object> baseProps = Map.of(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9093",
            ConsumerConfig.GROUP_ID_CONFIG, "test-group"
        );
        Map<String, Object> sslProps = Map.of(
            "security.protocol", "SSL",
            "ssl.keystore.location", "classpath:secrets/kafka.keystore.jks"
        );
        when(sslConfiguration.buildSslProperties(kafkaProperties)).thenReturn(sslProps);

        // When
        Map<String, Object> mergedProps = kafkaConsumerConfiguration.mergeConsumerProperties(baseProps, kafkaProperties, sslConfiguration);

        // Then
        assertNotNull(mergedProps, "Merged properties should not be null");
        assertEquals("localhost:9093", mergedProps.get(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG));
        assertEquals("test-group", mergedProps.get(ConsumerConfig.GROUP_ID_CONFIG));
        assertEquals("SSL", mergedProps.get("security.protocol"));
        assertEquals("classpath:secrets/kafka.keystore.jks", mergedProps.get("ssl.keystore.location"));
    }

    @Test
    @DisplayName("should_ParseTimeoutValues_When_ValidTimeStringsProvided")
    void should_ParseTimeoutValues_When_ValidTimeStringsProvided() {
        // Given
        String sessionTimeout = "30000ms";
        String heartbeatInterval = "3000ms";
        String autoCommitInterval = "1000ms";

        // When
        int sessionTimeoutMs = kafkaConsumerConfiguration.parseTimeoutMilliseconds(sessionTimeout);
        int heartbeatIntervalMs = kafkaConsumerConfiguration.parseTimeoutMilliseconds(heartbeatInterval);
        int autoCommitIntervalMs = kafkaConsumerConfiguration.parseTimeoutMilliseconds(autoCommitInterval);

        // Then
        assertEquals(30000, sessionTimeoutMs, "Session timeout should be parsed correctly");
        assertEquals(3000, heartbeatIntervalMs, "Heartbeat interval should be parsed correctly");
        assertEquals(1000, autoCommitIntervalMs, "Auto commit interval should be parsed correctly");
    }

    @Test
    @DisplayName("should_ThrowException_When_InvalidTimeoutFormatProvided")
    void should_ThrowException_When_InvalidTimeoutFormatProvided() {
        // Given
        String invalidTimeout = "invalid-time";

        // When & Then
        assertThrows(NumberFormatException.class, () -> {
            kafkaConsumerConfiguration.parseTimeoutMilliseconds(invalidTimeout);
        }, "Should throw NumberFormatException for invalid timeout format");
    }

    private KafkaProperties createTestKafkaProperties() {
        KafkaProperties properties = new KafkaProperties();
        properties.setBootstrapServers("localhost:9093");
        
        KafkaProperties.Consumer consumer = new KafkaProperties.Consumer();
        consumer.setGroupId("test-group");
        consumer.setAutoOffsetReset("latest");
        consumer.setKeyDeserializer("org.apache.kafka.common.serialization.StringDeserializer");
        consumer.setValueDeserializer("org.apache.kafka.common.serialization.StringDeserializer");
        consumer.setEnableAutoCommit(true);
        consumer.setAutoCommitInterval("1000ms");
        consumer.setMaxPollRecords(500);
        consumer.setSessionTimeout("30000ms");
        consumer.setHeartbeatInterval("3000ms");
        
        // Add optimized polling configurations
        consumer.setMaxPollInterval("300000ms");
        consumer.setFetchMinBytes(1);
        consumer.setFetchMaxWait("500ms");
        consumer.setRequestTimeout("30000ms");
        consumer.setRetryBackoff("1000ms");
        consumer.setConnectionsMaxIdle("540000ms");
        consumer.setMetadataMaxAge("300000ms");
        
        properties.setConsumer(consumer);

        KafkaProperties.Topic topic = new KafkaProperties.Topic();
        topic.setName("test-topic");
        properties.setTopic(topic);

        KafkaProperties.Ssl ssl = new KafkaProperties.Ssl();
        ssl.setEnabled(true);
        ssl.setProtocol("SSL");
        KafkaProperties.Keystore keystore = new KafkaProperties.Keystore();
        keystore.setLocation("classpath:secrets/kafka.keystore.jks");
        keystore.setPassword("kafka123");
        ssl.setKeystore(keystore);
        KafkaProperties.Truststore truststore = new KafkaProperties.Truststore();
        truststore.setLocation("classpath:secrets/kafka.truststore.jks");
        truststore.setPassword("kafka123");
        ssl.setTruststore(truststore);
        ssl.setKeyPassword("kafka123");
        properties.setSsl(ssl);

        return properties;
    }
} 