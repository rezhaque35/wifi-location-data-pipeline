// wifi-data-pipeline-computation/wifi-access-point-localization/src/test/java/com/wifi/ap/location/estimation/state/CovarianceMatrixTest.java
package com.wifi.ap.location.estimation.state;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import static org.assertj.core.api.Assertions.*;

/**
 * Comprehensive unit tests for CovarianceMatrix class.
 * 
 * <p>Tests focus on the new methods added to support Sequential Bayesian Update
 * confidence calculation:
 * <ul>
 *   <li>{@link CovarianceMatrix#getEigenvalues()}</li>
 *   <li>{@link CovarianceMatrix#getLatitudeVariance()}</li>
 *   <li>{@link CovarianceMatrix#getLongitudeVariance()}</li>
 * </ul>
 * 
 * <p><strong>Mathematical Foundation:</strong>
 * For a 2x2 symmetric covariance matrix:
 * <pre>
 * | xx  xy |
 * | yx  yy |
 * </pre>
 * Eigenvalues are: λ = (tr ± √(tr² - 4·det)) / 2
 * where tr = xx + yy (trace), det = xx·yy - xy·yx
 * 
 * @author WiFi Access Point Localization Team
 * @since 1.0
 */
@DisplayName("CovarianceMatrix Tests")
class CovarianceMatrixTest {

    // ==================== Eigenvalue Calculation Tests ====================

    @Nested
    @DisplayName("Eigenvalue Calculation Tests")
    class EigenvalueTests {

        @Test
        @DisplayName("Test 1: Diagonal Matrix Eigenvalues")
        void testDiagonalMatrixEigenvalues() {
            // For diagonal matrix, eigenvalues = diagonal elements
            
            // Given: Diagonal covariance matrix
            CovarianceMatrix diagonal = CovarianceMatrix.diagonal(9.0, 4.0);
            
            // When: Computing eigenvalues
            double[] eigenvalues = diagonal.getEigenvalues();
            
            // Then: Eigenvalues should be diagonal elements in descending order
            assertThat(eigenvalues).hasSize(2);
            assertThat(eigenvalues[0]).isEqualTo(9.0); // Largest first
            assertThat(eigenvalues[1]).isEqualTo(4.0);
        }

        @Test
        @DisplayName("Test 2: Identity Matrix Eigenvalues")
        void testIdentityMatrixEigenvalues() {
            // Identity matrix has eigenvalues = 1.0
            
            // Given: Identity covariance matrix
            CovarianceMatrix identity = CovarianceMatrix.identity();
            
            // When: Computing eigenvalues
            double[] eigenvalues = identity.getEigenvalues();
            
            // Then: Both eigenvalues should be 1.0
            assertThat(eigenvalues).hasSize(2);
            assertThat(eigenvalues[0]).isEqualTo(1.0);
            assertThat(eigenvalues[1]).isEqualTo(1.0);
        }

        @Test
        @DisplayName("Test 3: Eigenvalues are Sorted Descending")
        void testEigenvaluesSortedDescending() {
            // Eigenvalues should always be returned in descending order
            
            // Given: Various covariance matrices
            CovarianceMatrix cov1 = new CovarianceMatrix(16.0, 2.0, 2.0, 9.0);
            CovarianceMatrix cov2 = new CovarianceMatrix(4.0, 1.0, 1.0, 25.0);
            
            // When: Computing eigenvalues
            double[] eigenvalues1 = cov1.getEigenvalues();
            double[] eigenvalues2 = cov2.getEigenvalues();
            
            // Then: First eigenvalue should be >= second
            assertThat(eigenvalues1[0]).isGreaterThanOrEqualTo(eigenvalues1[1]);
            assertThat(eigenvalues2[0]).isGreaterThanOrEqualTo(eigenvalues2[1]);
        }

        @Test
        @DisplayName("Test 4: Eigenvalues are Positive for Valid Covariance")
        void testEigenvaluesPositive() {
            // Valid covariance matrix must have positive eigenvalues (positive definite)
            
            // Given: Valid covariance matrix
            CovarianceMatrix cov = new CovarianceMatrix(10.0, 2.0, 2.0, 8.0);
            
            // When: Computing eigenvalues
            double[] eigenvalues = cov.getEigenvalues();
            
            // Then: Both eigenvalues should be positive
            assertThat(eigenvalues[0]).isPositive();
            assertThat(eigenvalues[1]).isPositive();
        }

        @Test
        @DisplayName("Test 5: Eigenvalue Sum Equals Trace")
        void testEigenvalueSumEqualsTrace() {
            // Mathematical property: λ₁ + λ₂ = trace(Σ) = xx + yy
            
            // Given: Covariance matrix
            double xx = 12.5;
            double yy = 7.3;
            double xy = 2.1;
            CovarianceMatrix cov = new CovarianceMatrix(xx, xy, xy, yy);
            
            // When: Computing eigenvalues
            double[] eigenvalues = cov.getEigenvalues();
            double eigenvalueSum = eigenvalues[0] + eigenvalues[1];
            double trace = xx + yy;
            
            // Then: Sum should equal trace (within numerical precision)
            assertThat(eigenvalueSum).isCloseTo(trace, within(1e-10));
        }

        @Test
        @DisplayName("Test 6: Eigenvalue Product Equals Determinant")
        void testEigenvalueProductEqualsDeterminant() {
            // Mathematical property: λ₁ × λ₂ = det(Σ)
            
            // Given: Covariance matrix
            double xx = 15.0;
            double yy = 10.0;
            double xy = 3.0;
            CovarianceMatrix cov = new CovarianceMatrix(xx, xy, xy, yy);
            
            // When: Computing eigenvalues and determinant
            double[] eigenvalues = cov.getEigenvalues();
            double eigenvalueProduct = eigenvalues[0] * eigenvalues[1];
            double determinant = cov.determinant();
            
            // Then: Product should equal determinant (within numerical precision)
            assertThat(eigenvalueProduct).isCloseTo(determinant, within(1e-10));
        }

        @Test
        @DisplayName("Test 7: Symmetric Matrix with Correlation")
        void testSymmetricMatrixWithCorrelation() {
            // Test eigenvalues for matrix with correlation
            
            // Given: Covariance matrix with correlation
            CovarianceMatrix cov = new CovarianceMatrix(20.0, 5.0, 5.0, 15.0);
            
            // When: Computing eigenvalues
            double[] eigenvalues = cov.getEigenvalues();
            
            // Then: Eigenvalues should be valid and properly ordered
            assertThat(eigenvalues).hasSize(2);
            assertThat(eigenvalues[0]).isGreaterThanOrEqualTo(eigenvalues[1]);
            assertThat(eigenvalues[0]).isPositive();
            assertThat(eigenvalues[1]).isPositive();
            
            // Verify mathematical properties
            double trace = 20.0 + 15.0;
            double det = 20.0 * 15.0 - 5.0 * 5.0;
            assertThat(eigenvalues[0] + eigenvalues[1]).isCloseTo(trace, within(1e-10));
            assertThat(eigenvalues[0] * eigenvalues[1]).isCloseTo(det, within(1e-10));
        }

        @Test
        @DisplayName("Test 8: Nearly Singular Matrix")
        void testNearlySingularMatrix() {
            // Test behavior with nearly singular matrix (small determinant)
            
            // Given: Nearly singular covariance matrix
            CovarianceMatrix cov = new CovarianceMatrix(10.0, 9.99, 9.99, 10.0);
            
            // When: Computing eigenvalues
            double[] eigenvalues = cov.getEigenvalues();
            
            // Then: Should have one large and one very small eigenvalue
            assertThat(eigenvalues[0]).isGreaterThan(10.0); // Large eigenvalue
            assertThat(eigenvalues[1]).isLessThan(1.0);     // Small eigenvalue
            
            // But both should still be non-negative
            assertThat(eigenvalues[0]).isGreaterThanOrEqualTo(0.0);
            assertThat(eigenvalues[1]).isGreaterThanOrEqualTo(0.0);
        }
    }

    // ==================== Variance Accessor Tests ====================

    @Nested
    @DisplayName("Variance Accessor Tests")
    class VarianceAccessorTests {

        @Test
        @DisplayName("Test 9: getLatitudeVariance Returns yy Component")
        void testGetLatitudeVariance() {
            // Latitude variance is the yy component
            
            // Given: Covariance matrix
            double expectedLatVariance = 12.5;
            CovarianceMatrix cov = new CovarianceMatrix(8.0, 2.0, 2.0, expectedLatVariance);
            
            // When: Getting latitude variance
            double latVariance = cov.getLatitudeVariance();
            
            // Then: Should return yy component
            assertThat(latVariance).isEqualTo(expectedLatVariance);
        }

        @Test
        @DisplayName("Test 10: getLongitudeVariance Returns xx Component")
        void testGetLongitudeVariance() {
            // Longitude variance is the xx component
            
            // Given: Covariance matrix
            double expectedLonVariance = 15.3;
            CovarianceMatrix cov = new CovarianceMatrix(expectedLonVariance, 3.0, 3.0, 10.0);
            
            // When: Getting longitude variance
            double lonVariance = cov.getLongitudeVariance();
            
            // Then: Should return xx component
            assertThat(lonVariance).isEqualTo(expectedLonVariance);
        }

        @Test
        @DisplayName("Test 11: Variance Accessors for Diagonal Matrix")
        void testVarianceAccessorsForDiagonalMatrix() {
            // Test accessors with diagonal matrix
            
            // Given: Diagonal covariance matrix
            double lonVariance = 20.0;
            double latVariance = 18.0;
            CovarianceMatrix diagonal = CovarianceMatrix.diagonal(lonVariance, latVariance);
            
            // When: Getting variances
            double actualLonVariance = diagonal.getLongitudeVariance();
            double actualLatVariance = diagonal.getLatitudeVariance();
            
            // Then: Should match input values
            assertThat(actualLonVariance).isEqualTo(lonVariance);
            assertThat(actualLatVariance).isEqualTo(latVariance);
        }

        @Test
        @DisplayName("Test 12: Variance Accessors for Identity Matrix")
        void testVarianceAccessorsForIdentityMatrix() {
            // Identity matrix has unit variances
            
            // Given: Identity covariance matrix
            CovarianceMatrix identity = CovarianceMatrix.identity();
            
            // When: Getting variances
            double lonVariance = identity.getLongitudeVariance();
            double latVariance = identity.getLatitudeVariance();
            
            // Then: Both should be 1.0
            assertThat(lonVariance).isEqualTo(1.0);
            assertThat(latVariance).isEqualTo(1.0);
        }
    }

    // ==================== Integration Tests ====================

    @Nested
    @DisplayName("Integration with Existing Methods")
    class IntegrationTests {

        @Test
        @DisplayName("Test 13: Eigenvalues Related to Horizontal Accuracy")
        void testEigenvaluesRelatedToHorizontalAccuracy() {
            // Eigenvalues represent uncertainty in principal directions
            // Larger eigenvalues = more uncertainty
            
            // Given: Two covariance matrices with different uncertainties
            CovarianceMatrix lowUncertainty = new CovarianceMatrix(4.0, 0.5, 0.5, 3.0);
            CovarianceMatrix highUncertainty = new CovarianceMatrix(16.0, 2.0, 2.0, 12.0);
            
            // When: Computing eigenvalues
            double[] eigenLow = lowUncertainty.getEigenvalues();
            double[] eigenHigh = highUncertainty.getEigenvalues();
            
            // Then: High uncertainty should have larger eigenvalues
            assertThat(eigenHigh[0]).isGreaterThan(eigenLow[0]);
            assertThat(eigenHigh[1]).isGreaterThan(eigenLow[1]);
        }

        @Test
        @DisplayName("Test 14: Variance Accessors Consistent with asArray")
        void testVarianceAccessorsConsistentWithAsArray() {
            // Variance accessors should be consistent with matrix array representation
            
            // Given: Covariance matrix
            double xx = 11.5;
            double yy = 8.3;
            double xy = 2.7;
            CovarianceMatrix cov = new CovarianceMatrix(xx, xy, xy, yy);
            
            // When: Getting values through different methods
            double lonVariance = cov.getLongitudeVariance();
            double latVariance = cov.getLatitudeVariance();
            double[][] array = cov.asArray();
            
            // Then: Values should match
            assertThat(lonVariance).isEqualTo(array[0][0]);
            assertThat(latVariance).isEqualTo(array[1][1]);
            assertThat(lonVariance).isEqualTo(xx);
            assertThat(latVariance).isEqualTo(yy);
        }

        @Test
        @DisplayName("Test 15: Eigenvalues for Realistic WiFi Positioning Covariance")
        void testEigenvaluesForRealisticCovariance() {
            // Test with realistic covariance values from WiFi positioning
            // Typical horizontal accuracy: 5-10 meters
            // Variance = accuracy² ≈ 25-100 m²
            
            // Given: Realistic covariance matrix
            // Longitude variance: 64 m² (8m std dev)
            // Latitude variance: 49 m² (7m std dev)
            // Small correlation: 10 m²
            CovarianceMatrix realistic = new CovarianceMatrix(64.0, 10.0, 10.0, 49.0);
            
            // When: Computing eigenvalues
            double[] eigenvalues = realistic.getEigenvalues();
            
            // Then: Eigenvalues should be realistic
            assertThat(eigenvalues[0]).isBetween(50.0, 100.0); // Major axis uncertainty
            assertThat(eigenvalues[1]).isBetween(10.0, 70.0);  // Minor axis uncertainty
            
            // Standard deviations should be realistic (5-10m)
            double majorAxisStdDev = Math.sqrt(eigenvalues[0]);
            double minorAxisStdDev = Math.sqrt(eigenvalues[1]);
            assertThat(majorAxisStdDev).isBetween(5.0, 15.0);
            assertThat(minorAxisStdDev).isBetween(3.0, 10.0);
        }
    }
}

