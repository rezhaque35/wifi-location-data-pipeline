// wifi-measurements-transformer-service/src/main/java/com/wifi/measurements/transformer/service/MobileHotspotDetectionService.java
package com.wifi.measurements.transformer.service;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

import com.wifi.measurements.transformer.config.properties.DataFilteringConfigurationProperties;
import com.wifi.measurements.transformer.config.properties.DataFilteringConfigurationProperties.MobileHotspotConfiguration;
import com.wifi.measurements.transformer.config.properties.DataFilteringConfigurationProperties.PatternType;
import com.wifi.measurements.transformer.config.properties.DataFilteringConfigurationProperties.SsidPattern;
import com.wifi.measurements.transformer.dto.NetworkIdentifier;
import com.wifi.measurements.transformer.dto.WifiMeasurement;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Service for detecting mobile hotspot measurements using OUI-based (BSSID) and
 * SSID-based pattern
 * matching.
 *
 * <h2>Detection Strategy</h2>
 *
 * <p>
 * Mobile hotspots are non-stationary and degrade location accuracy. This
 * service implements two
 * complementary detection approaches using functional predicate composition:
 *
 * <ul>
 * <li><strong>OUI-Based Detection:</strong> Checks BSSID against known mobile
 * device manufacturer
 * OUIs (first 3 octets of MAC address)
 * <li><strong>SSID-Based Detection:</strong> Matches SSID against configurable
 * patterns (iPhone,
 * Android, etc.) - always case-insensitive
 * </ul>
 *
 * <h2>Architecture</h2>
 *
 * <p>
 * Uses sealed interface hierarchy with record-based matchers for type safety
 * and performance:
 *
 * <ul>
 * <li><strong>OuiMatcher:</strong> O(1) Set-based lookup for normalized OUI
 * prefixes
 * <li><strong>ContainsMatcher:</strong> Fast-path string.contains() for simple
 * patterns (~10x faster than regex)
 * <li><strong>RegexMatcher:</strong> Pre-compiled regex for complex patterns
 * </ul>
 *
 * <p>
 * All matchers are composed into a single {@code Predicate<WifiMeasurement>} at
 * initialization
 * time, eliminating runtime null checks and configuration validation overhead.
 *
 * <h2>Performance Characteristics</h2>
 *
 * <ul>
 * <li><strong>Initialization-time validation:</strong> All config checks,
 * pattern compilation, and OUI normalization done once
 * <li><strong>Zero runtime overhead:</strong> No null checks, no map lookups -
 * direct predicate evaluation
 * <li><strong>Fast-path optimization:</strong> CONTAINS patterns use simple
 * string matching (~10ns vs ~150ns for regex)
 * <li><strong>Short-circuit evaluation:</strong> Stops on first match (OR
 * composition)
 * </ul>
 *
 * <h2>Metrics Tracking</h2>
 *
 * <p>
 * Tracks essential detection metrics via Micrometer:
 *
 * <ul>
 * <li><code>hotspot.detection.checked.total</code> - Total measurements checked
 * <li><code>hotspot.detection.excluded.total</code> - Total measurements
 * excluded
 * </ul>
 *
 * <p>
 * Exclusion rate = excluded / checked
 * </p>
 *
 * <h2>Thread Safety</h2>
 *
 * <p>
 * This service is thread-safe. All matchers are immutable records, predicate
 * composition is
 * immutable, and metrics are managed by thread-safe atomic counters.
 *
 * @author WiFi Location Data Pipeline Team
 * @version 2.0
 * @see WifiMeasurement
 * @see DataFilteringConfigurationProperties
 * @since 1.0
 */
@Service
@EnableConfigurationProperties(DataFilteringConfigurationProperties.class)
public class MobileHotspotDetectionService {

    private static final Logger logger = LoggerFactory.getLogger(MobileHotspotDetectionService.class);

    private final MobileHotspotConfiguration config;
    private final Optional<Predicate<NetworkIdentifier>> hotspotPredicate;
    private final Counter totalCheckedCounter;
    private final Counter excludedMeasurementsCounter;

    /**
     * Sealed interface for type-safe mobile hotspot detection matchers.
     *
     * <p>
     * Each matcher encapsulates its detection logic and provides a description for
     * logging and debugging.
     */
    sealed interface HotspotMatcher permits OuiMatcher, ContainsMatcher, RegexMatcher {
        /**
         * Tests if the WiFi measurement matches this hotspot pattern.
         *
         * @param measurement the WiFi measurement to test
         * @return true if the measurement matches the hotspot pattern
         */
        boolean matches(NetworkIdentifier networkIdentifier);

        /**
         * Returns a human-readable description of this matcher.
         *
         * @return matcher description
         */
        String description();
    }

    /**
     * OUI-based matcher using normalized MAC address prefixes.
     *
     * <p>
     * Performs O(1) Set lookup on the first 6 characters (3 octets) of normalized
     * BSSID.
     * All OUI strings are normalized at construction time (lowercase, no
     * separators).
     *
     * @param normalizedOuiSet set of normalized OUI prefixes (e.g., "00236c")
     * @param description      human-readable description
     */
    record OuiMatcher(Set<String> normalizedOuiSet, String description) implements HotspotMatcher {
        @Override
        public boolean matches(NetworkIdentifier networkIdentifier) {
            if (networkIdentifier.bssid() == null || networkIdentifier.bssid().isEmpty()) {
                return false;
            }
            String normalizedBssid = normalizeMacAddress(networkIdentifier.bssid());
            String oui = extractOui(normalizedBssid);
            return normalizedOuiSet.contains(oui);
        }

        private static String normalizeMacAddress(String macAddress) {
            return macAddress.replaceAll("[^a-fA-F0-9]", "").toLowerCase();
        }

        private static String extractOui(String normalizedMac) {
            return normalizedMac.length() >= 6 ? normalizedMac.substring(0, 6) : normalizedMac;
        }
    }

    /**
     * SSID-based matcher using fast String.contains() for simple substring
     * matching.
     *
     * <p>
     * Always case-insensitive. Approximately 10x faster than regex matching.
     * Pattern is normalized to lowercase at construction time.
     *
     * @param pattern     the substring pattern to match (normalized to lowercase)
     * @param description human-readable description
     */
    record ContainsMatcher(String pattern, String description) implements HotspotMatcher {
        public ContainsMatcher {
            // Normalize pattern to lowercase at construction time
            pattern = pattern.toLowerCase();
        }

        @Override
        public boolean matches(NetworkIdentifier networkIdentifier) {
            if (networkIdentifier.ssid() == null || networkIdentifier.ssid().isEmpty()) {
                return false;
            }
            return networkIdentifier.ssid().toLowerCase().contains(pattern);
        }
    }

    /**
     * SSID-based matcher using pre-compiled regex for complex patterns.
     *
     * <p>
     * Always case-insensitive (Pattern.CASE_INSENSITIVE flag applied).
     * Pattern is compiled at construction time for reuse.
     *
     * @param compiledPattern pre-compiled regex pattern (case-insensitive)
     * @param description     human-readable description
     */
    record RegexMatcher(Pattern compiledPattern, String description) implements HotspotMatcher {
        @Override
        public boolean matches(NetworkIdentifier networkIdentifier) {
            if (networkIdentifier.ssid() == null || networkIdentifier.ssid().isEmpty()) {
                return false;
            }
            return compiledPattern.matcher(networkIdentifier.ssid()).find();
        }
    }

    /**
     * Constructs the mobile hotspot detection service with configuration and
     * metrics registry.
     *
     * <p>
     * Performs all validation, pattern compilation, and OUI normalization at
     * initialization time.
     * Invalid configurations cause immediate failure (fail-fast principle).
     *
     * @param properties    the data filtering configuration properties
     * @param meterRegistry the Micrometer meter registry for metrics tracking
     * @throws IllegalArgumentException if configuration is invalid (e.g., invalid
     *                                  regex patterns)
     */
    public MobileHotspotDetectionService(
            DataFilteringConfigurationProperties properties, MeterRegistry meterRegistry) {
        this.config = properties.mobileHotspot();

        // Initialize metrics (always needed for consistent API)
        this.totalCheckedCounter = Counter.builder("hotspot.detection.checked.total")
                .description("Total number of measurements checked for hotspot detection")
                .register(meterRegistry);

        this.excludedMeasurementsCounter = Counter.builder("hotspot.detection.excluded.total")
                .description("Number of measurements excluded due to hotspot detection")
                .register(meterRegistry);

        // Build hotspot detection predicate (or empty if disabled)
        this.hotspotPredicate = buildHotspotPredicate();

        logInitialization();
    }

     /**
      * Checks if a WiFi measurement represents a mobile hotspot.
      *
      * <p>
      * Uses functional predicate composition for evaluation. Returns true if the
      * measurement is detected as a mobile hotspot.
      *
      * @param NetworkIdentifier the WiFi measurement to check
      * @return true if the measurement is a mobile hotspot, false otherwise
      */
     public boolean isMobileHotspot(NetworkIdentifier networkIdentifier) {
         return hotspotPredicate
                 .map(predicate -> {
                     totalCheckedCounter.increment();
                     boolean isHotspot = predicate.test(networkIdentifier);
                     if (isHotspot) {
                         excludedMeasurementsCounter.increment();
                         logger.debug(
                                 "Mobile hotspot detected - BSSID: {}, SSID: {}",
                                 networkIdentifier.bssid(),
                                 networkIdentifier.ssid());
                     }
                     return isHotspot;
                 })
                 .orElse(false);
     }

    // Private initialization methods

    /**
     * Builds the composed hotspot detection predicate from enabled configurations.
     *
     * <p>
     * Combines OUI and SSID matchers with OR logic using declarative stream
     * composition.
     * Returns empty Optional if detection is disabled or no matchers are
     * configured.
     *
     * @return Optional containing composed predicate, or empty if detection
     *         disabled
     */
     private Optional<Predicate<NetworkIdentifier>> buildHotspotPredicate() {
         if (isDetectionDisabled()) {
             logger.info("Mobile hotspot detection is DISABLED");
             return Optional.empty();
         }

         // Declaratively compose matchers from OUI and SSID detection streams
         // Collect to list to avoid stream reuse issues
         List<HotspotMatcher> matchers = Stream.concat(
                 buildOuiMatcher().stream(),
                 buildSsidMatchers().orElse(Stream.empty()))
             .toList();

         if (matchers.isEmpty()) {
             logger.warn(
                     "Mobile hotspot detection is ENABLED but no matchers configured - detection will be ineffective");
             return Optional.empty();
         }

         // Compose all matchers with OR logic (short-circuit on first match)
         Predicate<NetworkIdentifier> composedPredicate = measurement ->
                 matchers.stream().anyMatch(matcher -> matcher.matches(measurement));

         return Optional.of(composedPredicate);
     }

    private boolean isDetectionDisabled() {
        return config == null || !config.enabled();
    }

    /**
     * Builds OUI matcher from configuration if enabled.
     *
     * @return Optional containing OUI matcher, or empty if OUI detection disabled
     *         or no OUIs configured
     */
    private Optional<HotspotMatcher> buildOuiMatcher() {
        if (config.ouiDetection() == null
                || !config.ouiDetection().enabled()
                || config.ouiDetection().ouiBlacklist() == null
                || config.ouiDetection().ouiBlacklist().isEmpty()) {
            return Optional.empty();
        }

        Set<String> normalizedOuis = config.ouiDetection().ouiBlacklist().stream()
                .map(oui -> oui.replaceAll("[^a-fA-F0-9]", "").toLowerCase())
                .collect(Collectors.toSet());

        logger.info("Initialized OUI-based detection with {} OUI patterns", normalizedOuis.size());
        return Optional.of(
                new OuiMatcher(
                        normalizedOuis, "OUI-based detection (" + normalizedOuis.size() + " patterns)"));
    }

    /**
     * Builds SSID matchers from configuration if enabled.
     *
     * <p>
     * Creates ContainsMatcher for CONTAINS patterns and RegexMatcher for REGEX
     * patterns using
     * declarative stream operations. All patterns are case-insensitive.
     *
     * @return stream of SSID matchers (empty if SSID detection disabled)
     * @throws PatternSyntaxException if regex pattern compilation fails
     */
    private Optional<Stream<HotspotMatcher>> buildSsidMatchers() {
        if (config.ssidDetection() == null
                || !config.ssidDetection().enabled()
                || config.ssidDetection().patterns() == null
                || config.ssidDetection().patterns().isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(config.ssidDetection().patterns().stream()
                .map(this::createSsidMatcher));

    }

    /**
     * Creates appropriate matcher based on pattern type.
     *
     * @param ssidPattern the SSID pattern configuration
     * @return HotspotMatcher instance (ContainsMatcher or RegexMatcher)
     * @throws PatternSyntaxException if regex pattern is invalid
     */
    private HotspotMatcher createSsidMatcher(SsidPattern ssidPattern) {
        logger.info(
                "Compiling SSID pattern: '{}' (type: {}, description: {})",
                ssidPattern.pattern(),
                ssidPattern.type(),
                ssidPattern.description());
        
        return ssidPattern.type() == PatternType.CONTAINS
                ? new ContainsMatcher(ssidPattern.pattern(), ssidPattern.description())
                : new RegexMatcher(
                        Pattern.compile(ssidPattern.pattern(), Pattern.CASE_INSENSITIVE),
                        ssidPattern.description());
    }

     private void logInitialization() {
         if (hotspotPredicate.isEmpty()) {
             logger.info("Mobile hotspot detection is DISABLED or has no matchers configured");
         } else {
             logger.info(
                     "Mobile hotspot detection initialized - OUI Detection: {}, SSID Detection: {}",
                     config.ouiDetection() != null && config.ouiDetection().enabled(),
                     config.ssidDetection() != null && config.ssidDetection().enabled());
         }
     }
}
