package com.wifi.ap.location.measurements.outlier.global;

import com.wifi.ap.location.Location;
import com.wifi.ap.location.measurements.math.ECEFVector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for ECEFVector record and its operations.
 * 
 * <p>Tests cover vector creation, mathematical operations, conversions,
 * and edge cases to ensure the immutable ECEF vector implementation
 * works correctly in isolation.
 */
@DisplayName("ECEFVector Tests")
class ECEFVectorTest {

    @Nested
    @DisplayName("Factory Methods")
    class FactoryMethodsTest {

        @Test
        @DisplayName("Should create ECEF vector from location correctly")
        void fromLocation_WithValidLocation_ShouldCreateCorrectVector() {
            // Given
            Location location = Location.of(45.0, 90.0); // 45°N, 90°E
            
            // When
            ECEFVector vector = ECEFVector.fromLocation(location);
            
            // Then
            assertThat(vector.x()).isCloseTo(0.0, within(1e-10)); // cos(45°) * cos(90°) ≈ 0
            assertThat(vector.y()).isCloseTo(0.7071, within(1e-4)); // cos(45°) * sin(90°) ≈ 0.7071
            assertThat(vector.z()).isCloseTo(0.7071, within(1e-4)); // sin(45°) ≈ 0.7071
        }

        @Test
        @DisplayName("Should create zero vector correctly")
        void zero_ShouldCreateZeroVector() {
            // When
            ECEFVector vector = ECEFVector.zero();
            
            // Then
            assertThat(vector.x()).isZero();
            assertThat(vector.y()).isZero();
            assertThat(vector.z()).isZero();
            assertThat(vector.isZero()).isTrue();
        }

        @Test
        @DisplayName("Should throw exception for null location")
        void fromLocation_WithNullLocation_ShouldThrowException() {
            // When & Then
            assertThatThrownBy(() -> ECEFVector.fromLocation(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Location cannot be null");
        }
    }

    @Nested
    @DisplayName("Vector Operations")
    class VectorOperationsTest {

        @Test
        @DisplayName("Should add vectors correctly")
        void add_WithTwoVectors_ShouldAddComponentWise() {
            // Given
            ECEFVector vector1 = new ECEFVector(1.0, 2.0, 3.0);
            ECEFVector vector2 = new ECEFVector(4.0, 5.0, 6.0);
            
            // When
            ECEFVector result = vector1.add(vector2);
            
            // Then
            assertThat(result.x()).isEqualTo(5.0);
            assertThat(result.y()).isEqualTo(7.0);
            assertThat(result.z()).isEqualTo(9.0);
        }

        @Test
        @DisplayName("Should scale vector correctly")
        void scale_WithPositiveWeight_ShouldScaleAllComponents() {
            // Given
            ECEFVector vector = new ECEFVector(2.0, 3.0, 4.0);
            double weight = 2.5;
            
            // When
            ECEFVector result = vector.scale(weight);
            
            // Then
            assertThat(result.x()).isEqualTo(5.0);
            assertThat(result.y()).isEqualTo(7.5);
            assertThat(result.z()).isEqualTo(10.0);
        }

        @Test
        @DisplayName("Should calculate magnitude correctly")
        void magnitude_WithUnitVector_ShouldReturnOne() {
            // Given
            ECEFVector vector = ECEFVector.fromLocation(Location.of(0.0, 0.0)); // Equator, Prime Meridian
            
            // When
            double magnitude = vector.magnitude();
            
            // Then
            assertThat(magnitude).isCloseTo(1.0, within(1e-10));
        }

        @Test
        @DisplayName("Should normalize vector correctly")
        void normalize_WithNonUnitVector_ShouldReturnUnitVector() {
            // Given
            ECEFVector vector = new ECEFVector(3.0, 4.0, 0.0); // 3-4-5 triangle
            
            // When
            ECEFVector normalized = vector.normalize();
            
            // Then
            assertThat(normalized.magnitude()).isCloseTo(1.0, within(1e-10));
            assertThat(normalized.x()).isCloseTo(0.6, within(1e-10)); // 3/5
            assertThat(normalized.y()).isCloseTo(0.8, within(1e-10)); // 4/5
            assertThat(normalized.z()).isZero();
        }

        @Test
        @DisplayName("Should throw exception when adding null vector")
        void add_WithNullVector_ShouldThrowException() {
            // Given
            ECEFVector vector = new ECEFVector(1.0, 2.0, 3.0);
            
            // When & Then
            assertThatThrownBy(() -> vector.add(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Other vector cannot be null");
        }
    }

    @Nested
    @DisplayName("Conversions")
    class ConversionsTest {

        @Test
        @DisplayName("Should convert back to location correctly")
        void toLocation_WithNormalizedVector_ShouldRecreateOriginalLocation() {
            // Given
            Location originalLocation = Location.of(37.7749, -122.4194); // San Francisco
            ECEFVector vector = ECEFVector.fromLocation(originalLocation);
            
            // When
            Location convertedLocation = vector.toLocation();
            
            // Then
            assertThat(convertedLocation.latitude()).isCloseTo(37.7749, within(1e-10));
            assertThat(convertedLocation.longitude()).isCloseTo(-122.4194, within(1e-10));
        }

        @Test
        @DisplayName("Should handle poles correctly")
        void toLocation_WithPoleLocation_ShouldHandleCorrectly() {
            // Given
            Location northPole = Location.of(90.0, 0.0);
            ECEFVector vector = ECEFVector.fromLocation(northPole);
            
            // When
            Location convertedLocation = vector.toLocation();
            
            // Then
            assertThat(convertedLocation.latitude()).isCloseTo(90.0, within(1e-10));
            // Longitude at poles is undefined, so we don't test it specifically
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCasesTest {

        @Test
        @DisplayName("Should handle zero vector normalization")
        void normalize_WithZeroVector_ShouldReturnZeroVector() {
            // Given
            ECEFVector zeroVector = ECEFVector.zero();
            
            // When
            ECEFVector normalized = zeroVector.normalize();
            
            // Then
            assertThat(normalized.isZero()).isTrue();
            assertThat(normalized).isEqualTo(ECEFVector.zero());
        }

        @Test
        @DisplayName("Should detect zero vector correctly")
        void isZero_WithZeroVector_ShouldReturnTrue() {
            // Given
            ECEFVector zeroVector = ECEFVector.zero();
            
            // When & Then
            assertThat(zeroVector.isZero()).isTrue();
        }

        @Test
        @DisplayName("Should detect non-zero vector correctly")
        void isZero_WithNonZeroVector_ShouldReturnFalse() {
            // Given
            ECEFVector nonZeroVector = new ECEFVector(1.0, 0.0, 0.0);
            
            // When & Then
            assertThat(nonZeroVector.isZero()).isFalse();
        }
    }

    @Nested
    @DisplayName("String Representation")
    class StringRepresentationTest {

        @Test
        @DisplayName("Should provide readable string representation")
        void toString_ShouldProvideReadableFormat() {
            // Given
            ECEFVector vector = new ECEFVector(1.234567, -2.345678, 3.456789);
            
            // When
            String result = vector.toString();
            
            // Then
            assertThat(result).contains("ECEFVector");
            assertThat(result).contains("1.234567");
            assertThat(result).contains("-2.345678");
            assertThat(result).contains("3.456789");
        }
    }
}
