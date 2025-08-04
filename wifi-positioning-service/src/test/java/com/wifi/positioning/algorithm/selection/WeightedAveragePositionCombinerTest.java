package com.wifi.positioning.algorithm.selection;

import com.wifi.positioning.dto.Position;
import com.wifi.positioning.algorithm.selection.factor.GeometricQualityFactor;
import com.wifi.positioning.algorithm.impl.WeightedAveragePositionCombiner;
import com.wifi.positioning.algorithm.impl.PositionCombiner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
public class WeightedAveragePositionCombinerTest {

    private WeightedAveragePositionCombiner combiner;

    @BeforeEach
    public void setUp() {
        combiner = new WeightedAveragePositionCombiner();
    }
    
    // Helper method to create a complete Position object with all required fields
    private Position createPosition(double lat, double lon) {
        return new Position(lat, lon, 0.0, 1.0, 1.0);
    }
    
    @Test
    @DisplayName("combinePositions should return null for null or empty positions")
    public void testCombinePositionsWithNullOrEmpty() {
        assertNull(combiner.combinePositions(null));
        assertNull(combiner.combinePositions(List.of()));
    }
    
    @Test
    @DisplayName("combinePositions should return the only position when there is only one")
    public void testCombinePositionsWithSinglePosition() {
        Position position = createPosition(40.0, -74.0);
        PositionCombiner.WeightedPosition weightedPosition = new PositionCombiner.WeightedPosition(position, 1.0);
        
        Position result = combiner.combinePositions(List.of(weightedPosition));
        
        assertEquals(position, result);
    }
    
    @Test
    @DisplayName("combinePositions should call GeometricQualityFactor.isCollinear")
    public void testCombinePositionsCallsIsCollinear() {
        // Create test positions
        Position position1 = createPosition(40.0, -74.0);
        Position position2 = createPosition(41.0, -73.0);
        Position position3 = createPosition(42.0, -72.0);
        
        List<PositionCombiner.WeightedPosition> weightedPositions = Arrays.asList(
            new PositionCombiner.WeightedPosition(position1, 1.0),
            new PositionCombiner.WeightedPosition(position2, 1.0),
            new PositionCombiner.WeightedPosition(position3, 1.0)
        );
        
        // Use Mockito to verify that GeometricQualityFactor.isCollinear is called
        try (MockedStatic<GeometricQualityFactor> mockedStatic = Mockito.mockStatic(GeometricQualityFactor.class)) {
            // Mock the isCollinear method to return false
            mockedStatic.when(() -> GeometricQualityFactor.isCollinear(anyList())).thenReturn(false);
            
            // Call the method under test
            combiner.combinePositions(weightedPositions);
            
            // Verify that isCollinear was called with a list of positions
            mockedStatic.verify(() -> GeometricQualityFactor.isCollinear(anyList()));
        }
    }
    
    @Test
    @DisplayName("combinePositions should correctly handle collinear positions")
    public void testCombinePositionsWithCollinearPositions() {
        // Create collinear positions
        Position position1 = createPosition(40.0, -74.0);
        Position position2 = createPosition(40.0, -75.0);
        Position position3 = createPosition(40.0, -76.0);
        
        List<PositionCombiner.WeightedPosition> weightedPositions = Arrays.asList(
            new PositionCombiner.WeightedPosition(position1, 1.0),
            new PositionCombiner.WeightedPosition(position2, 1.0),
            new PositionCombiner.WeightedPosition(position3, 1.0)
        );
        
        // Actual test - verify the position is calculated correctly
        Position result = combiner.combinePositions(weightedPositions);
        
        // Since these are collinear positions, we expect:
        // 1. Position to be at the center (40.0, -75.0)
        // 2. Accuracy to be higher (worse) due to collinearity
        // 3. Confidence to be lower due to collinearity
        assertEquals(40.0, result.latitude(), 0.0001);
        assertEquals(-75.0, result.longitude(), 0.0001);
        
        // The exact values depend on the algorithm implementation, but we expect:
        assertTrue(result.accuracy() >= 6.0); // MIN_COLLINEAR_ACCURACY constant
        assertTrue(result.confidence() <= 0.69); // MAX_COLLINEAR_CONFIDENCE constant
    }
    
    @Test
    @DisplayName("combinePositions should correctly handle non-collinear positions")
    public void testCombinePositionsWithNonCollinearPositions() {
        // Create a triangle of positions
        Position position1 = createPosition(40.0, -74.0);
        Position position2 = createPosition(41.0, -75.0);
        Position position3 = createPosition(42.0, -73.0);
        
        List<PositionCombiner.WeightedPosition> weightedPositions = Arrays.asList(
            new PositionCombiner.WeightedPosition(position1, 1.0),
            new PositionCombiner.WeightedPosition(position2, 1.0),
            new PositionCombiner.WeightedPosition(position3, 1.0)
        );
        
        // Calculate the expected position (center of triangle)
        double expectedLat = (40.0 + 41.0 + 42.0) / 3.0;
        double expectedLon = (-74.0 + -75.0 + -73.0) / 3.0;
        
        // Actual test
        Position result = combiner.combinePositions(weightedPositions);
        
        // Basic position should be at the center
        assertEquals(expectedLat, result.latitude(), 0.0001);
        assertEquals(expectedLon, result.longitude(), 0.0001);
        
        // Non-collinear positions should have better accuracy and confidence
        // than collinear ones, but exact values depend on implementation
    }
    
    @Test
    @DisplayName("combinePositions should honor position weights")
    public void testCombinePositionsWithWeights() {
        // Create positions with different weights
        Position position1 = createPosition(40.0, -74.0);
        Position position2 = createPosition(42.0, -72.0);
        
        List<PositionCombiner.WeightedPosition> weightedPositions = Arrays.asList(
            new PositionCombiner.WeightedPosition(position1, 3.0), // Weight 3
            new PositionCombiner.WeightedPosition(position2, 1.0)  // Weight 1
        );
        
        // Calculate weighted average: (40.0*3 + 42.0*1)/4 = 40.5
        double expectedLat = (40.0 * 3.0 + 42.0 * 1.0) / 4.0;
        double expectedLon = (-74.0 * 3.0 + -72.0 * 1.0) / 4.0;
        
        // Actual test
        Position result = combiner.combinePositions(weightedPositions);
        
        // Verify weighted position
        assertEquals(expectedLat, result.latitude(), 0.0001);
        assertEquals(expectedLon, result.longitude(), 0.0001);
    }
} 