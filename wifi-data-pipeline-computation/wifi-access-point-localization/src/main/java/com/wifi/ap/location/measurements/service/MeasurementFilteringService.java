package com.wifi.ap.location.measurements.service;

import com.wifi.ap.location.estimation.state.APState;
import com.wifi.ap.location.measurements.WifiMeasurement;
import com.wifi.ap.location.measurements.WifiMeasurements;
import com.wifi.ap.location.APLocation;
import com.wifi.ap.location.measurements.outlier.Outliers;
import com.wifi.ap.location.measurements.outlier.global.GlobalOutlierDetector;
import com.wifi.ap.location.measurements.outlier.local.LocalOutlierDetector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import static java.util.function.Predicate.not;

/**
 * Service for filtering WiFi measurements by removing global and local outliers.
 *
 * <p>Implements Section 3.5 of the requirements with sequential outlier processing:
 * 1. Apply global outlier detection (centroid-based with MAD)
 * 2. Apply local outlier detection (LOF algorithm)
 * 3. Combine results and filter measurements
 * 4. Log outlier patterns for analysis
 */
@Service
public class MeasurementFilteringService {

    private static final Logger logger = LoggerFactory.getLogger(MeasurementFilteringService.class);

    private final GlobalOutlierDetector globalOutlierDetector;
    private final LocalOutlierDetector localOutlierDetector;

    public MeasurementFilteringService(
            GlobalOutlierDetector globalOutlierDetector,
            LocalOutlierDetector localOutlierDetector) {
        this.globalOutlierDetector = globalOutlierDetector;
        this.localOutlierDetector = localOutlierDetector;
    }

    /**
     * Filters measurements by removing detected outliers.
     *
     * @param measurements      List of measurements to filter
     * @param currentEstimation Current AP location estimate (unused for now, future enhancement)
     * @return Filtered list of measurements with outliers removed
     */
    public WifiMeasurements filter(WifiMeasurements measurements,
                                        Optional<APLocation> currentEstimation) {

        Outliers globalOutliers = globalOutlierDetector.detectGlobalOutliers(measurements);

        // Filter out global outliers using WifiMeasurements filter method
        WifiMeasurements measurementsWithoutGlobalOutliers = measurements.filter(not(globalOutliers::contains));


        Outliers localOutliers = localOutlierDetector.detectLocalOutliers(measurementsWithoutGlobalOutliers, currentEstimation.map(APLocation::getApState));

        // Filter out local outliers using WifiMeasurements filter method
        WifiMeasurements filteredMeasurements = measurementsWithoutGlobalOutliers.filter(not(localOutliers::contains));


        String summary = String.format(
                "Outlier detection: %d global, %d local, %d total outliers from %d measurements (%.1f%% filtered)",
                globalOutliers.size(), localOutliers.size(), globalOutliers.size() + localOutliers.size(),
                measurements.size(), (double) (globalOutliers.size() + localOutliers.size()) / measurements.size() * 100.0
        );
        logger.info("Outlier detection completed: {}", summary);// Log patterns for analysis

        return filteredMeasurements;
    }




}
