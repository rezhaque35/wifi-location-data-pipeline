package com.wifi.positioning.health;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Health indicator for service liveness.
 * 
 * This health indicator checks if the service is alive and running.
 * It provides information about:
 * - Service status (always UP for liveness)
 * - Service name and version
 * - Startup time (when the service was started)
 * - Current uptime (how long the service has been running)
 * 
 * The liveness check is designed to always return UP unless the service
 * is completely non-functional. This follows Kubernetes liveness probe
 * best practices where liveness should only fail if the service needs
 * to be restarted.
 * 
 * Mathematical Formula for Uptime Calculation:
 * uptime = current_time - startup_time
 * 
 * Where:
 * - current_time: System.currentTimeMillis() at the time of health check
 * - startup_time: System.currentTimeMillis() when the health indicator was created
 * - uptime: Difference in milliseconds, converted to human-readable format
 * 
 * The uptime is displayed in milliseconds if less than 5 seconds,
 * otherwise in seconds for better readability.
 */
@Component("serviceLiveness")
public class ServiceLivenessHealthIndicator implements HealthIndicator {

    // Service Information Constants
    /**
     * The name of the WiFi Positioning Service.
     * Used in health check responses to identify the service.
     */
    private static final String SERVICE_NAME = "WiFi Positioning Service";
    
    /**
     * The current version of the service.
     * This should be updated when the service version changes.
     */
    private static final String SERVICE_VERSION = "1.0.0";

    // Health Status Messages
    /**
     * Status message indicating the service is alive and running.
     * Used when the liveness health check passes.
     */
    private static final String SERVICE_ALIVE_MESSAGE = "Service is alive and running";

    // Health Check Detail Keys
    /**
     * Key for the status detail in health check responses.
     */
    private static final String STATUS_KEY = "status";
    
    /**
     * Key for the service name detail in health check responses.
     */
    private static final String SERVICE_NAME_KEY = "serviceName";
    
    /**
     * Key for the version detail in health check responses.
     */
    private static final String VERSION_KEY = "version";
    
    /**
     * Key for the startup time detail in health check responses.
     */
    private static final String STARTUP_TIME_KEY = "startupTime";
    
    /**
     * Key for the uptime detail in health check responses.
     */
    private static final String UPTIME_KEY = "uptime";

    // Time Constants
    /**
     * Conversion factor from milliseconds to seconds.
     * Used for displaying uptime in human-readable format.
     * 1 second = 1,000 milliseconds
     */
    private static final long MILLIS_TO_SECONDS = 1_000L;
    
    /**
     * Threshold for displaying uptime in seconds vs milliseconds.
     * If uptime is greater than this value (in milliseconds), display in seconds.
     * Set to 5 seconds (5000 milliseconds) for better readability.
     */
    private static final long UPTIME_SECONDS_THRESHOLD = 5_000L;

    /**
     * The time when this health indicator was instantiated (service startup time).
     * This represents when the Spring context was loaded and this bean was created.
     * Used as the baseline for calculating service uptime.
     */
    private final Instant startupTime;

    /**
     * Constructor that captures the service startup time.
     * The startup time is recorded when this health indicator bean is created
     * during Spring application context initialization.
     */
    public ServiceLivenessHealthIndicator() {
        this.startupTime = Instant.now();
    }

    /**
     * Performs the liveness health check.
     * 
     * This method always returns UP status as the liveness check is designed
     * to indicate whether the service is alive and capable of handling requests.
     * If this method can be called, it means the service is alive.
     * 
     * The health check includes:
     * 1. Service status (always "Service is alive and running")
     * 2. Service identification (name and version)
     * 3. Startup timestamp (when the service was started)
     * 4. Current uptime (calculated from startup time to now)
     * 
     * @return Health object with UP status and service details
     */
    @Override
    public Health health() {
        // Calculate current uptime using the mathematical formula:
        // uptime_ms = current_time_ms - startup_time_ms
        long currentTimeMillis = System.currentTimeMillis();
        long startupTimeMillis = startupTime.toEpochMilli();
        long uptimeMillis = currentTimeMillis - startupTimeMillis;
        
        // Format uptime for human readability
        String formattedUptime = formatUptime(uptimeMillis);

        return Health.up()
                .withDetail(STATUS_KEY, SERVICE_ALIVE_MESSAGE)
                .withDetail(SERVICE_NAME_KEY, SERVICE_NAME)
                .withDetail(VERSION_KEY, SERVICE_VERSION)
                .withDetail(STARTUP_TIME_KEY, startupTime)
                .withDetail(UPTIME_KEY, formattedUptime)
                .build();
    }

    /**
     * Formats uptime duration into a human-readable string.
     * 
     * This method applies the following formatting rules:
     * - If uptime < 5000ms (5 seconds): display in milliseconds (e.g., "1234 ms")
     * - If uptime >= 5000ms: display in seconds with decimal precision (e.g., "12.34 seconds")
     * 
     * Mathematical Formula:
     * seconds = uptime_milliseconds / 1000
     * 
     * The threshold of 5 seconds is chosen to provide meaningful precision:
     * - For short uptimes, millisecond precision is useful for debugging startup issues
     * - For longer uptimes, second precision is more readable and practical
     * 
     * @param uptimeMillis The uptime in milliseconds
     * @return Formatted uptime string
     */
    private String formatUptime(long uptimeMillis) {
        if (uptimeMillis < UPTIME_SECONDS_THRESHOLD) {
            return uptimeMillis + " ms";
        } else {
            // Convert milliseconds to seconds with decimal precision
            // Formula: seconds = milliseconds / 1000
            double uptimeSeconds = (double) uptimeMillis / MILLIS_TO_SECONDS;
            return String.format("%.2f seconds", uptimeSeconds);
        }
    }
} 