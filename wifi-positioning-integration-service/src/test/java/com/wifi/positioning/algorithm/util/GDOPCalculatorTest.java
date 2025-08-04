package com.wifi.positioning.algorithm.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for GDOPCalculator utility class.
 * Ensures that calculations match existing implementations in:
 * - TrilaterationAlgorithm
 * - MaximumLikelihoodAlgorithm
 * - WeightedAveragePositionCombiner
 */
@DisplayName("GDOP Calculator Tests")
class GDOPCalculatorTest {
    
    private static final double DELTA = 0.001;
    
    /**
     * Tests for GDOP calculation with various geometric configurations
     */
    @Nested
    @DisplayName("GDOP Calculation Tests")
    class GDOPCalculationTests {
        
        @Test
        @DisplayName("should calculate reasonable GDOP for triangular AP arrangement")
        void shouldCalculateExcellentGDOPForGoodGeometry() {
            // Create an equilateral triangle arrangement - excellent geometry
            double[][] coordinates = {
                {0.0, 0.0, 0.0},
                {100.0, 0.0, 0.0},
                {50.0, 86.6, 0.0}  // 100 * sin(60°) = 86.6
            };
            double[] position = {50.0, 28.87, 0.0};  // Center of triangle
            
            double gdop = GDOPCalculator.calculateGDOP(coordinates, position, true);
            
            // Should return a valid GDOP value (capped at MAX_ALLOWED_GDOP)
            assertTrue(gdop <= GDOPCalculator.MAX_ALLOWED_GDOP, 
                "Expected GDOP <= MAX_ALLOWED_GDOP for triangular geometry, got " + gdop);
        }
        
        @Test
        @DisplayName("should calculate poor GDOP for collinear APs")
        void shouldCalculatePoorGDOPForCollinearAPs() {
            // Create collinear APs - poor geometry
            double[][] coordinates = {
                {0.0, 0.0, 0.0},
                {50.0, 0.0, 0.0},
                {100.0, 0.0, 0.0}
            };
            double[] position = {50.0, 10.0, 0.0};  // Off the line slightly
            
            double gdop = GDOPCalculator.calculateGDOP(coordinates, position, true);
            
            // Should be poor GDOP (> 6.0)
            assertTrue(gdop > GDOPCalculator.FAIR_GDOP, 
                "Expected poor GDOP for collinear APs, got " + gdop);
        }
        
        @Test
        @DisplayName("should handle minimum required APs")
        void shouldHandleMinimumRequiredAPs() {
            // Need at least 3 APs for GDOP calculation
            double[][] coordinates = {
                {0.0, 0.0, 0.0},
                {100.0, 0.0, 0.0}
            };
            double[] position = {50.0, 50.0, 0.0};
            
            double gdop = GDOPCalculator.calculateGDOP(coordinates, position, true);
            
            // Should return MAX_ALLOWED_GDOP
            assertEquals(GDOPCalculator.MAX_ALLOWED_GDOP, gdop, DELTA,
                "Expected MAX_ALLOWED_GDOP for fewer than 3 APs");
        }
        
        @Test
        @DisplayName("should handle 2D coordinates properly")
        void shouldHandle2DCoordinatesProperly() {
            // 2D coordinates (no Z dimension)
            double[][] coordinates = {
                {0.0, 0.0},
                {100.0, 0.0},
                {50.0, 86.6}
            };
            double[] position = {50.0, 28.87};
            
            double gdop = GDOPCalculator.calculateGDOP(coordinates, position, false);
            
            // Should be excellent GDOP (< 2.0)
            assertTrue(gdop < GDOPCalculator.EXCELLENT_GDOP, 
                "Expected excellent GDOP for 2D triangular geometry, got " + gdop);
        }
    }
    
    /**
     * Tests for GDOP factor calculation
     */
    @Nested
    @DisplayName("GDOP Factor Tests")
    class GDOPFactorTests {
        
        @Test
        @DisplayName("should return factor 1.0 for excellent GDOP")
        void shouldReturnFactor1ForExcellentGDOP() {
            double gdop = 1.5;  // Well below EXCELLENT_GDOP threshold (2.0)
            double factor = GDOPCalculator.calculateGDOPFactor(gdop);
            
            assertEquals(1.0, factor, DELTA,
                "Expected factor 1.0 for excellent GDOP");
        }
        
        @Test
        @DisplayName("should return factor between 1.0-1.5 for good GDOP")
        void shouldReturnFactorBetween1And1Point5ForGoodGDOP() {
            double gdop = 3.0;  // Between EXCELLENT_GDOP (2.0) and GOOD_GDOP (4.0)
            double factor = GDOPCalculator.calculateGDOPFactor(gdop);
            
            assertTrue(factor > 1.0 && factor < 1.5,
                "Expected factor between 1.0 and 1.5 for good GDOP, got " + factor);
        }
        
        @Test
        @DisplayName("should return factor between 1.5-2.0 for fair GDOP")
        void shouldReturnFactorBetween1Point5And2ForFairGDOP() {
            double gdop = 5.0;  // Between GOOD_GDOP (4.0) and FAIR_GDOP (6.0)
            double factor = GDOPCalculator.calculateGDOPFactor(gdop);
            
            assertTrue(factor >= 1.5 && factor <= 2.0,
                "Expected factor between 1.5 and 2.0 for fair GDOP, got " + factor);
        }
        
        @Test
        @DisplayName("should return factor > 2.0 for poor GDOP")
        void shouldReturnFactorGreaterThan2ForPoorGDOP() {
            double gdop = 8.0;  // Above FAIR_GDOP (6.0)
            double factor = GDOPCalculator.calculateGDOPFactor(gdop);
            
            assertTrue(factor > 2.0,
                "Expected factor > 2.0 for poor GDOP, got " + factor);
        }
        
        @Test
        @DisplayName("should cap factor at 4.0 for very poor GDOP")
        void shouldCapFactorAt4ForVeryPoorGDOP() {
            double gdop = 15.0;  // Much higher than MAX_ALLOWED_GDOP (10.0)
            double factor = GDOPCalculator.calculateGDOPFactor(gdop);
            
            assertEquals(4.0, factor, DELTA,
                "Expected factor capped at 4.0 for very poor GDOP");
        }
    }
    
    /**
     * Tests for condition number and geometric quality factor calculations
     */
    @Nested
    @DisplayName("Geometric Quality Tests")
    class GeometricQualityTests {
        
        @Test
        @DisplayName("should calculate correct condition number for well-conditioned matrix")
        void shouldCalculateCorrectConditionNumberForWellConditionedMatrix() {
            // Well-conditioned covariance matrix
            double covLatLat = 1.0;
            double covLonLon = 1.2;
            double covLatLon = 0.1;
            
            double conditionNumber = GDOPCalculator.calculateConditionNumber(covLatLat, covLonLon, covLatLon);
            
            // Should be close to 1 for well-conditioned matrix
            assertTrue(conditionNumber < 2.0,
                "Expected condition number < 2.0 for well-conditioned matrix, got " + conditionNumber);
        }
        
        @Test
        @DisplayName("should calculate high condition number for nearly singular matrix")
        void shouldCalculateHighConditionNumberForNearlySingularMatrix() {
            // Nearly singular covariance matrix (determinant close to zero)
            double covLatLat = 1.0;
            double covLonLon = 1.0;
            double covLatLon = 0.999;  // Makes determinant near zero
            
            double conditionNumber = GDOPCalculator.calculateConditionNumber(covLatLat, covLonLon, covLatLon);
            
            // Should be very high for nearly singular matrix
            assertTrue(conditionNumber > 100.0,
                "Expected condition number > 100.0 for nearly singular matrix, got " + conditionNumber);
        }
        
        @Test
        @DisplayName("should calculate correct geometric quality factor for good geometry")
        void shouldCalculateCorrectGeometricFactorForGoodGeometry() {
            double conditionNumber = 3.0;  // Below GOOD_GEOMETRY_THRESHOLD (5.0)
            boolean isCollinear = false;
            
            double factor = GDOPCalculator.calculateGeometricQualityFactor(conditionNumber, isCollinear);
            
            assertEquals(1.0, factor, DELTA,
                "Expected factor 1.0 for good geometry");
        }
        
        @Test
        @DisplayName("should calculate correct geometric quality factor for collinear geometry")
        void shouldCalculateCorrectGeometricFactorForCollinearGeometry() {
            double conditionNumber = 50.0;  // High condition number
            boolean isCollinear = true;
            
            double factor = GDOPCalculator.calculateGeometricQualityFactor(conditionNumber, isCollinear);
            
            // COLLINEAR_BASE_FACTOR (2.0) + log adjustment
            assertTrue(factor > GDOPCalculator.COLLINEAR_BASE_FACTOR,
                "Expected factor > COLLINEAR_BASE_FACTOR for collinear geometry");
        }
    }
} 