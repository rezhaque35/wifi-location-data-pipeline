// wifi-data-pipeline-computation/wifi-access-point-localization/src/main/java/com/wifi/ap/location/estimate/dto/Location.java
package com.wifi.ap.location;

import com.wifi.ap.location.measurements.WifiMeasurement;

/**
 * Represents a geographic location with latitude and longitude coordinates.
 * 
 * <p>This record encapsulates location data and provides methods for geographic calculations,
 * following domain-driven design principles by keeping location-related operations together.
 * 
 * <p>Key features:
 * - Immutable location representation
 * - Distance calculation using Haversine formula
 * - Factory methods for creation from various sources
 * - Type-safe coordinate handling
 */
public record Location(double latitude, double longitude) {
    
    // Earth radius in meters for distance calculations
    private static final double EARTH_RADIUS_METERS = 6_371_000.0;
    
    /**
     * Validates location coordinates during construction.
     */
    public Location {
        if (!isValidLatitude(latitude)) {
            throw new IllegalArgumentException("Invalid latitude: " + latitude + ". Must be between -90 and 90 degrees.");
        }
        if (!isValidLongitude(longitude)) {
            throw new IllegalArgumentException("Invalid longitude: " + longitude + ". Must be between -180 and 180 degrees.");
        }
    }
    
    /**
     * Factory method to create a Location from coordinates.
     * 
     * @param latitude Latitude in degrees (-90 to 90)
     * @param longitude Longitude in degrees (-180 to 180)
     * @return New Location instance
     */
    public static Location of(double latitude, double longitude) {
        return new Location(latitude, longitude);
    }
    
    /**
     * Factory method to create a Location from a WifiMeasurement.
     * 
     * @param measurement WiFi measurement containing location data
     * @return New Location instance
     * @throws IllegalArgumentException if measurement has null coordinates
     */
    public static Location fromMeasurement(WifiMeasurement measurement) {
        if (measurement.latitude() == null || measurement.longitude() == null) {
            throw new IllegalArgumentException("Measurement must have non-null latitude and longitude");
        }
        return new Location(measurement.latitude(), measurement.longitude());
    }
    
    /**
     * Meters per degree of latitude (constant globally).
     * Derived from Earth's mean radius: R × π/180 where R = 6,371,000 m
     * 
     * <p><strong>Source:</strong> Snyder, J.P. (1987). "Map Projections: A Working Manual". USGS Professional Paper 1395
     */
    private static final double METERS_PER_DEGREE_LATITUDE = 111320.0;
    
    /**
     * Calculates the spherical distance to another location using the Haversine formula.
     * 
     * <p>The Haversine formula determines the great-circle distance between two points 
     * on a sphere given their latitude and longitude coordinates.
     * 
     * @param other The target location to calculate distance to
     * @return Distance in meters
     */
    public double distanceTo(Location other) {
        // Convert degrees to radians
        double dLat = Math.toRadians(other.latitude - this.latitude);
        double dLon = Math.toRadians(other.longitude - this.longitude);
        double lat1Rad = Math.toRadians(this.latitude);
        double lat2Rad = Math.toRadians(other.latitude);

        // Haversine formula
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                   Math.sin(dLon / 2) * Math.sin(dLon / 2) * Math.cos(lat1Rad) * Math.cos(lat2Rad);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS_METERS * c;
    }
    
    /**
     * Calculates the spatial gradient vector from this location to another location.
     * 
     * <p>This method computes the gradient ∇f where f is a function of distance,
     * returning the gradient in geographic coordinates (∂f/∂lat, ∂f/∂lon) in degrees.
     * 
     * <p><strong>Use Case:</strong> Fisher Information Matrix calculation, path loss gradients
     * 
     * <p><strong>Mathematical Foundation:</strong>
     * <pre>
     * Given: f(θ) where θ = [lat, lon]ᵀ is this location
     *        m = [lat_m, lon_m]ᵀ is the measurement location
     * 
     * Compute: ∇f = [∂f/∂lat, ∂f/∂lon]ᵀ
     * 
     * Using equirectangular projection for local Cartesian approximation:
     * Δlat_meters = (lat - lat_m) × 111,320
     * Δlon_meters = (lon - lon_m) × 111,320 × cos(lat)
     * 
     * Then apply chain rule:
     * ∂f/∂lat = (∂f/∂distance²) × (∂distance²/∂lat_meters) × (∂lat_meters/∂lat)
     * ∂f/∂lon = (∂f/∂distance²) × (∂distance²/∂lon_meters) × (∂lon_meters/∂lon)
     * </pre>
     * 
     * <p><strong>Geographic Coordinate Conversion Reference:</strong>
     * <ul>
     *   <li>Snyder, J.P. (1987). "Map Projections: A Working Manual". USGS Professional Paper 1395, pp. 5-8</li>
     *   <li>WGS84 Earth ellipsoid model (simplified to sphere for local calculations)</li>
     *   <li>National Geospatial-Intelligence Agency TR8350.2</li>
     * </ul>
     * 
     * <p><strong>Validity:</strong> Accurate for distances < 10km (typical WiFi positioning range)
     * <p><strong>Error:</strong> < 0.5% for distances < 10km at mid-latitudes
     * 
     * @param measurementLocation The measurement location (destination)
     * @param gradientScaleFunction Function that computes ∂f/∂(distance²) given distance² in meters²
     * @return Gradient vector as a Location where latitude = ∂f/∂lat, longitude = ∂f/∂lon (units: per degree)
     * 
     * @throws IllegalArgumentException if locations are identical (distance = 0)
     */
    public Location calculateGradient(Location measurementLocation, 
                                     java.util.function.DoubleUnaryOperator gradientScaleFunction) {
        // Convert to local Cartesian coordinates (equirectangular projection)
        // This is valid for small distances (<10km) typical in WiFi positioning
        double latRadians = Math.toRadians(this.latitude);
        double metersPerDegreeLon = METERS_PER_DEGREE_LATITUDE * Math.cos(latRadians);
        
        // Calculate distance components in meters
        double deltaLatMeters = (this.latitude - measurementLocation.latitude) * METERS_PER_DEGREE_LATITUDE;
        double deltaLonMeters = (this.longitude - measurementLocation.longitude) * metersPerDegreeLon;
        
        // Calculate distance squared in meters²
        double distanceSquared = deltaLatMeters * deltaLatMeters + deltaLonMeters * deltaLonMeters;
        
        if (distanceSquared < 1e-6) { // Less than 1mm distance
            throw new IllegalArgumentException(
                "Cannot calculate gradient: locations are effectively identical (distance < 1mm)");
        }
        
        // Get gradient scale factor: ∂f/∂(distance²)
        double gradientScale = gradientScaleFunction.applyAsDouble(distanceSquared);
        
        // Apply chain rule to get gradient in geographic coordinates (degrees)
        // ∂f/∂lat = (∂f/∂distance²) × (∂distance²/∂lat_meters) × (∂lat_meters/∂lat)
        //         = gradientScale × (2 × deltaLatMeters) × METERS_PER_DEGREE_LATITUDE
        // Simplified: gradientScale × deltaLatMeters × METERS_PER_DEGREE_LATITUDE
        // (factor of 2 is absorbed into gradientScale for efficiency)
        double gradLat = gradientScale * deltaLatMeters * METERS_PER_DEGREE_LATITUDE;
        double gradLon = gradientScale * deltaLonMeters * metersPerDegreeLon;
        
        return new Location(gradLat, gradLon);
    }
    
    /**
     * Checks if this location has valid coordinates.
     * 
     * @return true if both latitude and longitude are valid
     */
    public boolean isValid() {
        return isValidLatitude(latitude) && isValidLongitude(longitude);
    }
    
    /**
     * Validates latitude coordinate.
     * 
     * @param latitude Latitude in degrees
     * @return true if latitude is between -90 and 90 degrees
     */
    private static boolean isValidLatitude(double latitude) {
        return latitude >= -90.0 && latitude <= 90.0;
    }
    
    /**
     * Validates longitude coordinate.
     * 
     * @param longitude Longitude in degrees
     * @return true if longitude is between -180 and 180 degrees
     */
    private static boolean isValidLongitude(double longitude) {
        return longitude >= -180.0 && longitude <= 180.0;
    }
    
    @Override
    public String toString() {
        return String.format("Location[lat=%.6f, lon=%.6f]", latitude, longitude);
    }

    public double [] asArray() { return new double[]{latitude, longitude}; }
}
