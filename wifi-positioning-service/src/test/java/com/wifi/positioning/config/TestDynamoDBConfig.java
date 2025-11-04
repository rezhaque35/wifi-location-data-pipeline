package com.wifi.positioning.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import com.wifi.positioning.repository.CellTowerRepository;
import com.wifi.positioning.repository.WifiAccessPointRepository;
import com.wifi.positioning.repository.impl.InMemoryCellTowerRepository;
import com.wifi.positioning.repository.impl.InMemoryWifiAccessPointRepository;

/**
 * Test configuration that provides in-memory repositories for WiFi access points and cell towers.
 * This eliminates DynamoDB dependency in unit tests while maintaining full functionality.
 * 
 * Provides:
 * - In-memory WiFi access point repository with test data loading methods
 * - In-memory cell tower repository with LRU caching and test data loading
 */
@TestConfiguration
@Profile("test")
public class TestDynamoDBConfig {

  /**
   * Provides in-memory WiFi access point repository for testing.
   * @return InMemoryWifiAccessPointRepository instance
   */
  @Bean
  @Primary
  public WifiAccessPointRepository wifiAccessPointRepository() {
    return new InMemoryWifiAccessPointRepository();
  }

  /**
   * Provides in-memory cell tower repository for testing.
   * Implements full CellTowerRepository interface with LRU caching.
   * @return InMemoryCellTowerRepository instance
   */
  @Bean
  @Primary
  public CellTowerRepository cellTowerRepository() {
    return new InMemoryCellTowerRepository();
  }
}
