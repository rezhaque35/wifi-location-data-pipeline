# WiFi Positioning Test Suite - Quick Reference

## Test Execution

```bash
# Run all tests
./scripts/test/run-data-driven-tests.sh

# Run specific test
./scripts/test/run-data-driven-tests.sh --file test-10-combined-all-filters.json

# Run with verbose output (shows request/response)
./scripts/test/run-data-driven-tests.sh --verbose

# Run specific test with verbose output
./scripts/test/run-data-driven-tests.sh --verbose --file test-10-combined-all-filters.json
```

## Test Suite Overview

| # | File | Test Name | Filters Tested | Key Validation |
|---|------|-----------|----------------|----------------|
| 01 | `test-01-top20-with-25-aps.json` | Top 20 with 25 APs | Weak Signal | 25 total → 20 used, 5 discarded |
| 03 | `test-03-top20-exactly-20.json` | Exactly 20 APs | None | 20 total → 20 used, 0 discarded |
| 04 | `test-04-status-filtering-mixed.json` | Mixed statuses | Status | 5 total → 2 used, 3 invalid status |
| 05 | `test-05-status-filtering-all-inactive.json` | All invalid | Status | ERROR - all APs invalid |
| 06 | `test-06-status-filtering-partial.json` | Partial valid | Status | 3 total → 1 used, 2 invalid status |
| 07 | `test-07-cell-tower-within-range.json` | All within range | Cell Tower | 3 total → 3 used, 0 outside range |
| 08 | `test-08-cell-tower-outliers.json` | Some outliers | Cell Tower | 5 total → 4 used, 1 outside range |
| 09 | `test-09-cell-tower-all-outliers.json` | All outliers | Cell Tower | ERROR - all APs outside range |
| 10 | `test-10-combined-all-filters.json` | **All three filters** | **All** | **27 → 17 used, 7 weak + 2 status + 1 cell** |
| 11 | `test-11-combined-top20-status.json` | Top-20 + Status | Weak + Status | 24 total → 20 used, 4 weak signal |
| 12 | `test-12-combined-top20-cell.json` | Top-20 + Cell | Weak + Cell | 23 total → 20 used, 3 weak signal |
| 13 | `test-13-combined-status-cell-effective.json` | Status + Cell | Status + Cell | 7 → 4 used, 2 status + 1 cell |
| 14 | `test-14-insufficient-aps-after-filtering.json` | 2 APs positioning | Status | 5 → 2 used (validates 2-AP support) |

## Filter Categories

### 🔵 Top-20 Signal Filtering
Tests that primarily validate weak signal filtering (keeping only top 20 strongest signals):
- Test 01, 03, 11, 12

### 🟢 Status Filtering  
Tests that validate filtering based on AP status (active/warning vs error/expired/wifi-hotspot):
- Test 04, 05, 06, 13, 14

### 🟡 Cell Tower Filtering
Tests that validate filtering based on distance from cell tower (1500m effective range):
- Test 07, 08, 09, 13

### 🔴 Combined Filtering
Tests that validate multiple filters working together:
- **Test 10** - All three filters (weak signal + status + cell tower)
- **Test 11** - Top-20 + status filtering
- **Test 12** - Top-20 + cell tower filtering  
- **Test 13** - Status + cell tower filtering

## Status Counts Explained

### Filtering Status Values
- `USED` - AP was used in position calculation
- `DISCARDED_WEAK_SIGNAL` - AP discarded (not in top 20 or unknown)
- `DISCARDED_STATUS` - AP has invalid status (error, expired, wifi-hotspot)
- `DISCARDED_CELL_RANGE` - AP outside cell tower effective range

### Example Output
```json
"statusCounts": [
  {"status": "USED", "count": 17},
  {"status": "DISCARDED_WEAK_SIGNAL", "count": 7},
  {"status": "DISCARDED_STATUS", "count": 2},
  {"status": "DISCARDED_CELL_RANGE", "count": 1}
]
```

## Test Data Access Points

### Known APs in Database
- `00:11:22:33:44:01-32` - Active APs at various locations
- `00:11:22:33:44:36` - AP outside cell tower range (~2000m from tower 1002)
- `00:11:22:33:44:41` - Active status
- `00:11:22:33:44:42` - Warning status (valid)
- `00:11:22:33:44:43` - Error status (invalid)
- `00:11:22:33:44:44` - Expired status (invalid)
- `00:11:22:33:44:45` - WiFi-hotspot status (invalid)

### Unknown APs (Not in Database)
- `AA:BB:CC:*` - Various unknown APs for testing

### Cell Towers
- Cell Tower 1002: 37.77505, -122.41955 (range: 1000m + 500m WiFi = 1500m effective)
- Cell Tower 1041: Used in status filtering tests

## Expected Behaviors

### Minimum AP Requirements
- ✅ System works with **1 AP** (proximity method)
- ✅ System works with **2 APs** (RSSI ratio + weighted centroid)
- ✅ System works with **3+ APs** (trilateration + maximum likelihood)

### Valid AP Statuses
- ✅ `active` - Valid for positioning
- ✅ `warning` - Valid for positioning (may have reduced confidence)
- ❌ `error` - Invalid, discarded
- ❌ `expired` - Invalid, discarded  
- ❌ `wifi-hotspot` - Invalid, discarded

### Filtering Order
1. **Top-20 filtering** - Applied first to known APs
2. **Status filtering** - Applied second to remove invalid statuses
3. **Cell tower filtering** - Applied third to remove APs outside range

## Common Test Patterns

### Testing Single Filter
```json
{
  "wifiScanResults": [/* APs designed to trigger specific filter */],
  "cellInfo": [/* Optional: for cell tower tests */],
  "calculationDetail": true
}
```

### Testing Combined Filters
```json
{
  "wifiScanResults": [
    /* Mix of strong/weak signals */,
    /* Mix of valid/invalid statuses */,
    /* Mix of in-range/out-of-range APs */
  ],
  "cellInfo": [/* Cell tower for range filtering */],
  "calculationDetail": true
}
```

### Validating Error Conditions
```json
{
  "expected": {
    "result": "ERROR",
    "message": "Expected error message"
  }
}
```

## Troubleshooting

### Test Fails with Coordinate Mismatch
- Position calculation is deterministic but uses weighted algorithms
- Use coordinate ranges with tolerance (default 0.0002 degrees ~22m)
- Update expected coordinates if algorithm weights change

### Test Fails with Status Count Mismatch
- Check if APs are in database (unknown APs counted as weak signal)
- Verify AP status values in database
- Check cell tower range calculations

### Test Fails with Method Mismatch
- Algorithm selection depends on AP count, signal quality, and geometric distribution
- Update expected methods if algorithm selection logic changes
- Use method arrays to allow multiple valid combinations

## Performance Benchmarks

All tests run in < 50ms per test on typical hardware:
- Simple tests (1-3 APs): ~1-2ms
- Complex tests (20+ APs): ~10-15ms
- Full suite (13 tests): ~5-10 seconds

## Related Documentation

- `/documents/test-suite-review-summary.md` - Detailed review and changes
- `/documents/wifi-positioning-architecture.md` - System architecture
- `/documents/wifi-positioning-implementation-context.md` - Implementation details
- `/README.md` - Project overview and setup

