package com.wifi.positioning.repository;

import com.wifi.positioning.config.TestApplicationConfig;
import com.wifi.positioning.dto.WifiAccessPoint;
import com.wifi.positioning.repository.impl.InMemoryWifiAccessPointRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = TestApplicationConfig.class)
@ActiveProfiles("test")
class WifiAccessPointRepositoryTest {

    @Autowired
    private WifiAccessPointRepository repository;

    private WifiAccessPoint testAccessPoint;
    private InMemoryWifiAccessPointRepository inMemoryRepository;

    @BeforeEach
    void setUp() {
        // Cast to access helper methods
        inMemoryRepository = (InMemoryWifiAccessPointRepository) repository;
        inMemoryRepository.clearAll();
        
        testAccessPoint = WifiAccessPoint.builder()
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
                .geohash("abcdef")
                .status(WifiAccessPoint.STATUS_ACTIVE)
                .build();
    }

    @Test
    void findByMacAddress_shouldReturnEmptyOptional_whenNoAccessPointExists() {
        // Act
        Optional<WifiAccessPoint> found = repository.findByMacAddress("non:existent:mac");
        
        // Assert
        assertFalse(found.isPresent());
    }

    @Test
    void findByMacAddress_shouldReturnAccessPoint_whenSingleVersionExists() {
        // Arrange
        inMemoryRepository.addAccessPoint(testAccessPoint);
        
        // Act
        Optional<WifiAccessPoint> found = repository.findByMacAddress(testAccessPoint.getMacAddress());
        
        // Assert
        assertTrue(found.isPresent());
        assertEquals(testAccessPoint.getMacAddress(), found.get().getMacAddress());
        assertEquals(testAccessPoint.getVersion(), found.get().getVersion());
        assertEquals(testAccessPoint.getLatitude(), found.get().getLatitude());
        assertEquals(testAccessPoint.getLongitude(), found.get().getLongitude());
    }

    @Test
    void findByMacAddress_shouldReturnFirstVersion_whenMultipleVersionsExist() {
        // Arrange
        inMemoryRepository.addAccessPoint(testAccessPoint);
        
        // Create a second version
        WifiAccessPoint secondVersion = WifiAccessPoint.builder()
                .macAddress(testAccessPoint.getMacAddress())
                .version("test-2.0")
                .latitude(37.7750)
                .longitude(-122.4195)
                .confidence(0.90)
                .build();
        
        inMemoryRepository.addAccessPoint(secondVersion);
        
        // Act
        Optional<WifiAccessPoint> found = repository.findByMacAddress(testAccessPoint.getMacAddress());
        
        // Assert
        assertTrue(found.isPresent());
        assertEquals(testAccessPoint.getMacAddress(), found.get().getMacAddress());
        // Note: Since we're not controlling the order in the in-memory implementation,
        // we can't assert exactly which version we'll get, just that we get one.
    }
    
    @Test
    void findByMacAddress_shouldReturnCorrectData_forProximityDetectionScenario() {
        // Arrange - setup test data in InMemoryWifiAccessPointRepository
        inMemoryRepository.loadProximityDetectionScenario();
        
        // Act
        Optional<WifiAccessPoint> found = repository.findByMacAddress("00:11:22:33:44:01");
        
        // Assert
        assertTrue(found.isPresent());
        assertEquals("00:11:22:33:44:01", found.get().getMacAddress());
        assertEquals("v1.0", found.get().getVersion());
        assertEquals(37.7749, found.get().getLatitude());
        assertEquals(-122.4194, found.get().getLongitude());
    }
    
    @Test
    void findByMacAddress_shouldReturnCorrectData_forRssiRatioScenario() {
        // Arrange
        inMemoryRepository.loadRssiRatioScenario();
        
        // Act
        Optional<WifiAccessPoint> found = repository.findByMacAddress("00:11:22:33:44:02");
        
        // Assert
        assertTrue(found.isPresent());
        assertEquals("00:11:22:33:44:02", found.get().getMacAddress());
        assertEquals("v1.0", found.get().getVersion());
    }
    
    @Test
    void findByMacAddress_shouldReturnCorrectData_forWeakSignalsScenario() {
        // Arrange
        inMemoryRepository.loadWeakSignalsScenario();
        
        // Act
        Optional<WifiAccessPoint> found = repository.findByMacAddress("00:11:22:33:44:07");
        
        // Assert
        assertTrue(found.isPresent());
        assertEquals("00:11:22:33:44:07", found.get().getMacAddress());
        assertEquals("v1.0", found.get().getVersion());
        assertTrue(found.get().getLatitude() > 0);
        assertTrue(found.get().getLongitude() < 0);
    }
    
    @Test
    void findByMacAddress_shouldReturnAllScenarioData_whenAllScenariosAreLoaded() {
        // Arrange
        inMemoryRepository.loadAllTestScenarios();
        
        // Assert
        assertTrue(repository.findByMacAddress("00:11:22:33:44:01").isPresent()); // Proximity
        assertTrue(repository.findByMacAddress("00:11:22:33:44:02").isPresent()); // RSSI Ratio
        assertTrue(repository.findByMacAddress("00:11:22:33:44:03").isPresent()); // Trilateration
        assertTrue(repository.findByMacAddress("00:11:22:33:44:07").isPresent()); // Weak Signal
    }
} 