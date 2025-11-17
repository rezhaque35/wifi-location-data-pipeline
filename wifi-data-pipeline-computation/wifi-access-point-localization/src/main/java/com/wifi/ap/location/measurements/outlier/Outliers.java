package com.wifi.ap.location.measurements.outlier;

import com.wifi.ap.location.measurements.WifiMeasurement;

import java.util.Set;

public record Outliers(Set<String> outliers) {
    public boolean contains(WifiMeasurement measurement) {
        return this.outliers.contains(measurement.id());
    }

    public int size() {
        return this.outliers.size();
    }
}
