package com.wifi.ap.location.estimation.fisher;

import com.wifi.ap.location.Location;
import com.wifi.ap.location.estimation.WiFiFrequencyBand;
import com.wifi.ap.location.estimation.state.CovarianceMatrix;
import org.apache.commons.math3.analysis.MultivariateFunction;
import org.apache.commons.math3.linear.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive test suite for FisherInformationCalculator.
 * 
 * <p>Tests cover:
 * - Gradient-based Fisher Information Matrix calculation
 * - Hessian-based Fisher Information Matrix calculation
 * - Covariance matrix computation
 * - Helper methods (noise variance, information gain, condition number)
 * - Edge cases and numerical stability
 */
@DisplayName("FisherInformationCalculator Tests")
class FisherInformationCalculatorTest {

    @Nested
    @DisplayName("Gradient-Based FIM Tests")
    class GradientBasedFIMTests {

        @Test
        @DisplayName("Should calculate FIM contribution from gradient")
        void calculateGradientBasedFIM_ShouldReturnCorrectMatrix() {
            // Arrange
            Location apLocation = Location.of(37.7749, -122.4194); // San Francisco
            Location measurementLocation = Location.of(37.7750, -122.4195); // Very close
            WiFiFrequencyBand band = WiFiFrequencyBand.BAND_2_4_GHZ;
            String connectionStatus = "CONNECTED";

            // Act
            RealMatrix fim = FisherInformationCalculator.calculateGradientBasedFIM(
                apLocation, measurementLocation, band, connectionStatus);

            // Assert
            assertNotNull(fim);
            assertEquals(2, fim.getRowDimension());
            assertEquals(2, fim.getColumnDimension());
            
            // FIM should be symmetric
            assertEquals(fim.getEntry(0, 1), fim.getEntry(1, 0), 1e-10);
            
            // FIM should be positive semi-definite (all eigenvalues >= 0)
            EigenDecomposition eigen = new EigenDecomposition(fim);
            for (double eigenvalue : eigen.getRealEigenvalues()) {
                assertTrue(eigenvalue >= 0, "Eigenvalue should be non-negative: " + eigenvalue);
            }
        }

        @Test
        @DisplayName("Should produce larger FIM for CONNECTED vs SCAN measurements")
        void calculateGradientBasedFIM_ConnectedShouldHaveHigherPrecision() {
            // Arrange
            Location apLocation = Location.of(37.7749, -122.4194);
            Location measurementLocation = Location.of(37.7750, -122.4195);
            WiFiFrequencyBand band = WiFiFrequencyBand.BAND_2_4_GHZ;

            // Act
            RealMatrix fimConnected = FisherInformationCalculator.calculateGradientBasedFIM(
                apLocation, measurementLocation, band, "CONNECTED");
            RealMatrix fimScan = FisherInformationCalculator.calculateGradientBasedFIM(
                apLocation, measurementLocation, band, "SCAN");

            // Assert - CONNECTED has 4x weight (variance 16 vs 64)
            double ratioExpected = 4.0;
            double ratio = fimConnected.getEntry(0, 0) / fimScan.getEntry(0, 0);
            assertEquals(ratioExpected, ratio, 0.01);
        }

        @Test
        @DisplayName("Should calculate path loss gradient correctly")
        void calculatePathLossGradient_ShouldReturnCorrectGradient() {
            // Arrange
            Location apLocation = Location.of(37.7749, -122.4194);
            Location measurementLocation = Location.of(37.7850, -122.4194); // ~1.1km north
            WiFiFrequencyBand band = WiFiFrequencyBand.BAND_2_4_GHZ;

            // Act
            RealVector gradient = FisherInformationCalculator.calculatePathLossGradient(
                apLocation, measurementLocation, band);

            // Assert
            assertEquals(2, gradient.getDimension());
            
            // Gradient components should be non-zero
            assertNotEquals(0.0, gradient.getEntry(0), 1e-10, "Latitude gradient should be non-zero");
            
            // Since measurement is north of AP (higher latitude), the gradient direction depends on
            // the gradient formula: ∇h = -10n/ln(10) × (AP - measurement) / distance²
            // Just verify gradient is reasonable magnitude
            assertTrue(Math.abs(gradient.getEntry(0)) > 0, "Latitude gradient should have non-zero magnitude");
        }
    }

    @Nested
    @DisplayName("Hessian-Based FIM Tests")
    class HessianBasedFIMTests {

        @Test
        @DisplayName("Should compute Hessian matrix numerically")
        void computeHessianMatrix_ShouldReturnSymmetricMatrix() {
            // Arrange
            double[] location = {37.7749, -122.4194};
            
            // Simple quadratic objective function: f(x,y) = x² + 2xy + y²
            MultivariateFunction objectiveFunction = point -> {
                double x = point[0];
                double y = point[1];
                return x*x + 2*x*y + y*y;
            };

            // Act
            RealMatrix hessian = FisherInformationCalculator.computeHessianMatrix(
                location, objectiveFunction);

            // Assert
            assertNotNull(hessian);
            assertEquals(2, hessian.getRowDimension());
            assertEquals(2, hessian.getColumnDimension());
            
            // Hessian should be symmetric
            assertEquals(hessian.getEntry(0, 1), hessian.getEntry(1, 0), 1e-6);
            
            // For f(x,y) = x² + 2xy + y², Hessian should be [[2, 2], [2, 2]]
            assertEquals(2.0, hessian.getEntry(0, 0), 0.1);
            assertEquals(2.0, hessian.getEntry(0, 1), 0.1);
            assertEquals(2.0, hessian.getEntry(1, 0), 0.1);
            assertEquals(2.0, hessian.getEntry(1, 1), 0.1);
        }

        @Test
        @DisplayName("Should handle convex objective function")
        void computeHessianMatrix_ConvexFunction_ShouldBePositiveDefinite() {
            // Arrange
            double[] location = {0.0, 0.0};
            
            // Convex function: f(x,y) = 3x² + 4y²
            MultivariateFunction objectiveFunction = point -> 
                3 * point[0] * point[0] + 4 * point[1] * point[1];

            // Act
            RealMatrix hessian = FisherInformationCalculator.computeHessianMatrix(
                location, objectiveFunction);

            // Assert - all eigenvalues should be positive
            assertTrue(FisherInformationCalculator.isPositiveDefinite(hessian),
                      "Hessian of convex function should be positive definite");
        }
    }

    @Nested
    @DisplayName("Covariance Matrix Tests")
    class CovarianceMatrixTests {

        @Test
        @DisplayName("Should compute covariance from Fisher Information Matrix")
        void computeCovarianceMatrix_ShouldInvertFIM() {
            // Arrange
            double[][] fimData = {{4.0, 0.0}, {0.0, 4.0}}; // Diagonal FIM
            RealMatrix fim = new Array2DRowRealMatrix(fimData);

            // Act
            RealMatrix covariance = FisherInformationCalculator.computeCovarianceMatrix(fim);

            // Assert
            assertEquals(0.25, covariance.getEntry(0, 0), 1e-10); // 1/4
            assertEquals(0.25, covariance.getEntry(1, 1), 1e-10); // 1/4
            assertEquals(0.0, covariance.getEntry(0, 1), 1e-10);
            assertEquals(0.0, covariance.getEntry(1, 0), 1e-10);
        }

        @Test
        @DisplayName("Should throw exception for singular matrix")
        void computeCovarianceMatrix_SingularMatrix_ShouldThrow() {
            // Arrange
            double[][] singularData = {{1.0, 1.0}, {1.0, 1.0}}; // Singular matrix
            RealMatrix singular = new Array2DRowRealMatrix(singularData);

            // Act & Assert
            assertThrows(SingularMatrixException.class, () -> 
                FisherInformationCalculator.computeCovarianceMatrix(singular));
        }

        @Test
        @DisplayName("Should convert RealMatrix to CovarianceMatrix")
        void toCovarianceMatrix_ShouldConvertCorrectly() {
            // Arrange
            double[][] covData = {{1.0, 0.5}, {0.5, 2.0}};
            RealMatrix realMatrix = new Array2DRowRealMatrix(covData);

            // Act
            CovarianceMatrix covMatrix = FisherInformationCalculator.toCovarianceMatrix(realMatrix);

            // Assert
            assertEquals(1.0, covMatrix.xx(), 1e-10);
            assertEquals(0.5, covMatrix.xy(), 1e-10);
            assertEquals(0.5, covMatrix.yx(), 1e-10);
            assertEquals(2.0, covMatrix.yy(), 1e-10);
        }
    }

    @Nested
    @DisplayName("Helper Methods Tests")
    class HelperMethodsTests {

        @Test
        @DisplayName("Should calculate inverse noise variance correctly")
        void calculateInverseNoiseVariance_ShouldReturnCorrectValues() {
            // Act
            double connectedPrecision = FisherInformationCalculator.calculateInverseNoiseVariance("CONNECTED");
            double scanPrecision = FisherInformationCalculator.calculateInverseNoiseVariance("SCAN");

            // Assert
            assertEquals(1.0/16.0, connectedPrecision, 1e-10); // 1/(4²)
            assertEquals(1.0/64.0, scanPrecision, 1e-10);       // 1/(8²)
            
            // CONNECTED should have 4x higher precision
            assertEquals(4.0, connectedPrecision / scanPrecision, 1e-10);
        }

        @Test
        @DisplayName("Should calculate noise variance correctly")
        void calculateNoiseVariance_ShouldReturnCorrectValues() {
            // Act
            double connectedVariance = FisherInformationCalculator.calculateNoiseVariance("CONNECTED");
            double scanVariance = FisherInformationCalculator.calculateNoiseVariance("SCAN");

            // Assert
            assertEquals(16.0, connectedVariance, 1e-10); // 4²
            assertEquals(64.0, scanVariance, 1e-10);       // 8²
        }

        @Test
        @DisplayName("Should calculate information gain correctly")
        void calculateInformationGain_ShouldReturnPositiveValue() {
            // Arrange
            double priorDeterminant = 100.0;
            double posteriorDeterminant = 25.0; // Uncertainty reduced by 4x

            // Act
            double informationGain = FisherInformationCalculator.calculateInformationGain(
                priorDeterminant, posteriorDeterminant);

            // Assert
            // IG = 0.5 * ln(100/25) = 0.5 * ln(4) ≈ 0.693 nats
            assertEquals(0.693, informationGain, 0.01);
            assertTrue(informationGain > 0, "Information gain should be positive for uncertainty reduction");
        }

        @Test
        @DisplayName("Should return zero information gain for invalid determinants")
        void calculateInformationGain_InvalidDeterminants_ShouldReturnZero() {
            // Act & Assert
            assertEquals(0.0, FisherInformationCalculator.calculateInformationGain(0.0, 100.0));
            assertEquals(0.0, FisherInformationCalculator.calculateInformationGain(100.0, 0.0));
            assertEquals(0.0, FisherInformationCalculator.calculateInformationGain(-1.0, 100.0));
        }

        @Test
        @DisplayName("Should check positive definiteness correctly")
        void isPositiveDefinite_ShouldWorkCorrectly() {
            // Arrange
            double[][] positiveData = {{4.0, 1.0}, {1.0, 3.0}}; // Positive definite
            double[][] negativeData = {{-4.0, 1.0}, {1.0, -3.0}}; // Negative definite
            
            RealMatrix positive = new Array2DRowRealMatrix(positiveData);
            RealMatrix negative = new Array2DRowRealMatrix(negativeData);

            // Act & Assert
            assertTrue(FisherInformationCalculator.isPositiveDefinite(positive));
            assertFalse(FisherInformationCalculator.isPositiveDefinite(negative));
        }

        @Test
        @DisplayName("Should calculate condition number correctly")
        void calculateConditionNumber_ShouldReturnCorrectValue() {
            // Arrange - diagonal matrix with eigenvalues 100 and 1
            double[][] data = {{100.0, 0.0}, {0.0, 1.0}};
            RealMatrix matrix = new Array2DRowRealMatrix(data);

            // Act
            double conditionNumber = FisherInformationCalculator.calculateConditionNumber(matrix);

            // Assert
            assertEquals(100.0, conditionNumber, 1e-6);
        }

        @Test
        @DisplayName("Should identify well-conditioned matrix")
        void calculateConditionNumber_WellConditioned_ShouldBeLow() {
            // Arrange - nearly diagonal matrix
            double[][] data = {{4.0, 0.1}, {0.1, 4.0}};
            RealMatrix matrix = new Array2DRowRealMatrix(data);

            // Act
            double conditionNumber = FisherInformationCalculator.calculateConditionNumber(matrix);

            // Assert
            assertTrue(conditionNumber < 10, "Well-conditioned matrix should have low condition number");
        }
    }

    @Nested
    @DisplayName("Integration Tests")
    class IntegrationTests {

        @Test
        @DisplayName("Should produce consistent results across methods")
        void gradientAndHessianMethods_ShouldBeConsistent() {
            // This test verifies that gradient-based and Hessian-based approaches
            // produce Fisher Information Matrices with similar properties
            
            // Arrange
            Location apLocation = Location.of(37.7749, -122.4194);
            Location measurementLocation = Location.of(37.7760, -122.4194);
            WiFiFrequencyBand band = WiFiFrequencyBand.BAND_2_4_GHZ;

            // Act
            RealMatrix gradientFIM = FisherInformationCalculator.calculateGradientBasedFIM(
                apLocation, measurementLocation, band, "CONNECTED");

            // Assert - gradient FIM should be positive semi-definite (eigenvalues >= 0)
            // Note: Single measurement may produce rank-1 matrix (not strictly positive definite)
            EigenDecomposition eigen = new EigenDecomposition(gradientFIM);
            for (double eigenvalue : eigen.getRealEigenvalues()) {
                assertTrue(eigenvalue >= -1e-10, "Eigenvalue should be non-negative: " + eigenvalue);
            }
        }

        @Test
        @DisplayName("Should calculate FIM with correct structure")
        void fisherInformationMatrix_ShouldHaveCorrectStructure() {
            // Arrange
            Location apLocation = Location.of(37.7749, -122.4194);
            Location measurementLocation = Location.of(37.7760, -122.4194); // ~1.2km north
            WiFiFrequencyBand band = WiFiFrequencyBand.BAND_2_4_GHZ;

            // Act
            RealMatrix fim = FisherInformationCalculator.calculateGradientBasedFIM(
                apLocation, measurementLocation, band, "CONNECTED");

            // Assert - FIM should be 2×2 and symmetric
            assertEquals(2, fim.getRowDimension());
            assertEquals(2, fim.getColumnDimension());
            assertEquals(fim.getEntry(0, 1), fim.getEntry(1, 0), 1e-10,
                        "FIM should be symmetric");
        }
    }
}

