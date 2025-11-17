// src/test/java/com/wifi/ap/location/estimate/dto/DataMaturityTierTest.java
package com.wifi.ap.location.estimation.dto;

import com.wifi.ap.location.estimation.state.DataMaturityTier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test cases for DataMaturityTier enum functionality.
 */
@DisplayName("DataMaturityTier Tests")
class DataMaturityTierTest {

    @Test
    @DisplayName("Should correctly determine tier from measurement count with hardcoded thresholds")
    void testFromMeasurementCount() {
        // INSUFFICIENT: N < 20
        assertEquals(DataMaturityTier.INSUFFICIENT, DataMaturityTier.fromMeasurementCount(5));
        assertEquals(DataMaturityTier.INSUFFICIENT, DataMaturityTier.fromMeasurementCount(19));
        
        // BOOTSTRAP: 20 ≤ N < 50
        assertEquals(DataMaturityTier.BOOTSTRAP, DataMaturityTier.fromMeasurementCount(20));
        assertEquals(DataMaturityTier.BOOTSTRAP, DataMaturityTier.fromMeasurementCount(35));
        assertEquals(DataMaturityTier.BOOTSTRAP, DataMaturityTier.fromMeasurementCount(49));
        
        // MATURE: 50 ≤ N < 100
        assertEquals(DataMaturityTier.MATURE, DataMaturityTier.fromMeasurementCount(50));
        assertEquals(DataMaturityTier.MATURE, DataMaturityTier.fromMeasurementCount(75));
        assertEquals(DataMaturityTier.MATURE, DataMaturityTier.fromMeasurementCount(99));
        
        // HIGHLY_MATURE: N ≥ 100
        assertEquals(DataMaturityTier.HIGHLY_MATURE, DataMaturityTier.fromMeasurementCount(100));
        assertEquals(DataMaturityTier.HIGHLY_MATURE, DataMaturityTier.fromMeasurementCount(500));
    }

    @Test
    @DisplayName("Should correctly check if measurement count is in range")
    void testIsInRange() {
        // Test INSUFFICIENT
        assertTrue(DataMaturityTier.INSUFFICIENT.isInRange(10));
        assertFalse(DataMaturityTier.INSUFFICIENT.isInRange(20));
        
        // Test BOOTSTRAP
        assertTrue(DataMaturityTier.BOOTSTRAP.isInRange(20));
        assertTrue(DataMaturityTier.BOOTSTRAP.isInRange(35));
        assertFalse(DataMaturityTier.BOOTSTRAP.isInRange(19));
        assertFalse(DataMaturityTier.BOOTSTRAP.isInRange(50));
        
        // Test MATURE
        assertTrue(DataMaturityTier.MATURE.isInRange(50));
        assertTrue(DataMaturityTier.MATURE.isInRange(75));
        assertFalse(DataMaturityTier.MATURE.isInRange(49));
        assertFalse(DataMaturityTier.MATURE.isInRange(100));
        
        // Test HIGHLY_MATURE
        assertTrue(DataMaturityTier.HIGHLY_MATURE.isInRange(100));
        assertTrue(DataMaturityTier.HIGHLY_MATURE.isInRange(1000));
        assertFalse(DataMaturityTier.HIGHLY_MATURE.isInRange(99));
    }

    @Test
    @DisplayName("Should return correct min and max counts for each tier")
    void testGetMinMaxCounts() {
        // INSUFFICIENT: [0, 20)
        assertEquals(0, DataMaturityTier.INSUFFICIENT.getMinCount());
        assertEquals(20, DataMaturityTier.INSUFFICIENT.getMaxCount());
        
        // BOOTSTRAP: [20, 50)
        assertEquals(20, DataMaturityTier.BOOTSTRAP.getMinCount());
        assertEquals(50, DataMaturityTier.BOOTSTRAP.getMaxCount());
        
        // MATURE: [50, 100)
        assertEquals(50, DataMaturityTier.MATURE.getMinCount());
        assertEquals(100, DataMaturityTier.MATURE.getMaxCount());
        
        // HIGHLY_MATURE: [100, ∞)
        assertEquals(100, DataMaturityTier.HIGHLY_MATURE.getMinCount());
        assertEquals(Integer.MAX_VALUE, DataMaturityTier.HIGHLY_MATURE.getMaxCount());
    }

    @Test
    @DisplayName("Should provide meaningful descriptions for each tier")
    void testGetDescription() {
        assertNotNull(DataMaturityTier.INSUFFICIENT.getDescription());
        assertNotNull(DataMaturityTier.BOOTSTRAP.getDescription());
        assertNotNull(DataMaturityTier.MATURE.getDescription());
        assertNotNull(DataMaturityTier.HIGHLY_MATURE.getDescription());
        
        assertTrue(DataMaturityTier.INSUFFICIENT.getDescription().toLowerCase().contains("few"));
        assertTrue(DataMaturityTier.BOOTSTRAP.getDescription().toLowerCase().contains("initial"));
        assertTrue(DataMaturityTier.MATURE.getDescription().toLowerCase().contains("stable"));
        assertTrue(DataMaturityTier.HIGHLY_MATURE.getDescription().toLowerCase().contains("extensive"));
    }

    @Test
    @DisplayName("Should have correct hardcoded threshold values")
    void testThresholdConstants() {
        assertEquals(20, DataMaturityTier.getInsufficientThreshold());
        assertEquals(50, DataMaturityTier.getBootstrapThreshold());
        assertEquals(100, DataMaturityTier.getMatureThreshold());
    }

    @Test
    @DisplayName("Should handle edge cases correctly")
    void testEdgeCases() {
        // Test zero
        assertEquals(DataMaturityTier.INSUFFICIENT, DataMaturityTier.fromMeasurementCount(0));
        
        // Test exactly at thresholds
        assertEquals(DataMaturityTier.BOOTSTRAP, DataMaturityTier.fromMeasurementCount(20));
        assertEquals(DataMaturityTier.MATURE, DataMaturityTier.fromMeasurementCount(50));
        assertEquals(DataMaturityTier.HIGHLY_MATURE, DataMaturityTier.fromMeasurementCount(100));
        
        // Test one below thresholds
        assertEquals(DataMaturityTier.INSUFFICIENT, DataMaturityTier.fromMeasurementCount(19));
        assertEquals(DataMaturityTier.BOOTSTRAP, DataMaturityTier.fromMeasurementCount(49));
        assertEquals(DataMaturityTier.MATURE, DataMaturityTier.fromMeasurementCount(99));
        
        // Test very large numbers
        assertEquals(DataMaturityTier.HIGHLY_MATURE, DataMaturityTier.fromMeasurementCount(Integer.MAX_VALUE));
    }
}