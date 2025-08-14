### WiFi Positioning Integration Service — Requirements

#### 1) Purpose
- Provide a REST endpoint to accept client-provided inputs that include:
  - The original request the client used to compute a position (or will use)
  - The client's own computed response (SUCCESS/FAILURE), if available
- Derive a request for `wifi-positioning-service` from the incoming data, invoke it, measure latency, and collect the result with calculation details.
- Log a side-by-side comparison between the client-reported result and the service-computed result, along with timing and the derived request used. Comparison results are LOGGED ONLY; no retrieval API or persistence for later fetch is required.
- Respond to the caller quickly; support a synchronous mode and a configurable asynchronous mode.

#### 1.1) Service Comparison Context
- **VLSS Service**: External WiFi positioning service that can fall back to cell tower positioning when WiFi APs are not found in database
- **Frisco Service**: Internal `wifi-positioning-service` that only uses WiFi access point positioning
- **Shared Infrastructure**: Both services use the same backend AP location database
- **Key Insight**: When Frisco fails due to "APs not found" but VLSS succeeds, it indicates VLSS used cell tower fallback positioning

#### 2) Scope
- Single REST endpoint to ingest a “comparison report” payload.
- Transformation/mapping from the client’s WiFi scan structure to the `wifi-positioning-service` request format.
- Invocation of `wifi-positioning-service` API: `POST /wifi-positioning-service/api/positioning/calculate` with `WifiPositioningRequest`.
- Comparison logic and structured logging (no storage/retrieval endpoint for comparisons).
- Non-functional requirements aligned with `wifi-positioning-service` (Spring Boot 3.x, Actuator health, structured logs, config-based timeouts, validation).

#### 3) Tech stack and dependencies
- Spring Boot 3.4.x (already configured via Maven parent).
- Spring Web (controller + WebClient for non-blocking HTTP to positioning service).
- Spring Validation (request DTO validation).
- Spring Actuator (health, readiness/liveness endpoints consistent with existing services).
- springdoc-openapi (generate OpenAPI and Swagger UI paths as in other services).

#### 4) API Contract (Integration Service)
- Endpoint: `POST /wifi-positioning-integration-service/api/integration/report`
- Content-Type: `application/json`
- Request payload (high-level):
  - `sourceRequest` (object): The client’s original request. Supports the sample format in `Documents/sample_interface.txt` and a normalized format.
  - `sourceResponse` (object, optional): The client’s computed response (SUCCESS or FAILURE) to be compared against.
  - `options` (object, optional): `{ calculationDetail: boolean, processingMode: "sync"|"async" }`
  - `metadata` (object, optional): `{ correlationId, receivedAt, clientIp, userAgent }`

Example (sample-interface compatible variant):
```json
{
  "sourceRequest": {
    "svcHeader": { "authToken": "MyToken" },
    "svcBody": {
      "svcReq": {
        "clientId": "BLABLA",
        "requestId": "6335bcbf2b914777",
        "wifiInfo": [
          { "id": "88:b4:a6:f6:c3:1a", "signalStrength": -53, "frequency": 2437 },
          { "id": "ec:a9:40:27:fa:20", "signalStrength": -56, "frequency": 2437 }
        ],
        "cellInfo": []
      }
    }
  },
  "sourceResponse": {
    "success": true,
    "locationInfo": { "latitude": 34.090169, "longitude": -84.236067, "accuracy": 100.0 },
    "requestId": "6335bcbf2b914777"
  },
  "options": { "calculationDetail": true, "processingMode": "sync" },
  "metadata": { "correlationId": "abc-123" }
}
```

Validation rules:
- `wifiInfo` must contain at least 1 record with `id` (MAC) and `signalStrength` (double).
- `frequency` is required by `wifi-positioning-service` (2400–6000 MHz). If missing, either:
  - Drop that AP from derived request; or
  - Use a configurable default (e.g., 2412) while flagging `derivedDefaultsUsed=true` in the log. Default behavior: drop missing-frequency APs.
- `clientId` maps to `client`; `requestId` is required and maps through unchanged.
- `cellInfo` may be present but is not passed to Frisco service (logged for analysis).

#### 4.1) Enhanced Request Analysis  
Since both VLSS and Frisco use the same AP location database:
- Always attempt Frisco positioning call with available WiFi APs
- When Frisco returns "APs not found" error but VLSS succeeded, categorize as cell tower fallback scenario
- Log original request composition: WiFi AP count, cell tower count, signal strength ranges
- Track whether VLSS response indicates positioning method used (if available in response)

Derived request to `wifi-positioning-service` (based on its DTOs):
```json
{
  "wifiScanResults": [
    { "macAddress": "88:b4:a6:f6:c3:1a", "signalStrength": -53.0, "frequency": 2437 },
    { "macAddress": "ec:a9:40:27:fa:20", "signalStrength": -56.0, "frequency": 2437 }
  ],
  "client": "BLABLA",
  "requestId": "6335bcbf2b914777",
  "application": "wifi-positioning-integration-service",
  "calculationDetail": true
}
```

Response (sync mode):
- 200 OK with comparison summary:
```json
{
  "result": "SUCCESS",
  "message": "Request processed successfully",
  "requestId": "test-request-21-25",
  "client": "test-client",
  "application": "wifi-positioning-test-suite",
  "timestamp": 1754937643495,
  "wifiPosition": {
    "latitude": 37.778000000000006,
    "longitude": -122.422,
    "altitude": 22,
    "horizontalAccuracy": 51.150000000000006,
    "verticalAccuracy": 0,
    "confidence": 0.3769560187790405,
    "methodsUsed": [
      "weighted_centroid",
      "rssiratio"
    ],
    "apCount": 2,
    "calculationTimeMs": 1
  },
  "calculationInfo": {
    "accessPoints": [
      {
        "bssid": "00:11:22:33:44:21",
        "location": {
          "latitude": 37.778,
          "longitude": -122.422,
          "altitude": 22
        },
        "status": "active",
        "usage": "used"
      },
      {
        "bssid": "00:11:22:33:44:22",
        "location": {
          "latitude": 37.778,
          "longitude": -122.422,
          "altitude": 22
        },
        "status": "active",
        "usage": "used"
      }
    ],
    "accessPointSummary": {
      "total": 2,
      "used": 2,
      "statusCounts": [
        {
          "status": "active",
          "count": 2
        }
      ]
    },
    "selectionContext": {
      "apCountFactor": "TWO_APS",
      "signalQuality": "MEDIUM_SIGNAL",
      "signalDistribution": "UNIFORM_SIGNALS",
      "geometricQuality": "POOR_GDOP"
    },
    "algorithmSelection": [
      {
        "algorithm": "weighted_centroid",
        "selected": true,
        "reasons": [
          "Valid for two APs",
          "SELECTED. Weight Calculation: Weight=1.04: base(0.80) × signal(1.00) × geometric(1.30) × distribution(1.00)"
        ],
        "weight": 1.04
      },
      {
        "algorithm": "trilateration",
        "selected": false,
        "reasons": [
          "DISQUALIFIED (requires at least 3 APs)",
          "DISQUALIFIED (poor geometry)"
        ],
        "weight": null
      },
      {
        "algorithm": "RSSI Ratio",
        "selected": true,
        "reasons": [
          "Valid for two APs",
          "SELECTED. Weight Calculation: Weight=0.86: base(1.00) × signal(0.90) × geometric(0.80) × distribution(1.20)"
        ],
        "weight": 0.8640000000000001
      },
      {
        "algorithm": "proximity",
        "selected": false,
        "reasons": [
          "Valid for two APs",
          "DISQUALIFIED  (below threshold 0.40) . Weight Calculation: Weight=0.28: base(0.40) × signal(0.70) × geometric(1.00) × distribution(1.00)"
        ],
        "weight": null
      },
      {
        "algorithm": "maximum_likelihood",
        "selected": false,
        "reasons": [
          "DISQUALIFIED (requires at least 4 APS)",
          "DISQUALIFIED (poor geometry)"
        ],
        "weight": null
      },
      {
        "algorithm": "log_distance_path_loss",
        "selected": false,
        "reasons": [
          "Valid for two APs",
          "DISQUALIFIED  (below threshold 0.40) . Weight Calculation: Weight=0.31: base(0.50) × signal(0.80) × geometric(0.70) × distribution(1.10)"
        ],
        "weight": null
      }
    ]
  }
}
```

Response (async mode):
- 202 Accepted with minimal ack (processing continues in background; results are LOGGED ONLY):
```json
{ "correlationId": "abc-123", "receivedAt": 1712345678901, "processingMode": "async" }


#### 5) Functional requirements
- Parse and normalize incoming payload; support both the provided sample interface and a normalized schema.
- Validate and map to `WifiPositioningRequest`.
- Invoke `wifi-positioning-service` using non-blocking client, measure precise wall time (`System.nanoTime()`), capture `latencyMs`.
- Log a single structured event containing: incoming payload, derived request, service response, source response, latency, and mapping diagnostics.
- Analyze and categorize the comparison result based on the cross-service scenarios defined in section 8.1.
- Compute comprehensive comparison metrics and positioning method analysis.
- Return sync or async response per `options.processingMode` (default: `sync`).
- Do not persist comparison results and do not provide any REST endpoint to retrieve comparisons; comparisons are recorded via structured logs only.

#### 5.1) Enhanced Comparison Metrics
- **When Both Services Succeed (WiFi positioning)**:
  - Haversine distance between positions (meters)
  - Latitude/longitude deltas  
  - Altitude difference (if available)
  - Accuracy difference (VLSS accuracy - Frisco accuracy)
  - Confidence difference (VLSS confidence - Frisco confidence)
  - Method comparison (VLSS vs Frisco algorithms used)
  - AP count analysis (requested vs. used by each service)

- **When VLSS Succeeds but Frisco Fails (Cell tower fallback detected)**:
  - VLSS position details (with method indication as cell/hybrid)
  - Frisco error details and reason
  - WiFi AP count in request vs. APs found in database
  - Cell tower count and details (if available in request)
  - Flag as "CELL_TOWER_POSITIONING_DETECTED"

- **Performance Metrics for All Scenarios**:
  - Frisco service response time (wall time)
  - Frisco internal calculation time (`calculationTimeMs`)
  - Request transformation time
  - Total integration processing time

- **Data Quality Analysis**:
  - Number of WiFi APs in original request
  - Number of APs with valid frequency data
  - Signal strength distribution (min/max/avg)
  - Number of cell towers (if present)

#### 5.2) Structured Logging Schema
Each comparison event must produce a comprehensive structured log entry containing:

- **Request Context**:
  - `correlationId`, `requestId`, `timestamp`
  - Original VLSS request (sanitized, excluding auth tokens)
  - Transformed Frisco request
  - Request categorization (WIFI_ONLY, CELL_PRESENT, etc.)

- **Service Responses**:
  - VLSS response (full response if provided)
  - Frisco response (including full calculation details when available)
  - Error details for failed services (error codes, messages, reasons)

- **Comparison Analysis**:
  - Scenario classification (from section 8.1)
  - All metrics from section 5.1 
  - Positioning method detection (WIFI vs CELL_FALLBACK)
  - Distance and accuracy comparisons (when applicable)

- **Performance Data**:
  - All timing metrics from section 5.1
  - Network latency vs. computation time breakdown

- **Access Point Intelligence** (from Frisco calculation details when available):
  - Individual AP locations, status, and usage
  - AP summary statistics (total, used, status counts)
  - Algorithm selection details and reasoning
  - Selection context (signal quality, geometric quality, etc.)

- **Diagnostic Information**:
  - Data transformation notes (frequency defaults used, APs dropped, etc.)
  - AP database lookup results (found/not found indicators)
  - Signal quality assessments

#### 6) Non-functional requirements
- Performance: target p95 added latency for sync ≤ 150ms under light load assuming positioning service is local; hard timeout configurable (default 800ms).
- Resilience: timeouts, retries disabled by default (comparison must reflect first response); circuit-breaker optional future.
- Observability: 
  - Actuator liveness/readiness and health groups similar to `wifi-positioning-service`.
  - Structured JSON logs (correlationId, requestId) and INFO-level summaries; DEBUG can include detailed payloads gated by configuration to avoid PII leaks.
- Security: accept and persist `authToken` only as redacted/hash; do not forward to positioning service (not required). Validate payload sizes and sanitize logs.
- Configurability via `application.yml` or env overrides: positioning base URL, timeouts, async enabled, queue sizes (if async), max payload size, drop-or-default frequency strategy.

#### 7) Async processing deliberation (internal queue)
- Synchronous mode pros:
  - Simpler; immediate comparison result returned to caller.
  - Deterministic and easier troubleshooting.
  - Suitable while positioning latency is small and throughput is moderate.
- Synchronous mode cons:
  - Response time includes downstream latency.
  - Less resilient under spikes.
- Asynchronous mode (internal bounded queue + worker pool) pros:
  - Responds immediately with 202; decouples caller from downstream spikes.
  - Enables backpressure and smoothing.
- Asynchronous mode cons:
  - Higher complexity (queue management, persistence if at-least-once delivery is required).
  - Caller cannot get comparison result immediately; and there will be no retrieval endpoint in this iteration (results available via logs only).
- Decision: implement synchronous as default MVP. Provide optional async mode behind a feature flag using an in-memory bounded queue and worker thread pool for best-effort processing. Persistent queue (SQS/Kafka) is out of scope for this iteration and remains a future enhancement.

#### 8) Error handling
- 400 for schema/validation errors (missing `requestId`, empty `wifiInfo`, invalid MAC).
- 200 with positioning error in body is returned by positioning service; we relay that in comparison result and still return 200 in sync mode (since business error is within body).
- 502 if positioning service is unreachable or times out; still log the attempt with error and latency.

#### 8.1) Cross-Service Result Analysis Scenarios
Since both VLSS and Frisco use the same AP location database, result combinations provide key insights:

- **VLSS Success + Frisco Success**: Both found WiFi APs in database
  - Log: "BOTH_WIFI_SUCCESS" 
  - Compare: positions, accuracy, confidence, methods used
  
- **VLSS Success + Frisco Error**: VLSS used cell tower fallback
  - Log: "VLSS_CELL_FALLBACK_DETECTED"
  - Analysis: WiFi APs not in database, VLSS fell back to cell towers
  - Compare: VLSS position vs. no Frisco position, include cell tower count
  
- **VLSS Error + Frisco Error**: Insufficient data for both services
  - Log: "BOTH_INSUFFICIENT_DATA"
  - Analysis: WiFi APs not in database, cell towers also insufficient
  
- **VLSS Error + Frisco Success**: Unexpected scenario (same AP database)
  - Log: "VLSS_ERROR_FRISCO_SUCCESS" 
  - Analysis: Possible VLSS service issue or different filtering logic
  
All scenarios logged with full request/response details, AP counts, cell tower presence, and categorized for positioning method analysis.

#### 9) Configuration keys (proposed)
```yaml
integration:
  positioning:
    base-url: http://localhost:8080/wifi-positioning-service
    path: /api/positioning/calculate
    connect-timeout-ms: 300
    read-timeout-ms: 800
  processing:
    default-mode: sync # or async
    async:
      enabled: false
      queue-capacity: 1000
      workers: 4
  mapping:
    drop-missing-frequency: true
    default-frequency-mhz: 2412
  comparison:
    enable-cell-fallback-detection: true
    log-full-calculation-details: true
    include-ap-location-data: true
    track-positioning-method-analysis: true
    enable-performance-breakdown: true
  logging:
    include-payloads: false
    include-vlss-request: true
    include-frisco-response: true
    include-calculation-details: true
    sanitize-auth-tokens: true
```

#### 10) Compliance with existing services
- Use same actuator exposure, swagger paths, logging levels, and port conventions as other services.
- Maintain consistent package naming under `com.wifi.positioning`.

