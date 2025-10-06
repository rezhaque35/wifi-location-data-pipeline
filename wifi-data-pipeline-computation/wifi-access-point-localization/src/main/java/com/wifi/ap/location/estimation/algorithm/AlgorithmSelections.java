package com.wifi.ap.location.estimate.algorithm;

import com.wifi.ap.location.estimate.WifiAccessPointLocation;
import com.wifi.ap.location.measurements.WifiMeasurement;

import java.util.List;
import java.util.function.Function;

public class AlgorithmSelections {

    List<Function<List<WifiMeasurement>, WifiAccessPointLocation> > selectedAlgorithms;

    public WifiAccessPointLocation execute(List<WifiMeasurement> wifiMeasurements) {

        if (selectedAlgorithms.size() != 1) { throw new IllegalArgumentException("Only one selection supported"); }

        return selectedAlgorithms.getFirst().apply(wifiMeasurements);

    }
}
