package com.wifi.positioning.service;

import com.wifi.positioning.dto.WifiPositioningResponse;
import com.wifi.positioning.dto.WifiPositioningRequest;

/**
 * Service interface for WiFi positioning calculations.
 * Provides methods for calculating positions based on WiFi scan results.
 */
public interface PositioningService {
    
    /**
     * Calculate position based on WiFi scan results.
     *
     * @param request The position request containing WiFi scan results
     * @return A positioning response with result status, position data, and metadata
     */
    WifiPositioningResponse calculatePosition(WifiPositioningRequest request);
} 