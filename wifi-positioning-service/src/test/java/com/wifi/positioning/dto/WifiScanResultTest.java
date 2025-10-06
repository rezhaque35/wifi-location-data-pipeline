package com.wifi.positioning.dto;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

/**
 * Unit tests for WifiScanResult record. Tests validation rules, default values, and factory
 * methods.
 */
@DisplayName("WifiScanResult Tests")
class WifiScanResultTest {

  private Validator validator;

  @BeforeEach
  void setUp() {
    ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
    validator = factory.getValidator();
  }

  @Nested
  @DisplayName("Mandatory Field Validation Tests")
  class MandatoryFieldValidationTests {

    @Test
    @DisplayName("Should pass validation with all required fields")
    void shouldPassValidation_WhenAllRequiredFieldsProvided() {
      // Given
      WifiScanResult result =
          new WifiScanResult("00:11:22:33:44:55", -65.0, null, null, null, null);

      // When
      Set<ConstraintViolation<WifiScanResult>> violations = validator.validate(result);

      // Then
      assertTrue(violations.isEmpty(), "Should have no violations");
      assertEquals("00:11:22:33:44:55", result.macAddress());
      assertEquals(-65.0, result.signalStrength());
    }

    @Test
    @DisplayName("Should fail validation when MAC address is null")
    void shouldFailValidation_WhenMacAddressIsNull() {
      // Given
      WifiScanResult result = new WifiScanResult(null, -65.0, 2437, null, null, null);

      // When
      Set<ConstraintViolation<WifiScanResult>> violations = validator.validate(result);

      // Then
      assertFalse(violations.isEmpty(), "Should have violations for null MAC address");
      assertTrue(
          violations.stream()
              .anyMatch(
                  v ->
                      v.getPropertyPath().toString().equals("macAddress")
                          && v.getMessage().contains("MAC address is required")));
    }

    @Test
    @DisplayName("Should fail validation when MAC address is blank")
    void shouldFailValidation_WhenMacAddressIsBlank() {
      // Given
      WifiScanResult result = new WifiScanResult("", -65.0, 2437, null, null, null);

      // When
      Set<ConstraintViolation<WifiScanResult>> violations = validator.validate(result);

      // Then
      assertFalse(violations.isEmpty(), "Should have violations for blank MAC address");
      assertTrue(
          violations.stream()
              .anyMatch(
                  v ->
                      v.getPropertyPath().toString().equals("macAddress")
                          && v.getMessage().contains("MAC address is required")));
    }

    @Test
    @DisplayName("Should fail validation when MAC address format is invalid")
    void shouldFailValidation_WhenMacAddressFormatIsInvalid() {
      // Given
      WifiScanResult result = new WifiScanResult("invalid-mac", -65.0, 2437, null, null, null);

      // When
      Set<ConstraintViolation<WifiScanResult>> violations = validator.validate(result);

      // Then
      assertFalse(violations.isEmpty(), "Should have violations for invalid MAC format");
      assertTrue(
          violations.stream()
              .anyMatch(
                  v ->
                      v.getPropertyPath().toString().equals("macAddress")
                          && v.getMessage().contains("Invalid MAC address format")));
    }

    @Test
    @DisplayName("Should fail validation when signal strength is null")
    void shouldFailValidation_WhenSignalStrengthIsNull() {
      // Given
      WifiScanResult result = new WifiScanResult("00:11:22:33:44:55", null, 2437, null, null, null);

      // When
      Set<ConstraintViolation<WifiScanResult>> violations = validator.validate(result);

      // Then
      assertFalse(violations.isEmpty(), "Should have violations for null signal strength");
      assertTrue(
          violations.stream()
              .anyMatch(
                  v ->
                      v.getPropertyPath().toString().equals("signalStrength")
                          && v.getMessage().contains("Signal strength is required")));
    }

    @Test
    @DisplayName("Should fail validation when signal strength is below -100 dBm")
    void shouldFailValidation_WhenSignalStrengthBelowMinimum() {
      // Given
      WifiScanResult result =
          new WifiScanResult("00:11:22:33:44:55", -101.0, 2437, null, null, null);

      // When
      Set<ConstraintViolation<WifiScanResult>> violations = validator.validate(result);

      // Then
      assertFalse(violations.isEmpty(), "Should have violations for signal strength < -100");
      assertTrue(
          violations.stream()
              .anyMatch(
                  v ->
                      v.getPropertyPath().toString().equals("signalStrength")
                          && v.getMessage().contains("at least -100 dBm")));
    }

    @Test
    @DisplayName("Should fail validation when signal strength is above 0 dBm")
    void shouldFailValidation_WhenSignalStrengthAboveMaximum() {
      // Given
      WifiScanResult result =
          new WifiScanResult("00:11:22:33:44:55", 1.0, 2437, null, null, null);

      // When
      Set<ConstraintViolation<WifiScanResult>> violations = validator.validate(result);

      // Then
      assertFalse(violations.isEmpty(), "Should have violations for signal strength > 0");
      assertTrue(
          violations.stream()
              .anyMatch(
                  v ->
                      v.getPropertyPath().toString().equals("signalStrength")
                          && v.getMessage().contains("at most 0 dBm")));
    }

    @Test
    @DisplayName("Should accept valid MAC address with colon separator")
    void shouldAcceptValidMacAddress_WithColonSeparator() {
      // Given
      WifiScanResult result =
          new WifiScanResult("AA:BB:CC:DD:EE:FF", -65.0, 2437, null, null, null);

      // When
      Set<ConstraintViolation<WifiScanResult>> violations = validator.validate(result);

      // Then
      assertTrue(violations.isEmpty(), "Should accept MAC address with colon separator");
    }

    @Test
    @DisplayName("Should accept valid MAC address with hyphen separator")
    void shouldAcceptValidMacAddress_WithHyphenSeparator() {
      // Given
      WifiScanResult result =
          new WifiScanResult("AA-BB-CC-DD-EE-FF", -65.0, 2437, null, null, null);

      // When
      Set<ConstraintViolation<WifiScanResult>> violations = validator.validate(result);

      // Then
      assertTrue(violations.isEmpty(), "Should accept MAC address with hyphen separator");
    }
  }

  @Nested
  @DisplayName("Default Value Tests")
  class DefaultValueTests {

    @Test
    @DisplayName("Should set frequency to 2400 MHz when null")
    void shouldSetDefaultFrequency_WhenNull() {
      // Given/When
      WifiScanResult result =
          new WifiScanResult("00:11:22:33:44:55", -65.0, null, null, null, null);

      // Then
      assertEquals(2400, result.frequency(), "Frequency should default to 2400 MHz");
    }

    @Test
    @DisplayName("Should preserve provided frequency when not null")
    void shouldPreserveProvidedFrequency_WhenNotNull() {
      // Given/When
      WifiScanResult result =
          new WifiScanResult("00:11:22:33:44:55", -65.0, 5180, null, null, null);

      // Then
      assertEquals(5180, result.frequency(), "Frequency should be preserved as 5180 MHz");
    }

    @Test
    @DisplayName("Should allow null values for optional fields")
    void shouldAllowNullValues_ForOptionalFields() {
      // Given/When
      WifiScanResult result =
          new WifiScanResult("00:11:22:33:44:55", -65.0, null, null, null, null);

      // Then
      assertEquals(2400, result.frequency(), "Frequency should default to 2400");
      assertNull(result.ssid(), "SSID should be null");
      assertNull(result.linkSpeed(), "Link speed should be null");
      assertNull(result.channelWidth(), "Channel width should be null");
    }
  }

  @Nested
  @DisplayName("Optional Field Validation Tests")
  class OptionalFieldValidationTests {

    @Test
    @DisplayName("Should pass validation when frequency is within valid range")
    void shouldPassValidation_WhenFrequencyInRange() {
      // Given
      WifiScanResult result =
          new WifiScanResult("00:11:22:33:44:55", -65.0, 2437, null, null, null);

      // When
      Set<ConstraintViolation<WifiScanResult>> violations = validator.validate(result);

      // Then
      assertTrue(violations.isEmpty(), "Should have no violations for valid frequency");
    }

    @Test
    @DisplayName("Should fail validation when frequency is below minimum")
    void shouldFailValidation_WhenFrequencyBelowMinimum() {
      // Given
      WifiScanResult result =
          new WifiScanResult("00:11:22:33:44:55", -65.0, 2399, null, null, null);

      // When
      Set<ConstraintViolation<WifiScanResult>> violations = validator.validate(result);

      // Then
      assertFalse(violations.isEmpty(), "Should have violations for frequency < 2400");
      assertTrue(
          violations.stream()
              .anyMatch(
                  v ->
                      v.getPropertyPath().toString().equals("frequency")
                          && v.getMessage().contains("at least 2400 MHz")));
    }

    @Test
    @DisplayName("Should fail validation when frequency is above maximum")
    void shouldFailValidation_WhenFrequencyAboveMaximum() {
      // Given
      WifiScanResult result =
          new WifiScanResult("00:11:22:33:44:55", -65.0, 6001, null, null, null);

      // When
      Set<ConstraintViolation<WifiScanResult>> violations = validator.validate(result);

      // Then
      assertFalse(violations.isEmpty(), "Should have violations for frequency > 6000");
      assertTrue(
          violations.stream()
              .anyMatch(
                  v ->
                      v.getPropertyPath().toString().equals("frequency")
                          && v.getMessage().contains("at most 6000 MHz")));
    }

    @Test
    @DisplayName("Should pass validation when SSID is null")
    void shouldPassValidation_WhenSsidIsNull() {
      // Given
      WifiScanResult result =
          new WifiScanResult("00:11:22:33:44:55", -65.0, 2437, null, null, null);

      // When
      Set<ConstraintViolation<WifiScanResult>> violations = validator.validate(result);

      // Then
      assertTrue(violations.isEmpty(), "Should have no violations when SSID is null");
    }

    @Test
    @DisplayName("Should pass validation with valid SSID")
    void shouldPassValidation_WithValidSsid() {
      // Given
      WifiScanResult result =
          new WifiScanResult("00:11:22:33:44:55", -65.0, 2437, "TestNetwork", null, null);

      // When
      Set<ConstraintViolation<WifiScanResult>> violations = validator.validate(result);

      // Then
      assertTrue(violations.isEmpty(), "Should have no violations with valid SSID");
      assertEquals("TestNetwork", result.ssid());
    }

    @Test
    @DisplayName("Should fail validation when channel width is below minimum")
    void shouldFailValidation_WhenChannelWidthBelowMinimum() {
      // Given
      WifiScanResult result =
          new WifiScanResult("00:11:22:33:44:55", -65.0, 2437, null, null, 19);

      // When
      Set<ConstraintViolation<WifiScanResult>> violations = validator.validate(result);

      // Then
      assertFalse(violations.isEmpty(), "Should have violations for channel width < 20");
      assertTrue(
          violations.stream()
              .anyMatch(
                  v ->
                      v.getPropertyPath().toString().equals("channelWidth")
                          && v.getMessage().contains("at least 20 MHz")));
    }

    @Test
    @DisplayName("Should fail validation when channel width is above maximum")
    void shouldFailValidation_WhenChannelWidthAboveMaximum() {
      // Given
      WifiScanResult result =
          new WifiScanResult("00:11:22:33:44:55", -65.0, 2437, null, null, 161);

      // When
      Set<ConstraintViolation<WifiScanResult>> violations = validator.validate(result);

      // Then
      assertFalse(violations.isEmpty(), "Should have violations for channel width > 160");
      assertTrue(
          violations.stream()
              .anyMatch(
                  v ->
                      v.getPropertyPath().toString().equals("channelWidth")
                          && v.getMessage().contains("at most 160 MHz")));
    }

    @Test
    @DisplayName("Should pass validation with valid channel width values")
    void shouldPassValidation_WithValidChannelWidth() {
      // Test common channel width values: 20, 40, 80, 160
      int[] validWidths = {20, 40, 80, 160};

      for (int width : validWidths) {
        // Given
        WifiScanResult result =
            new WifiScanResult("00:11:22:33:44:55", -65.0, 2437, null, null, width);

        // When
        Set<ConstraintViolation<WifiScanResult>> violations = validator.validate(result);

        // Then
        assertTrue(
            violations.isEmpty(),
            "Should have no violations for channel width " + width + " MHz");
        assertEquals(width, result.channelWidth());
      }
    }

    @Test
    @DisplayName("Should fail validation when link speed is negative")
    void shouldFailValidation_WhenLinkSpeedIsNegative() {
      // Given
      WifiScanResult result =
          new WifiScanResult("00:11:22:33:44:55", -65.0, 2437, null, -1, null);

      // When
      Set<ConstraintViolation<WifiScanResult>> violations = validator.validate(result);

      // Then
      assertFalse(violations.isEmpty(), "Should have violations for negative link speed");
      assertTrue(
          violations.stream()
              .anyMatch(
                  v ->
                      v.getPropertyPath().toString().equals("linkSpeed")
                          && v.getMessage().contains("non-negative")));
    }

    @Test
    @DisplayName("Should pass validation with valid link speed")
    void shouldPassValidation_WithValidLinkSpeed() {
      // Given
      WifiScanResult result =
          new WifiScanResult("00:11:22:33:44:55", -65.0, 2437, null, 54, null);

      // When
      Set<ConstraintViolation<WifiScanResult>> violations = validator.validate(result);

      // Then
      assertTrue(violations.isEmpty(), "Should have no violations with valid link speed");
      assertEquals(54, result.linkSpeed());
    }
  }

  @Nested
  @DisplayName("Factory Method Tests")
  class FactoryMethodTests {

    @Test
    @DisplayName("Should create instance with four-parameter factory method")
    void shouldCreateInstance_WithFourParameterFactory() {
      // Given/When
      WifiScanResult result = WifiScanResult.of("00:11:22:33:44:55", -65.0, 2437, "TestAP");

      // Then
      assertEquals("00:11:22:33:44:55", result.macAddress());
      assertEquals(-65.0, result.signalStrength());
      assertEquals(2437, result.frequency());
      assertEquals("TestAP", result.ssid());
      assertNull(result.linkSpeed(), "Link speed should be null");
      assertNull(result.channelWidth(), "Channel width should be null");
    }

    @Test
    @DisplayName("Should create instance with six-parameter factory method")
    void shouldCreateInstance_WithSixParameterFactory() {
      // Given/When
      WifiScanResult result =
          WifiScanResult.of("00:11:22:33:44:55", -65.0, 2437, "TestAP", 54, 40);

      // Then
      assertEquals("00:11:22:33:44:55", result.macAddress());
      assertEquals(-65.0, result.signalStrength());
      assertEquals(2437, result.frequency());
      assertEquals("TestAP", result.ssid());
      assertEquals(54, result.linkSpeed());
      assertEquals(40, result.channelWidth());
    }

    @Test
    @DisplayName("Should apply default frequency when using factory with null frequency")
    void shouldApplyDefaultFrequency_WhenUsingFactoryWithNullFrequency() {
      // Given/When
      WifiScanResult result =
          new WifiScanResult("00:11:22:33:44:55", -65.0, null, "TestAP", null, null);

      // Then
      assertEquals(2400, result.frequency(), "Frequency should default to 2400 MHz");
    }
  }

  @Nested
  @DisplayName("Comprehensive Validation Tests")
  class ComprehensiveValidationTests {

    @Test
    @DisplayName("Should pass validation with all fields populated correctly")
    void shouldPassValidation_WithAllFieldsPopulated() {
      // Given
      WifiScanResult result =
          new WifiScanResult("AA:BB:CC:DD:EE:FF", -50.5, 5180, "Office-Network", 866, 80);

      // When
      Set<ConstraintViolation<WifiScanResult>> violations = validator.validate(result);

      // Then
      assertTrue(violations.isEmpty(), "Should have no violations with all valid fields");
      assertEquals("AA:BB:CC:DD:EE:FF", result.macAddress());
      assertEquals(-50.5, result.signalStrength());
      assertEquals(5180, result.frequency());
      assertEquals("Office-Network", result.ssid());
      assertEquals(866, result.linkSpeed());
      assertEquals(80, result.channelWidth());
    }

    @Test
    @DisplayName("Should pass validation with only mandatory fields")
    void shouldPassValidation_WithOnlyMandatoryFields() {
      // Given
      WifiScanResult result =
          new WifiScanResult("00:11:22:33:44:55", -65.0, null, null, null, null);

      // When
      Set<ConstraintViolation<WifiScanResult>> violations = validator.validate(result);

      // Then
      assertTrue(violations.isEmpty(), "Should have no violations with only mandatory fields");
      assertEquals("00:11:22:33:44:55", result.macAddress());
      assertEquals(-65.0, result.signalStrength());
      assertEquals(2400, result.frequency(), "Should have default frequency");
    }

    @Test
    @DisplayName("Should handle edge case signal strength values correctly")
    void shouldHandleEdgeCaseSignalStrength() {
      // Test boundary values
      WifiScanResult weakSignal =
          new WifiScanResult("00:11:22:33:44:55", -100.0, 2437, null, null, null);
      WifiScanResult strongSignal =
          new WifiScanResult("00:11:22:33:44:56", 0.0, 2437, null, null, null);

      // When
      Set<ConstraintViolation<WifiScanResult>> weakViolations = validator.validate(weakSignal);
      Set<ConstraintViolation<WifiScanResult>> strongViolations = validator.validate(strongSignal);

      // Then
      assertTrue(weakViolations.isEmpty(), "Should accept -100 dBm");
      assertTrue(strongViolations.isEmpty(), "Should accept 0 dBm");
    }

    @Test
    @DisplayName("Should handle edge case frequency values correctly")
    void shouldHandleEdgeCaseFrequency() {
      // Test boundary values
      WifiScanResult lowFreq =
          new WifiScanResult("00:11:22:33:44:55", -65.0, 2400, null, null, null);
      WifiScanResult highFreq =
          new WifiScanResult("00:11:22:33:44:56", -65.0, 6000, null, null, null);

      // When
      Set<ConstraintViolation<WifiScanResult>> lowViolations = validator.validate(lowFreq);
      Set<ConstraintViolation<WifiScanResult>> highViolations = validator.validate(highFreq);

      // Then
      assertTrue(lowViolations.isEmpty(), "Should accept 2400 MHz");
      assertTrue(highViolations.isEmpty(), "Should accept 6000 MHz");
    }
  }
}
