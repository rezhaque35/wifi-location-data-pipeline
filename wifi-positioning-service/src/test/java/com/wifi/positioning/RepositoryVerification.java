package com.wifi.positioning;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.wifi.positioning.dto.WifiAccessPoint;
import com.wifi.positioning.repository.impl.InMemoryWifiAccessPointRepository;

/**
 * Simple standalone verification program for the InMemoryWifiAccessPointRepository implementation.
 * This bypasses the need for Spring and testing frameworks.
 */
public class RepositoryVerification {

  public static void main(String[] args) {
    System.out.println("Verifying InMemoryWifiAccessPointRepository implementation...");

    // Create repository instance
    InMemoryWifiAccessPointRepository repository = new InMemoryWifiAccessPointRepository();

    // Create a test access point
    WifiAccessPoint testAP =
        WifiAccessPoint.builder()
            .macAddress("00:11:22:33:44:55")
            .version("test-1.0")
            .latitude(37.7749)
            .longitude(-122.4194)
            .altitude(10.0)
            .horizontalAccuracy(5.0)
            .verticalAccuracy(2.0)
            .confidence(0.85)
            .ssid("test-ssid")
            .frequency(2437)
            .vendor("test-vendor")
            .geohash("9q8yyk")
            .build();

    // Test basic operations
    verifyAddAndFind(repository, testAP);

    // Test scenario data loading
    verifyScenarioLoading(repository);

    System.out.println("\nAll verifications completed!");
  }

  private static void verifyAddAndFind(
      InMemoryWifiAccessPointRepository repository, WifiAccessPoint testAP) {
    System.out.println("\n== Verifying basic add and find operations ==");

    // Clear any previous data
    repository.clearAll();

    // Verify empty results when nothing added
    Optional<WifiAccessPoint> emptyResult = repository.findByMacAddress("00:11:22:33:44:55");
    System.out.println("Empty result: " + (emptyResult.isEmpty() ? "PASS" : "FAIL"));

    // Add test access point
    repository.addAccessPoint(testAP);

    // Find by MAC address
    Optional<WifiAccessPoint> result = repository.findByMacAddress(testAP.getMacAddress());

    // Verify results
    boolean foundResult = result.isPresent();
    System.out.println("Found result: " + (foundResult ? "PASS" : "FAIL"));

    if (foundResult) {
      boolean correctMac = result.get().getMacAddress().equals(testAP.getMacAddress());
      System.out.println("Correct MAC address: " + (correctMac ? "PASS" : "FAIL"));

      boolean correctVersion = result.get().getVersion().equals(testAP.getVersion());
      System.out.println("Correct version: " + (correctVersion ? "PASS" : "FAIL"));

      boolean correctSSID = result.get().getSsid().equals(testAP.getSsid());
      System.out.println("Correct SSID: " + (correctSSID ? "PASS" : "FAIL"));
    }
  }

  private static void verifyScenarioLoading(InMemoryWifiAccessPointRepository repository) {
    System.out.println("\n== Verifying scenario data loading ==");

    // Clear any previous data
    repository.clearAll();

    // Load proximity detection scenario
    repository.loadProximityDetectionScenario();

    // Verify proximity detection scenario data
    List<WifiAccessPoint> proximityAPList =
        repository
            .findByMacAddress("00:11:22:33:44:01")
            .map(ap -> Collections.singletonList(ap))
            .orElse(Collections.emptyList());

    boolean proximityLoaded = !proximityAPList.isEmpty();
    System.out.println("Proximity scenario loaded: " + (proximityLoaded ? "PASS" : "FAIL"));

    if (proximityLoaded) {
      boolean correctConfidence = proximityAPList.get(0).getConfidence() == 0.65;
      System.out.println("Proximity confidence correct: " + (correctConfidence ? "PASS" : "FAIL"));
    }

    // Load all scenarios
    repository.loadAllTestScenarios();

    // Check number of collinear APs (should be 5)
    int collinearCount = 0;
    for (int i = 6; i <= 10; i++) {
      String macAddress = String.format("00:11:22:33:44:%02d", i);
      Optional<WifiAccessPoint> ap = repository.findByMacAddress(macAddress);
      if (ap.isPresent()) {
        collinearCount++;
      }
    }

    System.out.println(
        "Collinear APs loaded: "
            + (collinearCount == 5 ? "PASS" : "FAIL (" + collinearCount + "/5)"));

    // Check weak signal scenario
    List<WifiAccessPoint> weakSignalAPList =
        repository
            .findByMacAddress("00:11:22:33:44:05")
            .map(ap -> Collections.singletonList(ap))
            .orElse(Collections.emptyList());

    boolean weakSignalLoaded = !weakSignalAPList.isEmpty();
    System.out.println("Weak signal scenario loaded: " + (weakSignalLoaded ? "PASS" : "FAIL"));

    if (weakSignalLoaded) {
      boolean lowConfidence = weakSignalAPList.get(0).getConfidence() < 0.5;
      System.out.println("Weak signal confidence correct: " + (lowConfidence ? "PASS" : "FAIL"));
    }
  }

  private static void verifyBatchRetrievalAndConsistency(
      InMemoryWifiAccessPointRepository repository) {
    System.out.println("\n== Verifying batch retrieval and data consistency ==");

    // Get sample AP data for testing
    Set<String> macAddresses =
        new HashSet<>(Arrays.asList("00:11:22:33:44:01", "00:11:22:33:44:02", "00:11:22:33:44:05"));

    // Load test scenarios
    repository.loadAllTestScenarios();

    // Test batch retrieval
    Map<String, WifiAccessPoint> batchResults = repository.findByMacAddresses(macAddresses);
    System.out.println("Batch size correct: " + (batchResults.size() == 3 ? "PASS" : "FAIL"));

    // Check each AP is in the batch results
    System.out.println(
        "Contains proximity AP: "
            + (batchResults.containsKey("00:11:22:33:44:01") ? "PASS" : "FAIL"));
    System.out.println(
        "Contains RSSI ratio AP: "
            + (batchResults.containsKey("00:11:22:33:44:02") ? "PASS" : "FAIL"));
    System.out.println(
        "Contains weak signal AP: "
            + (batchResults.containsKey("00:11:22:33:44:05") ? "PASS" : "FAIL"));

    // Verify individual lookups match batch lookups
    for (String macAddress : macAddresses) {
      Optional<WifiAccessPoint> individual = repository.findByMacAddress(macAddress);
      WifiAccessPoint batch = batchResults.get(macAddress);

      // Check that they match
      boolean match =
          individual.isPresent()
              && individual.get().getMacAddress().equals(batch.getMacAddress())
              && individual.get().getVersion().equals(batch.getVersion());

      System.out.println("Consistency for " + macAddress + ": " + (match ? "PASS" : "FAIL"));
    }

    // Test batch lookup for non-existent MAC addresses
    Set<String> nonExistentMacs =
        new HashSet<>(Arrays.asList("99:99:99:99:99:99", "88:88:88:88:88:88"));
    Map<String, WifiAccessPoint> emptyResults = repository.findByMacAddresses(nonExistentMacs);
    System.out.println(
        "Empty batch returns empty map: " + (emptyResults.isEmpty() ? "PASS" : "FAIL"));

    // Test mixed batch (existing + non-existent)
    Set<String> mixedMacs = new HashSet<>(Arrays.asList("00:11:22:33:44:01", "99:99:99:99:99:99"));
    Map<String, WifiAccessPoint> mixedResults = repository.findByMacAddresses(mixedMacs);
    System.out.println("Mixed batch size correct: " + (mixedResults.size() == 1 ? "PASS" : "FAIL"));
    System.out.println(
        "Mixed batch contains existing: "
            + (mixedResults.containsKey("00:11:22:33:44:01") ? "PASS" : "FAIL"));
    System.out.println(
        "Mixed batch omits non-existent: "
            + (!mixedResults.containsKey("99:99:99:99:99:99") ? "PASS" : "FAIL"));
  }

  private static void verifyProximityDetectionScenarioData(
      InMemoryWifiAccessPointRepository repository) {
    System.out.println("\n== Verifying proximity detection scenario data ==");

    // Load proximity detection scenario
    repository.loadProximityDetectionScenario();

    // Verify data
    Optional<WifiAccessPoint> proximityAP = repository.findByMacAddress("00:11:22:33:44:01");
    if (proximityAP.isPresent()) {
      System.out.println("Proximity AP found: PASS");
      WifiAccessPoint ap = proximityAP.get();
      System.out.println(
          "MAC address correct: "
              + ("00:11:22:33:44:01".equals(ap.getMacAddress()) ? "PASS" : "FAIL"));
      System.out.println(
          "Has latitude/longitude: "
              + (ap.getLatitude() != null && ap.getLongitude() != null ? "PASS" : "FAIL"));
      System.out.println("Has confidence value: " + (ap.getConfidence() != null ? "PASS" : "FAIL"));
    } else {
      System.out.println("Proximity AP found: FAIL");
    }

    // Test version-specific lookup
    Optional<WifiAccessPoint> versionSpecific =
        repository.findByMacAddressAndVersion("00:11:22:33:44:01", "v1.0");
    System.out.println(
        "Version-specific lookup works: " + (versionSpecific.isPresent() ? "PASS" : "FAIL"));
  }

  private static void verifyWeakSignalsScenarioData(InMemoryWifiAccessPointRepository repository) {
    System.out.println("\n== Verifying weak signals scenario data ==");

    // Load weak signals scenario
    repository.loadWeakSignalsScenario();

    // Verify data
    Optional<WifiAccessPoint> weakSignalAP = repository.findByMacAddress("00:11:22:33:44:05");
    if (weakSignalAP.isPresent()) {
      System.out.println("Weak signal AP found: PASS");
      WifiAccessPoint ap = weakSignalAP.get();
      System.out.println(
          "MAC address correct: "
              + ("00:11:22:33:44:05".equals(ap.getMacAddress()) ? "PASS" : "FAIL"));
      System.out.println(
          "Has latitude/longitude: "
              + (ap.getLatitude() != null && ap.getLongitude() != null ? "PASS" : "FAIL"));
    } else {
      System.out.println("Weak signal AP found: FAIL");
    }
  }

  private static void verifyVersionScenarios(InMemoryWifiAccessPointRepository repository) {
    System.out.println("\n== Verifying version handling ==");

    // Add base version for MAC address
    WifiAccessPoint ap1 =
        WifiAccessPoint.builder()
            .macAddress("00:11:22:33:44:99")
            .version("v1.0")
            .latitude(37.7749)
            .longitude(-122.4194)
            .altitude(10.0)
            .confidence(0.85)
            .ssid("test-version-ap")
            .build();
    repository.save(ap1);

    // Add second version
    WifiAccessPoint ap2 =
        WifiAccessPoint.builder()
            .macAddress("00:11:22:33:44:99")
            .version("v2.0")
            .latitude(37.7750)
            .longitude(-122.4195)
            .altitude(10.5)
            .confidence(0.90)
            .ssid("test-version-ap-updated")
            .build();
    repository.save(ap2);

    // Get by MAC address should return the latest version
    Optional<WifiAccessPoint> latest = repository.findByMacAddress("00:11:22:33:44:99");
    System.out.println("Found latest version: " + (latest.isPresent() ? "PASS" : "FAIL"));

    // Get specific versions
    Optional<WifiAccessPoint> v1 =
        repository.findByMacAddressAndVersion("00:11:22:33:44:99", "v1.0");
    Optional<WifiAccessPoint> v2 =
        repository.findByMacAddressAndVersion("00:11:22:33:44:99", "v2.0");

    System.out.println("Found v1: " + (v1.isPresent() ? "PASS" : "FAIL"));
    System.out.println("Found v2: " + (v2.isPresent() ? "PASS" : "FAIL"));

    // Delete specific version
    repository.delete("00:11:22:33:44:99", "v1.0");

    // Version 1 should be gone
    Optional<WifiAccessPoint> v1After =
        repository.findByMacAddressAndVersion("00:11:22:33:44:99", "v1.0");
    System.out.println("V1 deleted: " + (v1After.isEmpty() ? "PASS" : "FAIL"));

    // Version 2 should still be there
    Optional<WifiAccessPoint> v2After =
        repository.findByMacAddressAndVersion("00:11:22:33:44:99", "v2.0");
    System.out.println("V2 retained: " + (v2After.isPresent() ? "PASS" : "FAIL"));
  }
}
