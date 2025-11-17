package com.wifi.ap.location.estimation.state;

import com.wifi.ap.location.Location;

/**
 * 2x2 covariance matrix for location uncertainty representation.
 * 
 * <p>Used for Bayesian inference and Mahalanobis distance calculations in
 * post-maturity outlier detection and advanced localization algorithms.
 * 
 * <p>Matrix format:
 * <pre>
 * | xx  xy |
 * | yx  yy |
 * </pre>
 * 
 * @param xx Variance in X direction (longitude)
 * @param xy Covariance between X and Y
 * @param yx Covariance between Y and X (should equal xy)
 * @param yy Variance in Y direction (latitude)
 * 
 * @author WiFi Location Data Pipeline Team
 * @version 1.0
 * @since 1.0
 */
public record CovarianceMatrix(double xx, double xy, double yx, double yy) {
    
    /**
     * Creates a diagonal covariance matrix (no correlation).
     * 
     * @param xVariance Variance in X direction
     * @param yVariance Variance in Y direction
     * @return CovarianceMatrix with no correlation
     */
    public static CovarianceMatrix diagonal(double xVariance, double yVariance) {
        return new CovarianceMatrix(xVariance, 0.0, 0.0, yVariance);
    }
    
    /**
     * Creates an identity covariance matrix.
     * 
     * @return CovarianceMatrix representing unit variance with no correlation
     */
    public static CovarianceMatrix identity() {
        return new CovarianceMatrix(1.0, 0.0, 0.0, 1.0);
    }
    
    /**
     * Calculates the determinant of the covariance matrix.
     * 
     * @return Determinant value
     */
    public double determinant() {
        return (xx * yy) - (xy * yx);
    }
    
    /**
     * Calculates the inverse of the covariance matrix.
     * 
     * <p>For a 2x2 matrix [[a,b],[c,d]], the inverse is [[d,-b],[-c,a]]/det
     * where det = ad - bc.
     * 
     * @return Inverse covariance matrix
     * @throws ArithmeticException if matrix is singular (determinant ≈ 0)
     */
    public CovarianceMatrix inverse() {
        double det = determinant();
        if (Math.abs(det) < 1e-10) {
            throw new ArithmeticException("Cannot invert singular covariance matrix (determinant ≈ 0)");
        }
        
        return new CovarianceMatrix(
            yy / det,    // invXX = d/det
            -xy / det,   // invXY = -b/det  
            -yx / det,   // invYX = -c/det
            xx / det     // invYY = a/det
        );
    }
    
    /**
     * Calculates the Mahalanobis distance between two locations using this covariance matrix.
     * 
     * <p>Mahalanobis distance = √[(x-μ)ᵀ Σ⁻¹ (x-μ)]
     * 
     * @param candidate Candidate location
     * @param priorMean Prior mean location
     * @return Mahalanobis distance squared (for efficiency in log-probability calculations)
     */
    public double mahalanobisDistanceSquared(Location candidate, Location priorMean) {
        CovarianceMatrix inverse = inverse();
        
        double deltaLat = candidate.latitude() - priorMean.latitude();
        double deltaLon = candidate.longitude() - priorMean.longitude();
        
        return deltaLat * deltaLat * inverse.xx() + 
               deltaLon * deltaLon * inverse.yy() + 
               2 * deltaLat * deltaLon * inverse.xy();
    }


    public double[][] asArray() {
        return new double[][] {
                {this.xx(), this.xy()},
                {this.yx(), this.yy()}
        };
    }

    /**
     * Calculates the eigenvalues of the covariance matrix.
     * 
     * <p>For a 2x2 symmetric matrix, eigenvalues are:
     * <pre>
     * λ = (tr ± √(tr² - 4·det)) / 2
     * where tr = xx + yy (trace)
     *       det = xx·yy - xy·yx
     * </pre>
     * 
     * @return Array of eigenvalues [λ₁, λ₂] sorted in descending order (largest first)
     */
    public double[] getEigenvalues() {
        double trace = xx + yy;
        double det = determinant();
        
        // For symmetric 2x2 matrix: λ = (tr ± √(tr² - 4·det)) / 2
        double discriminant = trace * trace - 4.0 * det;
        
        if (discriminant < 0) {
            // Should not happen for valid covariance matrix
            discriminant = 0;
        }
        
        double sqrtDiscriminant = Math.sqrt(discriminant);
        double lambda1 = (trace + sqrtDiscriminant) / 2.0;
        double lambda2 = (trace - sqrtDiscriminant) / 2.0;
        
        // Return in descending order (largest first)
        return new double[] {lambda1, lambda2};
    }

    /**
     * Gets the latitude variance (yy component).
     * 
     * @return Variance in latitude direction (meters²)
     */
    public double getLatitudeVariance() {
        return yy;
    }

    /**
     * Gets the longitude variance (xx component).
     * 
     * @return Variance in longitude direction (meters²)
     */
    public double getLongitudeVariance() {
        return xx;
    }
}
