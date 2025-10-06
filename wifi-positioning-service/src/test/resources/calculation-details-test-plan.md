# Calculation Details Integration Test Plan

## Overview
This document outlines the integration tests for verifying that `calculationInfo` is properly included/excluded in positioning responses based on the `calculationDetail` flag.

## Test Implementation Status

### ✅ Test Classes Created
1. **`CalculationDetailsIntegrationTest.java`** - Simplified integration test using in-memory repository
2. **`PositioningCalculationDetailsIntegrationTest.java`** - Comprehensive test with nested test classes (has some issues with mock setup)

### ⚠️ Current Issue
**Problem**: `calculationInfo` is always `null` in responses, even when `calculationDetail=true`

**Evidence**: Test responses show:
```json
{
  "result": "SUCCESS",
  "calculationInfo": null  // ← Always null regardless of flag
}
```

**Root Cause**: There appears to be an implementation issue in `PositioningServiceImpl` where `calculationInfo` is not being properly constructed or included in the response when the flag is set.

## Test Scenarios to Verify

### 1. Success Scenarios with calculationDetail=true
**Test**: `shouldIncludeCalculationInfoWhenFlagIsTrue`
- **Setup**: Valid APs in repository, 2-3 scan results
- **Expected**: `calculationInfo` should be present with:
  - `accessPoints` array with AP details (bssid, location, status, usage)
  - `accessPointSummary` with total/used counts and status breakdown
  - `selectionContext` with APCountFactor, signal quality, distribution, GDOP
  - `algorithmSelection` array with algorithms, weights, and selection reasons

### 2. Success Scenarios with calculationDetail=false
**Test**: `shouldExcludeCalculationInfoWhenFlagIsFalse`
- **Setup**: Valid APs in repository, 2-3 scan results
- **Expected**: `calculationInfo` should be `null` or not present

### 3. Success Scenarios with flag not provided
**Test**: `shouldExcludeCalculationInfoWhenFlagNotProvided`
- **Setup**: Valid APs, omit `calculationDetail` from request
- **Expected**: `calculationInfo` should be `null` (default behavior)

### 4. Error Scenario - No APs Found (calculationDetail=true)
**Test**: `shouldIncludePartialInfoWhenNoAPsFoundAndFlagTrue`
- **Setup**: Empty repository or unknown MAC addresses
- **Expected**: 
  - `result`: "ERROR"
  - `message`: "No known access points found in database"
  - `calculationInfo`: `null` (no AP data available to build partial info)

### 5. Error Scenario - No Valid APs (calculationDetail=true)
**Test**: `shouldIncludePartialInfoWhenNoValidAPsAndFlagTrue`
- **Setup**: Only error/expired status APs in repository
- **Expected**:
  - `result`: "ERROR"  
  - `message`: "No valid access points available for positioning"
  - `calculationInfo`: Should be present with:
    - `accessPoints` showing all APs marked as "filtered"
    - `accessPointSummary` with `used=0`
    - `algorithmSelection`: empty array
    - `selectionContext`: present but may have limited data

### 6. Error Scenario with calculationDetail=false
**Test**: `shouldExcludeCalculationInfoWhenFlagFalseAndError`
- **Setup**: No APs in repository
- **Expected**:
  - `result`: "ERROR"
  - `calculationInfo`: `null` (excluded because flag is false)

### 7. Calculation Failure Scenario (calculationDetail=true)
**Test**: `shouldIncludePartialDetailsWhenCalculationFailsAndFlagIsTrue`
- **Setup**: Valid APs but algorithm fails to calculate position
- **Expected**:
  - `result`: "ERROR"
  - `calculationInfo`: Should be present with partial data including:
    - AP information
    - Algorithm selection context
    - What algorithms were attempted

### 8. AP Filtering Details
**Test**: `shouldIncludeAPFilteringDetailsWhenFlagTrue`
- **Setup**: Mix of active, error, expired, wifi-hotspot status APs
- **Expected**: `calculationInfo` should show:
  - Individual APs with their `usage` (used/filtered)
  - Status counts breaking down APs by status
  - Clear indication of which APs were used vs filtered out

### 9. Algorithm Selection Details
**Test**: `shouldIncludeAlgorithmSelectionWhenFlagTrue`
- **Setup**: 3+ APs triggering multiple algorithm selection
- **Expected**: `calculationInfo.algorithmSelection` should show:
  - List of algorithms considered
  - Which were selected (selected=true)
  - Weights assigned to each
  - Reasons for selection/disqualification

### 10. Validation Errors
**Test**: `shouldHandleValidationErrorsWithoutCalculationInfo`
- **Setup**: Empty scan results (validation failure)
- **Expected**:
  - HTTP 400 status
  - `result`: "ERROR"
  - `calculationInfo`: `null` (validation errors bypass normal processing)

## Implementation Checklist

To make these tests pass, verify the following in `PositioningServiceImpl`:

### ✓ Already Implemented (per our changes)
- [x] `buildCalculationInfo()` method exists
- [x] `buildPartialCalculationInfo()` method for error scenarios
- [x] Conditional inclusion based on `request.calculationDetail()`
- [x] Error handlers accept `CalculationInfo` parameter

### ⚠️ Needs Verification
- [ ] `buildSuccessResponse()` actually includes `calculationInfo` when flag is true
- [ ] `WifiPositioningResponse.success()` factory method accepts and uses `calculationInfo`
- [ ] `WifiPositioningResponse.error()` factory method accepts and uses `calculationInfo`
- [ ] JSON serialization properly includes/excludes `calculationInfo`

### 🔍 Debugging Steps

1. **Check Response Construction**:
   - Verify `buildSuccessResponse()` is passing `calculationInfo` to response builder
   - Confirm response DTOs have `calculationInfo` field properly annotated

2. **Check Calculation Info Building**:
   - Add logging to see if `buildCalculationInfo()` is being called
   - Verify it's returning non-null data
   - Check if data is being passed correctly to response builder

3. **Check JSON Serialization**:
   - Verify `@JsonInclude` annotations on `WifiPositioningResponse`
   - Ensure `calculationInfo` field is not accidentally excluded

## Running Tests

```bash
# Run specific test class
mvn test -Dtest=CalculationDetailsIntegrationTest

# Run with verbose output
mvn test -Dtest=CalculationDetailsIntegrationTest -X

# Run all integration tests
mvn test -Dtest="*IntegrationTest"
```

## Expected Test Output

When implementation is correct, you should see:
```
✓ calculationInfo included when flag=true
✓ calculationInfo excluded when flag=false  
✓ calculationInfo excluded when flag not provided
✓ Partial calculationInfo included on error when flag=true
✓ calculationInfo excluded on error when flag=false
✓ AP filtering details included when flag=true
✓ Algorithm selection details included when flag=true
✓ Validation errors handled without calculationInfo

Tests run: 8, Failures: 0, Errors: 0, Skipped: 0
```

## Notes

- Tests use `InMemoryWifiAccessPointRepository` with pre-loaded scenarios
- `@ActiveProfiles("test")` ensures test configuration is used
- Test data scenarios: Proximity Detection, Trilateration, RSSI Ratio, Weak Signals
- All tests should be idempotent and not depend on execution order

