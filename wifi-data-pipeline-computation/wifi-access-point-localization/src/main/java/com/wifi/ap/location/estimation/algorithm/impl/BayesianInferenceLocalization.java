// wifi-measurements-transformer-service/src/main/java/com/wifi/measurements/transformer/service/impl/DefaultFeedProcessor.java
package com.wifi.ap.location.estimate.algorithm.impl;

import com.wifi.ap.location.estimate.WifiAccessPointLocation;
import com.wifi.ap.location.measurements.WifiMeasurement;
import com.wifi.ap.location.estimate.algorithm.LocalizationAlgorithm;
import org.springframework.stereotype.Service;

import java.util.List;


@Service
public class BayesianInferenceLocalization implements LocalizationAlgorithm {

    public BayesianInferenceLocalization()
    {
    }

    @Override
    public WifiAccessPointLocation estimateLocation(List<WifiMeasurement> locationMeasurements) {
        throw new UnsupportedOperationException("Not supported without current Estimation.");
    }

    @Override
    public WifiAccessPointLocation estimateLocation(List<WifiMeasurement> locationMeasurements, WifiAccessPointLocation currentEstimation) {
        return null;
    }

}
