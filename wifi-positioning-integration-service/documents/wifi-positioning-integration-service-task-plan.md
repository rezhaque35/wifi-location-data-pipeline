### WiFi Positioning Integration Service — Implementation Task Plan

#### Milestone 0: Project hygiene ✅ COMPLETED
- [x] Confirm Spring Boot app boots and actuator endpoints are available.
- [x] Add base configuration keys in `application.yml` for positioning base URL, timeouts, processing mode.

#### Milestone 1: API and DTOs ✅ COMPLETED
- [x] Define request DTOs:
  - `IntegrationReportRequest` with fields: `sourceRequest`, `sourceResponse`, `options`, `metadata`.
  - `SampleInterfaceSourceRequest` types for `svcHeader/svcBody/svcReq` (to support the provided sample shape).
  - Normalized internal model for mapping to `WifiPositioningRequest`.
- [x] Define response DTOs:
  - `IntegrationReportResponse` with fields: `correlationId`, `receivedAt`, `processingMode`, `derivedRequest`, `positioningService { httpStatus, latencyMs, response }`, `sourceResponse`, `comparison`.
  - `ComparisonMetrics` with haversine distance, accuracyDelta, confidenceDelta, echoed `methodsUsed`, `apCount`, `calculationTimeMs`.
- [x] Add Bean Validation annotations and unit tests for DTO validation.

#### Milestone 2: Mapping ✅ COMPLETED
- [x] Implement `SampleInterfaceMapper` to derive `WifiPositioningRequest` from the sample interface:
  - Map `clientId -> client`, `requestId -> requestId`.
  - Map each `wifiInfo` to `WifiScanResult(macAddress=id, signalStrength, frequency, ssid?)`.
  - Drop records missing `frequency` if `integration.mapping.drop-missing-frequency=true`.
  - Set `application = "wifi-positioning-integration-service"` and `calculationDetail` per options.
- [x] Negative tests for missing MAC, frequency, or empty wifi list.

#### Milestone 3: Positioning client ✅ COMPLETED
- [x] Create `PositioningServiceClient` using `WebClient` with timeouts from config.
- [x] Method: `invoke(WifiPositioningRequest)` → returns `ClientResult { httpStatus, responseBody, latencyMs }`.
- [x] Handle connection/timeout errors and map to error result; record latency.
- [x] Add tests with mocked `WebClient` (or `MockWebServer`).

#### Milestone 4: Comparison logic ✅ COMPLETED
- [x] Implement `ComparisonService`:
  - Normalize `sourceResponse` into `{ success, latitude, longitude, accuracy, confidence }` when possible.
  - Compute haversine distance when both positions exist.
  - Compute deltas for `accuracy` and `confidence` when available.
- [x] Unit tests with fixture pairs.

#### Milestone 4.1: AP Lookup and Usage Enrichment ✅ COMPLETED
- [x] Implement `AccessPointEnrichmentService`:
  - Extract AP details from `wifi-positioning-service` response `calculationInfo.accessPoints`
  - Produce `apDetails` with: mac, providedSsid?, providedRssi, providedFrequency, found, dbStatus, dbLatitude, dbLongitude, dbAltitude, eligible (status ∈ valid set)
  - Aggregate metrics: `foundApCount`, `notFoundApCount`, `percentRequestFound`, `foundApStatusCounts`, `eligibleApCount`
- [x] Compute `usedApCount` from positioning response `apCount` and derive `percentFoundUsed`, `unknownExclusions = max(eligibleApCount - usedApCount, 0)`
- [x] Unit tests mocking positioning service response to cover found/notFound/status eligibility scenarios

#### Milestone 5: REST Controller ✅ COMPLETED
- [x] Add `IntegrationReportController` at `POST /v1/wifi/position/report`.
- [x] Support `sync` and `async` via `options.processingMode` (default `sync`).
- [x] In `sync`:
  - Validate, map, invoke positioning, compute comparison, log structured event, return full response.
- [x] In `async` (feature-flagged):
  - Enqueue job with `correlationId` and return 202 immediately.
  - Implement in-memory bounded queue and worker threads to process jobs.
  - Log outcomes; do not persist results and do not expose any retrieval endpoint (logs are the source of truth).

#### Milestone 6: Logging and Observability
- [ ] Implement structured logging (`INFO` summary, optional `DEBUG` payloads) including `correlationId`, `requestId`, latency metrics.
- [ ] Include AP enrichment fields and comparison metrics in the structured log event.
- [ ] Mirror actuator configuration from `wifi-positioning-service` (health groups, base path, exposure, swagger config).

#### Milestone 7: Integration Tests ✅ COMPLETED
- [x] Start `wifi-positioning-service` locally.
- [x] Write integration tests that post `IntegrationReportRequest` samples derived from `scripts/test/run-comprehensive-tests.sh` payloads.
- [x] Assert HTTP 200 and presence of fields, including `positioningService.response.result` and `comparison` metrics.
- [x] **SUCCESSFULLY TESTED**: Full end-to-end integration with real positioning service data!

#### Milestone 8: Documentation
- [ ] Document API usage and examples in README and OpenAPI annotations.
- [ ] Include configuration reference and async mode notes.

#### Milestone 9: Optional Enhancements (post-MVP)
- [ ] Retry policy with jitter for transient downstream errors (off by default).
- [ ] Persistent queue or Kafka topic for async mode.



