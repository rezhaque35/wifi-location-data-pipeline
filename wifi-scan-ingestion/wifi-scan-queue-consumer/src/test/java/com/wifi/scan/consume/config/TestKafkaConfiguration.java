package com.wifi.scan.consume.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Profile;

/**
 * Test configuration for Kafka integration tests.
 * Provides test-specific configuration and utilities for embedded Kafka testing.
 */
@TestConfiguration
@Profile("test")
public class TestKafkaConfiguration {
    // This class enables test-specific configurations
    // The embedded Kafka configuration is handled by @EmbeddedKafka annotation
} 