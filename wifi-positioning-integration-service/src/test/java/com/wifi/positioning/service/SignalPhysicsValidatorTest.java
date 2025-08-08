package com.wifi.positioning.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.wifi.positioning.dto.WifiScanResult;

@DisplayName("SignalPhysicsValidator Tests")
class SignalPhysicsValidatorTest {

  private SignalPhysicsValidator validator;

  @BeforeEach
  void setUp() {
    validator = new SignalPhysicsValidator();
  }

  @Nested
  @DisplayName("Basic Validation Tests")
  class BasicValidationTests {
    @Test
    @DisplayName("should return false for null input")
    void shouldReturnFalseForNullInput() {
      assertFalse(validator.isPhysicallyPossible(null));
    }

    @Test
    @DisplayName("should return false for empty list")
    void shouldReturnFalseForEmptyList() {
      assertFalse(validator.isPhysicallyPossible(Collections.emptyList()));
    }

    @Test
    @DisplayName("should validate single valid signal")
    void shouldValidateSingleValidSignal() {
      List<WifiScanResult> scanResults =
          Collections.singletonList(WifiScanResult.of("00:11:22:33:44:55", -65.0, 2412, "Test"));
      assertTrue(validator.isPhysicallyPossible(scanResults));
    }
  }

  @Nested
  @DisplayName("Signal Strength Range Tests")
  class SignalStrengthRangeTests {
    @Test
    @DisplayName("should reject signals outside valid range")
    void shouldRejectSignalsOutsideValidRange() {
      // Test signal too strong
      List<WifiScanResult> tooStrong =
          Collections.singletonList(WifiScanResult.of("00:11:22:33:44:55", -29.9, 2412, "Test"));
      assertFalse(validator.isPhysicallyPossible(tooStrong));

      // Test signal too weak
      List<WifiScanResult> tooWeak =
          Collections.singletonList(WifiScanResult.of("00:11:22:33:44:55", -100.1, 2412, "Test"));
      assertFalse(validator.isPhysicallyPossible(tooWeak));
    }

    @Test
    @DisplayName("should accept signals at boundary values")
    void shouldAcceptSignalsAtBoundaryValues() {
      List<WifiScanResult> scanResults =
          Arrays.asList(
              WifiScanResult.of("00:11:22:33:44:55", -30.0, 2412, "Test"),
              WifiScanResult.of("00:11:22:33:44:56", -100.0, 2412, "Test"));
      assertTrue(validator.isPhysicallyPossible(scanResults));
    }
  }

  @Nested
  @DisplayName("Same Frequency Tests")
  class SameFrequencyTests {
    @Test
    @DisplayName("should detect physically impossible signal relationship")
    void shouldDetectPhysicallyImpossibleSignalRelationship() {
      List<WifiScanResult> scanResults =
          Arrays.asList(
              WifiScanResult.of("00:11:22:33:44:55", -40.0, 2412, "Test1"),
              WifiScanResult.of("00:11:22:33:44:56", -95.0, 2412, "Test2"));
      assertFalse(validator.isPhysicallyPossible(scanResults));
    }

    @Test
    @DisplayName("should accept reasonable signal variation")
    void shouldAcceptReasonableSignalVariation() {
      List<WifiScanResult> scanResults =
          Arrays.asList(
              WifiScanResult.of("00:11:22:33:44:55", -60.0, 2412, "Test1"),
              WifiScanResult.of("00:11:22:33:44:56", -75.0, 2412, "Test2"));
      assertTrue(validator.isPhysicallyPossible(scanResults));
    }
  }

  @Nested
  @DisplayName("Different Frequency Tests")
  class DifferentFrequencyTests {
    @Test
    @DisplayName("should accept signal variations across frequencies")
    void shouldAcceptSignalVariationsAcrossFrequencies() {
      List<WifiScanResult> scanResults =
          Arrays.asList(
              WifiScanResult.of("00:11:22:33:44:55", -40.0, 2412, "Test1"),
              WifiScanResult.of("00:11:22:33:44:56", -95.0, 5180, "Test2"));
      assertTrue(validator.isPhysicallyPossible(scanResults));
    }
  }

  @Nested
  @DisplayName("Strong Signal Tests")
  class StrongSignalTests {
    @Test
    @DisplayName("should enforce stricter consistency for strong signals")
    void shouldEnforceStricterConsistencyForStrongSignals() {
      List<WifiScanResult> scanResults =
          Arrays.asList(
              WifiScanResult.of("00:11:22:33:44:55", -45.0, 2412, "Test1"),
              WifiScanResult.of("00:11:22:33:44:56", -75.0, 2412, "Test2"),
              WifiScanResult.of("00:11:22:33:44:57", -80.0, 2412, "Test3"));
      // Within allowable range (45dBm difference)
      assertTrue(validator.isPhysicallyPossible(scanResults));

      List<WifiScanResult> inconsistentSignals =
          Arrays.asList(
              WifiScanResult.of("00:11:22:33:44:55", -45.0, 2412, "Test1"),
              WifiScanResult.of("00:11:22:33:44:56", -95.0, 2412, "Test2"));
      // Exceeds allowable range (50dBm difference)
      assertFalse(validator.isPhysicallyPossible(inconsistentSignals));
    }
  }
}
