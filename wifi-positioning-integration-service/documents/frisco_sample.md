
*** Request ***
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

*** Response *** 

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
}```
