package com.wifi.ap.location;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
@ConfigurationPropertiesScan("com.wifi.ap.location.estimation.config.properties")
public class WifiAccessPointLocalizationApplication {

  /**
   * Main entry point for the WiFi Measurements Transformer Service.
   *
   * <p>This method initializes the Spring Boot application context and starts the service. The
   * application will begin processing SQS messages and transforming WiFi scan data according to the
   * configured business rules.
   *
   * <p>The service runs continuously until terminated, processing messages asynchronously to
   * maintain high throughput and responsiveness.
   *
   * @param args Command line arguments passed to the application
   * @throws Exception if the application fails to start
   */
  public static void main(String[] args) {
    SpringApplication.run(WifiAccessPointLocalizationApplication.class, args);
  }
}
