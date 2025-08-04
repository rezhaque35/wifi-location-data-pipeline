package com.wifi.positioning.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Represents a WiFi scan result with signal information.
 * This record serves as both a Data Transfer Object (DTO) for API communication 
 * and as a domain model for internal processing.
 * 
 * Contains information about a WiFi access point detected during scanning:
 * - MAC address (required): Unique identifier in XX:XX:XX:XX:XX:XX format
 * - Signal strength (required): Measured in dBm, typically -30 to -100
 * - Frequency (required): Operating frequency in MHz
 * - SSID (optional): Network name
 * - Link speed (optional): Connection speed in Mbps
 * - Channel width (optional): WiFi channel width in MHz
 */
public record WifiScanResult(
    @NotBlank(message = "MAC address is required")
    @Pattern(regexp = "([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})", message = "Invalid MAC address format")
    String macAddress,
    
    @Min(value = -100, message = "Signal strength must be at least -100 dBm")
    @Max(value = 0, message = "Signal strength must be at most 0 dBm")
    Double signalStrength,
    
    @Min(value = 2400, message = "Frequency must be at least 2400 MHz")
    @Max(value = 6000, message = "Frequency must be at most 6000 MHz")
    Integer frequency,
    
    String ssid,
    
    @Min(value = 0, message = "Link speed must be non-negative")
    Integer linkSpeed,
    
    @Min(value = 20, message = "Channel width must be at least 20 MHz")
    @Max(value = 160, message = "Channel width must be at most 160 MHz")
    Integer channelWidth
) {
    /**
     * Compact constructor for validation.
     * Ensures required fields are not null even if validation annotations are bypassed.
     */
    public WifiScanResult {
        if (macAddress == null || macAddress.isBlank()) {
            throw new IllegalArgumentException("MAC address cannot be null or blank");
        }
        if (signalStrength == null) {
            throw new IllegalArgumentException("Signal strength cannot be null");
        }
        if (frequency == null) {
            throw new IllegalArgumentException("Frequency cannot be null");
        }
    }
    
    /**
     * Creates a new WifiScanResult with only the required fields.
     * This factory method is provided for compatibility with existing code.
     * 
     * @param macAddress MAC address of the access point
     * @param signalStrength Signal strength in dBm
     * @param frequency Frequency in MHz
     * @param ssid SSID (network name)
     * @return A new WifiScanResult without link speed and channel width
     */
    public static WifiScanResult of(String macAddress, double signalStrength, int frequency, String ssid) {
        return new WifiScanResult(macAddress, signalStrength, frequency, ssid, null, null);
    }
    
    /**
     * Creates a new WifiScanResult with primitive values automatically boxed.
     * This factory method is provided for compatibility with existing code.
     * 
     * @param macAddress MAC address of the access point
     * @param signalStrength Signal strength in dBm (as primitive double)
     * @param frequency Frequency in MHz (as primitive int)
     * @param ssid SSID (network name)
     * @param linkSpeed Link speed in Mbps (as primitive int)
     * @param channelWidth Channel width in MHz (as primitive int)
     * @return A new WifiScanResult with all fields
     */
    public static WifiScanResult of(String macAddress, double signalStrength, int frequency, String ssid, 
                                    int linkSpeed, int channelWidth) {
        return new WifiScanResult(macAddress, signalStrength, frequency, ssid, linkSpeed, channelWidth);
    }
} 