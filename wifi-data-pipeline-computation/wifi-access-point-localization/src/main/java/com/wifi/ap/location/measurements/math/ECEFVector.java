package com.wifi.ap.location.measurements.math;

import com.wifi.ap.location.Location;

/**
 * Immutable ECEF (Earth-Centered, Earth-Fixed) vector representation.
 * 
 * <p>This record represents a 3D Cartesian coordinate vector in the ECEF coordinate system,
 * where the origin is at Earth's center, the X-axis points through the intersection of the
 * Equator and Prime Meridian (0°N, 0°E), the Y-axis points through 0°N, 90°E, and the 
 * Z-axis points through the North Pole.
 * 
 * <h2>Mathematical Foundation</h2>
 * 
 * <p><strong>ECEF Conversion from Geographic Coordinates:</strong>
 * <pre>
 * For a point at (latitude, longitude):
 *   lat_rad = latitude * π/180
 *   lon_rad = longitude * π/180
 *   x = cos(lat_rad) * cos(lon_rad)
 *   y = cos(lat_rad) * sin(lon_rad)  
 *   z = sin(lat_rad)
 * </pre>
 * 
 * <p><strong>Vector Operations:</strong>
 * <ul>
 *   <li><strong>Addition:</strong> Component-wise addition of vectors</li>
 *   <li><strong>Scaling:</strong> Multiply each component by a scalar weight</li>
 *   <li><strong>Magnitude:</strong> Euclidean distance from origin</li>
 *   <li><strong>Normalization:</strong> Convert to unit vector (magnitude = 1)</li>
 * </ul>
 * 
 * <p><strong>Immutability:</strong> All operations return new ECEFVector instances,
 * ensuring thread safety and functional programming principles.
 * 
 * @param x X component of the ECEF vector
 * @param y Y component of the ECEF vector  
 * @param z Z component of the ECEF vector
 * 
 * @author WiFi Location Data Pipeline Team
 * @version 1.0
 * @since 1.0
 */
public record ECEFVector(double x, double y, double z) {

    /**
     * Creates an ECEFVector from geographic coordinates.
     * 
     * <p>This factory method converts latitude/longitude coordinates to ECEF unit vectors
     * using spherical trigonometry. The resulting vector represents a point on the unit
     * sphere (radius = 1) centered at Earth's center.
     * 
     * <p><strong>Conversion Formula:</strong>
     * <pre>
     * lat_rad = latitude * π/180
     * lon_rad = longitude * π/180
     * x = cos(lat_rad) * cos(lon_rad)
     * y = cos(lat_rad) * sin(lon_rad)
     * z = sin(lat_rad)
     * </pre>
     * 
     * @param location Geographic location with latitude and longitude in degrees
     * @return ECEFVector representing the location as a unit vector in ECEF coordinates
     * @throws IllegalArgumentException if location is null
     */
    public static ECEFVector fromLocation(Location location) {
        if (location == null) {
            throw new IllegalArgumentException("Location cannot be null");
        }
        
        double latRad = Math.toRadians(location.latitude());
        double lonRad = Math.toRadians(location.longitude());
        
        double x = Math.cos(latRad) * Math.cos(lonRad);
        double y = Math.cos(latRad) * Math.sin(lonRad);
        double z = Math.sin(latRad);
        
        return new ECEFVector(x, y, z);
    }

    /**
     * Creates the zero vector (origin).
     * 
     * @return ECEFVector with all components set to 0.0
     */
    public static ECEFVector zero() {
        return new ECEFVector(0.0, 0.0, 0.0);
    }

    /**
     * Adds another ECEFVector to this vector.
     * 
     * <p>Performs component-wise vector addition:
     * <pre>
     * result.x = this.x + other.x
     * result.y = this.y + other.y
     * result.z = this.z + other.z
     * </pre>
     * 
     * @param other The vector to add to this vector
     * @return New ECEFVector representing the sum of the two vectors
     * @throws IllegalArgumentException if other is null
     */
    public ECEFVector add(ECEFVector other) {
        if (other == null) {
            throw new IllegalArgumentException("Other vector cannot be null");
        }
        
        return new ECEFVector(
            this.x + other.x,
            this.y + other.y,
            this.z + other.z
        );
    }

    /**
     * Scales this vector by a scalar weight.
     * 
     * <p>Multiplies each component by the given weight:
     * <pre>
     * result.x = this.x * weight
     * result.y = this.y * weight
     * result.z = this.z * weight
     * </pre>
     * 
     * @param weight Scalar value to multiply each component by
     * @return New ECEFVector representing the scaled vector
     */
    public ECEFVector scale(double weight) {
        return new ECEFVector(
            this.x * weight,
            this.y * weight,
            this.z * weight
        );
    }

    /**
     * Calculates the magnitude (Euclidean norm) of this vector.
     * 
     * <p>Computes the distance from the origin to this point:
     * <pre>
     * magnitude = √(x² + y² + z²)
     * </pre>
     * 
     * @return The magnitude of the vector as a non-negative double
     */
    public double magnitude() {
        return Math.sqrt(x * x + y * y + z * z);
    }

    /**
     * Normalizes this vector to unit magnitude.
     * 
     * <p>Returns a new vector pointing in the same direction but with magnitude = 1.
     * If this vector has zero magnitude, returns the zero vector.
     * 
     * <p><strong>Normalization Formula:</strong>
     * <pre>
     * magnitude = √(x² + y² + z²)
     * if magnitude > 0:
     *   normalized.x = x / magnitude
     *   normalized.y = y / magnitude
     *   normalized.z = z / magnitude
     * else:
     *   return zero vector
     * </pre>
     * 
     * @return New ECEFVector with unit magnitude, or zero vector if this vector has zero magnitude
     */
    public ECEFVector normalize() {
        double magnitude = magnitude();
        
        if (magnitude == 0.0) {
            return ECEFVector.zero();
        }
        
        return new ECEFVector(
            this.x / magnitude,
            this.y / magnitude,
            this.z / magnitude
        );
    }

    /**
     * Converts this normalized ECEF vector back to geographic coordinates.
     * 
     * <p>This method assumes the vector is normalized (magnitude = 1) and converts
     * it back to latitude/longitude coordinates using inverse spherical trigonometry.
     * 
     * <p><strong>Conversion Formula:</strong>
     * <pre>
     * latitude = arcsin(z) * 180/π
     * longitude = atan2(y, x) * 180/π
     * </pre>
     * 
     * <p><strong>Note:</strong> If this vector is not normalized, the results may not
     * represent valid geographic coordinates. Use {@link #normalize()} first if needed.
     * 
     * @return Location representing the geographic coordinates of this ECEF vector
     */
    public Location toLocation() {
        double latitude = Math.toDegrees(Math.asin(z));
        double longitude = Math.toDegrees(Math.atan2(y, x));
        
        return Location.of(latitude, longitude);
    }

    /**
     * Checks if this vector has zero magnitude (is the origin).
     * 
     * @return true if all components are zero (within floating-point precision), false otherwise
     */
    public boolean isZero() {
        return magnitude() == 0.0;
    }

    /**
     * Returns a string representation of this ECEF vector.
     * 
     * @return String representation in the format "ECEFVector[x=1.0, y=2.0, z=3.0]"
     */
    @Override
    public String toString() {
        return String.format("ECEFVector[x=%.6f, y=%.6f, z=%.6f]", x, y, z);
    }
}
