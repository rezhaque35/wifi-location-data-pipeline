// com/wifi/positioning/dto/IntegrationReportRequestDeserializationTest.java
package com.wifi.positioning.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test to verify that our DTOs can properly deserialize request formats with String timestamps.
 */
class IntegrationReportRequestDeserializationTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        // Configure to ignore unknown properties, just like our @JsonIgnoreProperties annotations
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Test
    void testDeserializeProblematicRequest() throws Exception {
        // This is the exact request format that was failing
        String problematicRequest = """
            {
                "sourceRequest": {
                    "svcHeader": {
                        "authToken": "myauthtoken"
                    },
                    "svcBody": {
                        "svcReq": {
                            "clientId": "GIZMOPAL",
                            "requestId": "17557868316205",
                            "wifiInfo": [
                                {
                                    "id": "00:11:22:33:44:01",
                                    "signalStrength": -65
                                }
                            ],
                            "cellInfo": [
                                {
                                    "id": 120870668,
                                    "broadcastId": 480,
                                    "networkId": 44039,
                                    "cellType": "lte",
                                    "signalStrength": -112
                                }
                            ]
                        }
                    }
                },
                "sourceResponse": {
                    "success": true,
                    "locationInfo": {
                        "latitude": 34.083038,
                        "longitude": -84.253815,
                        "accuracy": 4024.0
                    },
                    "requestId": "17557868316205",
                    "transactionId": "d4f71054-83e0-4c73-8f3e-ceba0b2fb96f"
                },
                "options": {
                    "calculationDetail": true,
                    "processingMode": "async"
                },
                "metadata": {
                    "correlationId": "vlls-bridge-17557868316205",
                    "receivedAt": "2025-08-21T14:33:52.3NZ",
                    "bridgeScript": "vlls-to-integration-service.sh",
                    "vlls": {
                        "requestId": "17557868316205",
                        "clientId": "GIZMOPAL"
                    }
                }
            }
            """;

        // This should now deserialize successfully with String timestamp
        IntegrationReportRequest request = objectMapper.readValue(problematicRequest, IntegrationReportRequest.class);
        
        // Verify the request was deserialized correctly
        assertNotNull(request);
        assertNotNull(request.getSourceRequest());
        assertNotNull(request.getSourceResponse());
        assertNotNull(request.getOptions());
        assertNotNull(request.getMetadata());
        
        // Verify the timestamp was preserved as a String
        assertNotNull(request.getMetadata().getReceivedAt());
        assertEquals("2025-08-21T14:33:52.3NZ", request.getMetadata().getReceivedAt());
        
        // Verify the additional fields are present
        assertEquals("vlls-to-integration-service.sh", request.getMetadata().getBridgeScript());
        assertNotNull(request.getMetadata().getVlls());
        assertEquals("17557868316205", request.getMetadata().getVlls().get("requestId"));
        assertEquals("GIZMOPAL", request.getMetadata().getVlls().get("clientId"));
        
        // Verify the WiFi info
        assertNotNull(request.getSourceRequest().getSvcBody().getSvcReq().getWifiInfo());
        assertEquals(1, request.getSourceRequest().getSvcBody().getSvcReq().getWifiInfo().size());
        assertEquals("00:11:22:33:44:01", request.getSourceRequest().getSvcBody().getSvcReq().getWifiInfo().get(0).getId());
        assertEquals(-65.0, request.getSourceRequest().getSvcBody().getSvcReq().getWifiInfo().get(0).getSignalStrength());
        
        // Verify the cell info
        assertNotNull(request.getSourceRequest().getSvcBody().getSvcReq().getCellInfo());
        assertEquals(1, request.getSourceRequest().getSvcBody().getSvcReq().getCellInfo().size());
        assertEquals(120870668, request.getSourceRequest().getSvcBody().getSvcReq().getCellInfo().get(0).getId());
        assertEquals("lte", request.getSourceRequest().getSvcBody().getSvcReq().getCellInfo().get(0).getCellType());
    }

    @Test
    void testDeserializeWithStandardTimestamp() throws Exception {
        // Test with standard timestamp format
        String standardRequest = """
            {
                "sourceRequest": {
                    "svcHeader": {
                        "authToken": "myauthtoken"
                    },
                    "svcBody": {
                        "svcReq": {
                            "clientId": "GIZMOPAL",
                            "requestId": "17557868316205",
                            "wifiInfo": [
                                {
                                    "id": "00:11:22:33:44:01",
                                    "signalStrength": -65
                                }
                            ]
                        }
                    }
                },
                "metadata": {
                    "correlationId": "test-123",
                    "receivedAt": "2025-08-21T14:33:52.3Z"
                }
            }
            """;

        IntegrationReportRequest request = objectMapper.readValue(standardRequest, IntegrationReportRequest.class);
        
        assertNotNull(request);
        assertNotNull(request.getMetadata().getReceivedAt());
        assertEquals("2025-08-21T14:33:52.3Z", request.getMetadata().getReceivedAt());
    }
}
