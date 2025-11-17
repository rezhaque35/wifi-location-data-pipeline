// src/test/java/com/wifi/ap/location/estimate/dto/MaturityThresholdTest.java
package com.wifi.ap.location.estimation.dto;

import com.wifi.ap.location.estimation.state.DataMaturityTier;
import com.wifi.ap.location.estimation.state.MaturityThreshold;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test cases for MaturityThreshold enum functionality.
 */
@DisplayName("MaturityThreshold Tests")
class MaturityThresholdTest {

    @Test
    @DisplayName("Should have correct threshold values")
    void testThresholdValues() {
        assertEquals(20, MaturityThreshold.INSUFFICIENT.getValue());
        assertEquals(50, MaturityThreshold.BOOTSTRAP.getValue());
        assertEquals(100, MaturityThreshold.MATURE.getValue());
    }

    @Test
    @DisplayName("Should have proper ordering of threshold values")
    void testThresholdOrdering() {
        assertTrue(MaturityThreshold.INSUFFICIENT.getValue() < MaturityThreshold.BOOTSTRAP.getValue());
        assertTrue(MaturityThreshold.BOOTSTRAP.getValue() < MaturityThreshold.MATURE.getValue());
    }

    @Test
    @DisplayName("Should integrate correctly with DataMaturityTier")
    void testIntegrationWithDataMaturityTier() {
        // Verify that DataMaturityTier uses the correct threshold values
        assertEquals(MaturityThreshold.INSUFFICIENT.getValue(), DataMaturityTier.getInsufficientThreshold());
        assertEquals(MaturityThreshold.BOOTSTRAP.getValue(), DataMaturityTier.getBootstrapThreshold());
        assertEquals(MaturityThreshold.MATURE.getValue(), DataMaturityTier.getMatureThreshold());
    }

    @Test
    @DisplayName("Should work with tier determination logic")
    void testTierDeterminationWithThresholds() {
        // Test boundary cases with threshold values
        assertEquals(DataMaturityTier.INSUFFICIENT, 
                    DataMaturityTier.fromMeasurementCount(MaturityThreshold.INSUFFICIENT.getValue() - 1));
        assertEquals(DataMaturityTier.BOOTSTRAP, 
                    DataMaturityTier.fromMeasurementCount(MaturityThreshold.INSUFFICIENT.getValue()));
        assertEquals(DataMaturityTier.BOOTSTRAP, 
                    DataMaturityTier.fromMeasurementCount(MaturityThreshold.BOOTSTRAP.getValue() - 1));
        assertEquals(DataMaturityTier.MATURE, 
                    DataMaturityTier.fromMeasurementCount(MaturityThreshold.BOOTSTRAP.getValue()));
        assertEquals(DataMaturityTier.MATURE, 
                    DataMaturityTier.fromMeasurementCount(MaturityThreshold.MATURE.getValue() - 1));
        assertEquals(DataMaturityTier.HIGHLY_MATURE, 
                    DataMaturityTier.fromMeasurementCount(MaturityThreshold.MATURE.getValue()));
    }
}
