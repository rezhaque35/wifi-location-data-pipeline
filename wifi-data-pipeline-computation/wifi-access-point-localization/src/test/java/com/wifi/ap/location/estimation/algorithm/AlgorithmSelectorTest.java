package com.wifi.ap.location.estimation.algorithm;

import com.wifi.ap.location.APLocation;
import com.wifi.ap.location.estimation.algorithm.impl.LocalizationAlgorithmType;
import com.wifi.ap.location.estimation.state.APState;
import com.wifi.ap.location.estimation.state.DataMaturityTier;
import com.wifi.ap.location.measurements.WifiMeasurement;
import com.wifi.ap.location.measurements.WifiMeasurements;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AlgorithmSelector Tests")
class AlgorithmSelectorTest {

    private AlgorithmSelector algorithmSelector;

    @BeforeEach
    void setUp() {
        algorithmSelector = new AlgorithmSelector();
    }

    @Nested
    @DisplayName("Select Without Current Location - New AP Tests")
    class SelectWithoutCurrentLocationTest {

        @Test
        @DisplayName("Should return empty for insufficient measurements")
        void select_WhenInsufficientMeasurements_ShouldReturnEmpty() {
            // Given
            List<WifiMeasurement> measurements = createMeasurements(15); // Below 20 threshold
            
            // When
            Optional<AlgorithmSelections> result = algorithmSelector.select(WifiMeasurements.of(measurements));
            
            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should select WCL for bootstrap tier measurements (20-49)")
        void select_WhenBootstrapTierMeasurements_ShouldSelectWCL() {
            // Given
            List<WifiMeasurement> measurements = createMeasurements(30);
            
            // When
            Optional<AlgorithmSelections> result = algorithmSelector.select(WifiMeasurements.of(measurements));
            
            // Then
            assertThat(result).isPresent();
            AlgorithmSelections selections = result.get();
            assertThat(selections.getAlgorithmType()).isEqualTo(LocalizationAlgorithmType.WCL);
            assertThat(selections.getSelectionReasoning())
                .contains("Bootstrap tier")
                .contains("30 measurements")
                .contains("WCL algorithm");
        }

        @Test
        @DisplayName("Should select MLE for mature tier measurements (50-99)")
        void select_WhenMatureTierMeasurements_ShouldSelectMLE() {
            // Given
            List<WifiMeasurement> measurements = createMeasurements(75);
            
            // When
            Optional<AlgorithmSelections> result = algorithmSelector.select(WifiMeasurements.of(measurements));
            
            // Then
            assertThat(result).isPresent();
            AlgorithmSelections selections = result.get();
            assertThat(selections.getAlgorithmType()).isEqualTo(LocalizationAlgorithmType.MLE);
            assertThat(selections.getSelectionReasoning())
                .contains("Mature tier")
                .contains("75 measurements")
                .contains("MLE algorithm");
        }

        @Test
        @DisplayName("Should select MLE for highly mature tier measurements (100+) to establish prior")
        void select_WhenHighlyMatureTierMeasurements_ShouldSelectMLEToEstablishPrior() {
            // Given
            List<WifiMeasurement> measurements = createMeasurements(150);
            
            // When
            Optional<AlgorithmSelections> result = algorithmSelector.select(WifiMeasurements.of(measurements));
            
            // Then
            assertThat(result).isPresent();
            AlgorithmSelections selections = result.get();
            assertThat(selections.getAlgorithmType()).isEqualTo(LocalizationAlgorithmType.MLE);
            assertThat(selections.getSelectionReasoning())
                .contains("Reaching highly mature tier")
                .contains("150 measurements")
                .contains("MLE algorithm to establish prior");
        }
    }

    @Nested
    @DisplayName("Select With Current Location - Existing AP Tests")
    class SelectWithCurrentLocationTest {

        @Test
        @DisplayName("Should select Bayesian when current AP state is highly mature")
        void select_WhenCurrentStateIsHighlyMature_ShouldSelectBayesian() {
            // Given
            List<WifiMeasurement> measurements = createMeasurements(25);
            APLocation currentLocation = createAPLocationWithState(DataMaturityTier.HIGHLY_MATURE);
            
            // When
            Optional<AlgorithmSelections> result = algorithmSelector.select(WifiMeasurements.of(measurements), currentLocation);
            
            // Then
            assertThat(result).isPresent();
            AlgorithmSelections selections = result.get();
            assertThat(selections.getAlgorithmType()).isEqualTo(LocalizationAlgorithmType.BAYESIAN);
            assertThat(selections.getSelectionReasoning())
                .contains("Current state is highly mature")
                .contains("using Bayesian inference")
                .contains("25 new measurements");
        }

        @Test
        @DisplayName("Should fall back to measurement maturity when current state is not highly mature")
        void select_WhenCurrentStateIsNotHighlyMature_ShouldFallBackToMeasurementMaturity() {
            // Given
            List<WifiMeasurement> measurements = createMeasurements(30);
            APLocation currentLocation = createAPLocationWithState(DataMaturityTier.MATURE);
            
            // When
            Optional<AlgorithmSelections> result = algorithmSelector.select(WifiMeasurements.of(measurements), currentLocation);
            
            // Then
            assertThat(result).isPresent();
            AlgorithmSelections selections = result.get();
            assertThat(selections.getAlgorithmType()).isEqualTo(LocalizationAlgorithmType.WCL);
            assertThat(selections.getSelectionReasoning())
                .contains("Bootstrap tier")
                .contains("30 measurements")
                .contains("WCL algorithm");
        }

        @Test
        @DisplayName("Should handle null current location gracefully")
        void select_WhenCurrentLocationIsNull_ShouldFallBackToMeasurementMaturity() {
            // Given
            List<WifiMeasurement> measurements = createMeasurements(30);
            
            // When
            Optional<AlgorithmSelections> result = algorithmSelector.select(WifiMeasurements.of(measurements), null);
            
            // Then
            assertThat(result).isPresent();
            AlgorithmSelections selections = result.get();
            assertThat(selections.getAlgorithmType()).isEqualTo(LocalizationAlgorithmType.WCL);
        }

        @Test
        @DisplayName("Should handle null APState gracefully")
        void select_WhenAPStateIsNull_ShouldFallBackToMeasurementMaturity() {
            // Given
            List<WifiMeasurement> measurements = createMeasurements(30);
            APLocation currentLocation = APLocation.builder().build(); // No APState
            
            // When
            Optional<AlgorithmSelections> result = algorithmSelector.select(WifiMeasurements.of(measurements), currentLocation);
            
            // Then
            assertThat(result).isPresent();
            AlgorithmSelections selections = result.get();
            assertThat(selections.getAlgorithmType()).isEqualTo(LocalizationAlgorithmType.WCL);
        }
    }

    @Nested
    @DisplayName("Boundary Value Tests")
    class BoundaryValueTest {

        @Test
        @DisplayName("Should return empty for exactly 19 measurements")
        void select_WithExactly19Measurements_ShouldReturnEmpty() {
            // Given
            List<WifiMeasurement> measurements = createMeasurements(19);
            
            // When
            Optional<AlgorithmSelections> result = algorithmSelector.select(WifiMeasurements.of(measurements));
            
            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should select WCL for exactly 20 measurements")
        void select_WithExactly20Measurements_ShouldSelectWCL() {
            // Given
            List<WifiMeasurement> measurements = createMeasurements(20);
            
            // When
            Optional<AlgorithmSelections> result = algorithmSelector.select(WifiMeasurements.of(measurements));
            
            // Then
            assertThat(result).isPresent();
            assertThat(result.get().getAlgorithmType()).isEqualTo(LocalizationAlgorithmType.WCL);
        }

        @Test
        @DisplayName("Should select WCL for exactly 49 measurements")
        void select_WithExactly49Measurements_ShouldSelectWCL() {
            // Given
            List<WifiMeasurement> measurements = createMeasurements(49);
            
            // When
            Optional<AlgorithmSelections> result = algorithmSelector.select(WifiMeasurements.of(measurements));
            
            // Then
            assertThat(result).isPresent();
            assertThat(result.get().getAlgorithmType()).isEqualTo(LocalizationAlgorithmType.WCL);
        }

        @Test
        @DisplayName("Should select MLE for exactly 50 measurements")
        void select_WithExactly50Measurements_ShouldSelectMLE() {
            // Given
            List<WifiMeasurement> measurements = createMeasurements(50);
            
            // When
            Optional<AlgorithmSelections> result = algorithmSelector.select(WifiMeasurements.of(measurements));
            
            // Then
            assertThat(result).isPresent();
            assertThat(result.get().getAlgorithmType()).isEqualTo(LocalizationAlgorithmType.MLE);
        }

        @Test
        @DisplayName("Should select MLE for exactly 99 measurements")
        void select_WithExactly99Measurements_ShouldSelectMLE() {
            // Given
            List<WifiMeasurement> measurements = createMeasurements(99);
            
            // When
            Optional<AlgorithmSelections> result = algorithmSelector.select(WifiMeasurements.of(measurements));
            
            // Then
            assertThat(result).isPresent();
            assertThat(result.get().getAlgorithmType()).isEqualTo(LocalizationAlgorithmType.MLE);
        }

        @Test
        @DisplayName("Should select MLE for exactly 100 measurements (to establish prior)")
        void select_WithExactly100Measurements_ShouldSelectMLEToEstablishPrior() {
            // Given
            List<WifiMeasurement> measurements = createMeasurements(100);
            
            // When
            Optional<AlgorithmSelections> result = algorithmSelector.select(WifiMeasurements.of(measurements));
            
            // Then
            assertThat(result).isPresent();
            AlgorithmSelections selections = result.get();
            assertThat(selections.getAlgorithmType()).isEqualTo(LocalizationAlgorithmType.MLE);
            assertThat(selections.getSelectionReasoning()).contains("establish prior");
        }
    }

    @Nested
    @DisplayName("Edge Cases and Error Handling")
    class EdgeCasesTest {

        @Test
        @DisplayName("Should throw exception when measurements are null (no current location)")
        void select_WhenMeasurementsNull_ShouldThrowException() {
            // When & Then
            assertThatThrownBy(() -> algorithmSelector.select(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Measurements cannot be null");
        }

        @Test
        @DisplayName("Should throw exception when measurements are null (with current location)")
        void select_WhenMeasurementsNullWithCurrentLocation_ShouldThrowException() {
            // Given
            APLocation currentLocation = createAPLocationWithState(DataMaturityTier.MATURE);
            
            // When & Then
            assertThatThrownBy(() -> algorithmSelector.select(null, currentLocation))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Measurements cannot be null");
        }

        @Test
        @DisplayName("Should return empty for empty measurements list")
        void select_WhenEmptyMeasurements_ShouldReturnEmpty() {
            // Given
            List<WifiMeasurement> measurements = Collections.emptyList();
            
            // When
            Optional<AlgorithmSelections> result = algorithmSelector.select(WifiMeasurements.of(measurements));
            
            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should handle very large measurement counts")
        void select_WhenVeryLargeMeasurementCount_ShouldSelectMLE() {
            // Given
            List<WifiMeasurement> measurements = createMeasurements(10000);
            
            // When
            Optional<AlgorithmSelections> result = algorithmSelector.select(WifiMeasurements.of(measurements));
            
            // Then
            assertThat(result).isPresent();
            AlgorithmSelections selections = result.get();
            assertThat(selections.getAlgorithmType()).isEqualTo(LocalizationAlgorithmType.MLE);
            assertThat(selections.getSelectionReasoning()).contains("10000 measurements");
        }
    }

    @Nested
    @DisplayName("Algorithm Execution Tests")
    class AlgorithmExecutionTest {

        @Test
        @DisplayName("Should execute WCL algorithm without throwing exceptions")
        void execute_WhenWCLSelected_ShouldExecuteSuccessfully() {
            // Given
            List<WifiMeasurement> measurements = createMeasurements(30);
            
            // When
            Optional<AlgorithmSelections> result = algorithmSelector.select(WifiMeasurements.of(measurements));
            
            // Then
            assertThat(result).isPresent();
            AlgorithmSelections selections = result.get();
            
            // Should not throw when executing
            assertThatCode(selections::execute).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should execute MLE algorithm without throwing exceptions")
        void execute_WhenMLESelected_ShouldExecuteSuccessfully() {
            // Given
            List<WifiMeasurement> measurements = createMeasurements(75);
            
            // When
            Optional<AlgorithmSelections> result = algorithmSelector.select(WifiMeasurements.of(measurements));
            
            // Then
            assertThat(result).isPresent();
            AlgorithmSelections selections = result.get();
            
            // Should not throw when executing
            assertThatCode(selections::execute).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should execute Bayesian algorithm with prior without throwing exceptions")
        void execute_WhenBayesianWithPriorSelected_ShouldExecuteSuccessfully() {
            // Given
            List<WifiMeasurement> measurements = createMeasurements(25);
            APLocation currentLocation = createAPLocationWithState(DataMaturityTier.HIGHLY_MATURE);
            
            // When
            Optional<AlgorithmSelections> result = algorithmSelector.select(WifiMeasurements.of(measurements), currentLocation);
            
            // Then
            assertThat(result).isPresent();
            AlgorithmSelections selections = result.get();
            assertThat(selections.getAlgorithmType()).isEqualTo(LocalizationAlgorithmType.BAYESIAN);
            
            // Should not throw when executing
            assertThatCode(selections::execute).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("Two-Phase Strategy Integration Tests")
    class TwoPhaseStrategyTest {

        @Test
        @DisplayName("Should demonstrate phase transition from build-up to iterative")
        void select_ShouldDemonstratePhaseTransition() {
            // Phase 1: Build-up - First time reaching 100 measurements
            List<WifiMeasurement> measurements100 = createMeasurements(100);
            Optional<AlgorithmSelections> phase1Result = algorithmSelector.select(WifiMeasurements.of(measurements100));
            
            assertThat(phase1Result).isPresent();
            assertThat(phase1Result.get().getAlgorithmType()).isEqualTo(LocalizationAlgorithmType.MLE);
            assertThat(phase1Result.get().getSelectionReasoning()).contains("establish prior");
            
            // Phase 2: Iterative - AP now has highly mature state
            List<WifiMeasurement> newMeasurements = createMeasurements(10);
            APLocation highlyMatureLocation = createAPLocationWithState(DataMaturityTier.HIGHLY_MATURE);
            Optional<AlgorithmSelections> phase2Result = algorithmSelector.select(WifiMeasurements.of(newMeasurements), highlyMatureLocation);
            
            assertThat(phase2Result).isPresent();
            assertThat(phase2Result.get().getAlgorithmType()).isEqualTo(LocalizationAlgorithmType.BAYESIAN);
            assertThat(phase2Result.get().getSelectionReasoning()).contains("highly mature");
        }

        @Test
        @DisplayName("Should prioritize current state maturity over measurement count")
        void select_ShouldPrioritizeCurrentStateMaturityOverMeasurementCount() {
            // Even with insufficient new measurements, if current state is highly mature, use Bayesian
            List<WifiMeasurement> fewMeasurements = createMeasurements(5); // Insufficient
            APLocation highlyMatureLocation = createAPLocationWithState(DataMaturityTier.HIGHLY_MATURE);
            
            // When
            Optional<AlgorithmSelections> result = algorithmSelector.select(WifiMeasurements.of(fewMeasurements), highlyMatureLocation);
            
            // Then
            assertThat(result).isPresent();
            assertThat(result.get().getAlgorithmType()).isEqualTo(LocalizationAlgorithmType.BAYESIAN);
            assertThat(result.get().getSelectionReasoning()).contains("highly mature");
        }
    }

    @Nested
    @DisplayName("Data Maturity Tier Integration Tests")
    class DataMaturityTierIntegrationTest {

        @Test
        @DisplayName("Should correctly align with DataMaturityTier thresholds")
        void select_ShouldAlignWithDataMaturityTierThresholds() {
            // Verify threshold constants match expectations
            assertThat(DataMaturityTier.getInsufficientThreshold()).isEqualTo(20);
            
            // Test insufficient threshold
            assertThat(algorithmSelector.select(WifiMeasurements.of(createMeasurements(19)))).isEmpty();
            assertThat(algorithmSelector.select(WifiMeasurements.of(createMeasurements(20)))).isPresent();
            
            // Test bootstrap to mature transition
            Optional<AlgorithmSelections> bootstrap = algorithmSelector.select(WifiMeasurements.of(createMeasurements(49)));
            Optional<AlgorithmSelections> mature = algorithmSelector.select(WifiMeasurements.of(createMeasurements(50)));
            
            assertThat(bootstrap.get().getAlgorithmType()).isEqualTo(LocalizationAlgorithmType.WCL);
            assertThat(mature.get().getAlgorithmType()).isEqualTo(LocalizationAlgorithmType.MLE);
            
            // Test mature to highly mature transition
            Optional<AlgorithmSelections> stillMature = algorithmSelector.select(WifiMeasurements.of(createMeasurements(99)));
            Optional<AlgorithmSelections> highlyMature = algorithmSelector.select(WifiMeasurements.of(createMeasurements(100)));
            
            assertThat(stillMature.get().getAlgorithmType()).isEqualTo(LocalizationAlgorithmType.MLE);
            assertThat(stillMature.get().getSelectionReasoning()).contains("Mature tier");
            
            assertThat(highlyMature.get().getAlgorithmType()).isEqualTo(LocalizationAlgorithmType.MLE);
            assertThat(highlyMature.get().getSelectionReasoning()).contains("establish prior");
        }
    }

    // Helper methods

    private List<WifiMeasurement> createMeasurements(int count) {
        List<WifiMeasurement> measurements = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            measurements.add(createMockMeasurement(i));
        }
        return measurements;
    }

    private WifiMeasurement createMockMeasurement(int index) {
        return new WifiMeasurement(
            "measurement_" + index,
            "aa:bb:cc:dd:ee:" + String.format("%02x", index % 256),
            System.currentTimeMillis() - (index * 60000L),
            37.7749 + (index * 0.001),
            -122.4194 + (index * 0.001),
            10.0,
            5.0,
            -50 - index,
            2412,
            index % 2 == 0 ? "CONNECTED" : "SCAN",
            index % 2 == 0 ? 2.0 : 1.0,
            100,
            40,
            2412,
            false
        );
    }

    private APLocation createAPLocationWithState(DataMaturityTier maturityTier) {
        APState apState = new APState(
            maturityTier,
            100, // totalMeasurementCount
            95,  // validMeasurementCount
            LocalizationAlgorithmType.MLE, // lastAlgorithmUsed
            "Test reasoning", // algorithmUsedReasoning
            null, // covarianceMatrix
            null, // informationGain
            null, // psoConvergenceAchieved
            null, // psoIterations
            0,    // globalOutlierCount
            0,    // localOutlierCount
            5.0,  // averageLocationAccuracy
            -60.0, // averageRssi
            0.95, // consistencyScore
            Instant.now(), // lastUpdatedTimestamp
            1L    // stateVersion
        );
        
        return APLocation.builder()
            .macAddress("aa:bb:cc:dd:ee:ff")
            .latitude(37.7749)
            .longitude(-122.4194)
            .altitude(10.0)
            .horizontalAccuracy(5.0)
            .confidence(0.95)
            .apState(apState)
            .build();
    }
}