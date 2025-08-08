package com.wifi.positioning.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import com.wifi.positioning.repository.WifiAccessPointRepository;
import com.wifi.positioning.repository.impl.InMemoryWifiAccessPointRepository;

/**
 * Test configuration that provides an in-memory repository to avoid DynamoDB dependency in unit
 * tests.
 */
@TestConfiguration
@Profile("test")
public class TestDynamoDBConfig {

  @Bean
  @Primary
  public WifiAccessPointRepository wifiAccessPointRepository() {
    return new InMemoryWifiAccessPointRepository();
  }
}
