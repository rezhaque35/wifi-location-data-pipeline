// com/wifi/positioning/config/IntegrationProperties.java
package com.wifi.positioning.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for the integration service.
 * Maps to the 'integration' section in application.yml.
 */
@Data
@Component
@ConfigurationProperties(prefix = "integration")
public class IntegrationProperties {

    private Positioning positioning = new Positioning();
    private Processing processing = new Processing();
    private Mapping mapping = new Mapping();
    private Logging logging = new Logging();

    @Data
    public static class Positioning {
        private String baseUrl = "http://localhost:8080/wifi-positioning-service";
        private String path = "/api/positioning/calculate";
        private long connectTimeoutMs = 300;
        private long readTimeoutMs = 800;
    }

    @Data
    public static class Processing {
        private String defaultMode = "sync";
        private Async async = new Async();

        @Data
        public static class Async {
            private boolean enabled = false;
            private int queueCapacity = 1000;
            private int workers = 4;
        }
    }

    @Data
    public static class Mapping {
        private boolean dropMissingFrequency = true;
        private int defaultFrequencyMhz = 2412;
    }

    @Data
    public static class Logging {
        private boolean includePayloads = false;
    }
}
