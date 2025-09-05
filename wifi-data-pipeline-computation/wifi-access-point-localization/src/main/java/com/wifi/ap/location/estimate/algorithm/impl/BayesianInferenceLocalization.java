// wifi-measurements-transformer-service/src/main/java/com/wifi/measurements/transformer/service/impl/DefaultFeedProcessor.java
package com.wifi.ap.location.estimate.processor.impl;

import com.wifi.ap.location.estimate.dto.WifiAccessPointLocation;
import com.wifi.ap.location.estimate.dto.WifiMeasurement;
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
        return null;
    }

    @Override
    public boolean isApplicable(List<WifiMeasurement> locationMeasurements) {
        return false;
    }
}
