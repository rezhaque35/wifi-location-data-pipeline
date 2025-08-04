package com.wifi.positioning.config;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@EnableAutoConfiguration
@ComponentScan(basePackages = "com.wifi.positioning")
@Import(TestDynamoDBConfig.class)
public class TestApplicationConfig {
    // Empty configuration class that enables component scanning and auto-configuration
} 