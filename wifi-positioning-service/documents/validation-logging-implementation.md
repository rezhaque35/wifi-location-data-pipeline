# Validation Logging Implementation

## Overview
This document describes the implementation of comprehensive validation failure logging for the WiFi Positioning Service. When Jakarta Bean Validation fails (returning HTTP 400), the service now logs detailed information to Splunk for troubleshooting and monitoring.

## Problem Statement
Previously, when validation failed, the service would return HTTP 400 to the client, but there was no logging in Splunk to identify:
- Which service request failed
- The request ID that failed
- Why the validation failed
- What fields caused the validation failure

## Solution
Enhanced the `GlobalExceptionHandler` to capture and log validation failures with comprehensive context information.

## Implementation Details

### Modified Files
1. **GlobalExceptionHandler.java**
   - Added SLF4J logger
   - Enhanced `handleValidationExceptions` method to extract request context
   - Added helper methods to extract `requestId` and `client` from the request
   - Added `logValidationFailure` method for structured logging

### Key Features

#### 1. Request Context Extraction
The handler attempts to extract `requestId` and `client` from the `WifiPositioningRequest` object:
```java
private String extractRequestId(MethodArgumentNotValidException ex) {
    try {
        Object target = ex.getBindingResult().getTarget();
        if (target instanceof WifiPositioningRequest request) {
            return request.requestId() != null ? request.requestId() : UNKNOWN_VALUE;
        }
    } catch (Exception e) {
        logger.debug("Unable to extract requestId from validation exception", e);
    }
    return UNKNOWN_VALUE;
}
```

#### 2. Dual Log Format
Two complementary log entries are generated for each validation failure:

**Format 1: Human-readable ERROR log**
```
Validation failure - HTTP_STATUS=400 REQUEST_ID='req-validation-test-001' CLIENT='integration-test-client' PATH='/v1/wifi/position' VALIDATION_ERRORS=[signalStrength='Signal strength must be at least -100 dBm']
```

**Format 2: Structured WARN log (optimized for Splunk)**
```
service=wifi-positioning action=validation_failed status=400 requestId=req-validation-test-001 client=integration-test-client path=/v1/wifi/position errorCount=1 errors=signalStrength='Signal strength must be at least -100 dBm'
```

#### 3. Comprehensive Error Details
The logs include:
- **HTTP Status**: Always 400 for validation failures
- **Request ID**: Extracted from request body (or "UNKNOWN" if unavailable)
- **Client**: Extracted from request body (or "UNKNOWN" if unavailable)
- **Request Path**: The API endpoint that was called
- **Validation Errors**: All field-level validation failures with messages
- **Error Count**: Total number of validation errors

### Splunk Query Examples

To find all validation failures:
```
service=wifi-positioning action=validation_failed
```

To find validation failures for a specific request ID:
```
service=wifi-positioning action=validation_failed requestId=req-123456
```

To find validation failures for a specific client:
```
service=wifi-positioning action=validation_failed client=mobile-app
```

To find validation failures with multiple errors:
```
service=wifi-positioning action=validation_failed errorCount>1
```

To find all HTTP 400 errors:
```
HTTP_STATUS=400
```

## Testing

### Unit Tests (GlobalExceptionHandlerTest.java)
- **should_LogValidationFailureWithRequestIdAndClient_When_ValidationFails**: Verifies logging with requestId and client
- **should_LogUnknownValuesWhenRequestIdNotAvailable_When_ValidationFails**: Verifies "UNKNOWN" is logged when values unavailable
- **should_LogStructuredInformationForSplunk_When_ValidationFails**: Verifies structured logging format

### Integration Tests (ValidationLoggingIntegrationTest.java)
- **should_LogValidationFailureWithRequestIdAndStatus400_When_InvalidSignalStrength**: Real HTTP request with invalid signal strength
- **should_LogValidationFailureForMultipleFields_When_MissingRequiredFields**: Tests multiple validation errors
- **should_LogValidationFailureForInvalidMacAddress_When_InvalidFormat**: Tests MAC address format validation
- **should_LogStructuredDataForSplunkQuerying_When_ValidationFails**: Verifies Splunk-friendly format

All tests pass successfully and verify the logging behavior in both unit and integration scenarios.

## Benefits

1. **Improved Troubleshooting**: Easy to identify which requests failed validation and why
2. **Better Monitoring**: Track validation failure rates by client, request type, or specific fields
3. **Splunk Integration**: Structured logging enables efficient searching and alerting
4. **Request Tracing**: Request ID enables end-to-end request tracking across services
5. **Client Analysis**: Identify which clients are sending invalid requests

## Example Log Output

When a client sends an invalid request with signal strength -150 (below minimum of -100):

```
2025-10-06T11:20:33.024-04:00 ERROR 30494 --- [main] c.w.p.controller.GlobalExceptionHandler : Validation failure - HTTP_STATUS=400 REQUEST_ID='req-validation-test-001' CLIENT='integration-test-client' PATH='/v1/wifi/position' VALIDATION_ERRORS=[wifiScanResults[0].signalStrength='Signal strength must be at least -100 dBm']

2025-10-06T11:20:33.024-04:00 WARN 30494 --- [main] c.w.p.controller.GlobalExceptionHandler : service=wifi-positioning action=validation_failed status=400 requestId=req-validation-test-001 client=integration-test-client path=/v1/wifi/position errorCount=1 errors=wifiScanResults[0].signalStrength='Signal strength must be at least -100 dBm'
```

## Validation Rules Logged

The implementation logs failures for all Jakarta validation rules defined in:
- **WifiPositioningRequest**: requestId, client, wifiScanResults (size, not empty)
- **WifiScanResult**: macAddress format, signalStrength range, frequency range, channel width range

## Performance Considerations

- Logging is asynchronous (default SLF4J/Logback behavior)
- Minimal overhead - only occurs when validation fails (error path)
- Extraction methods use safe guards to prevent exceptions during logging
- Debug-level logging for extraction failures to avoid noise in production logs

## Future Enhancements

Potential improvements for consideration:
1. Add correlation IDs for distributed tracing
2. Include IP address or user agent for additional context
3. Add metrics/counters for validation failure rates by field
4. Create Splunk dashboards for validation failure monitoring
5. Set up alerts for unusual validation failure patterns

## Maintenance Notes

- The `UNKNOWN_VALUE` constant is used consistently for missing context values
- Extraction methods are defensive and will never throw exceptions
- Log formats are stable for Splunk query compatibility
- Both ERROR and WARN logs are retained for different use cases (human vs. machine parsing)

