// src/main/java/com/wifi/positioning/dto/WifiAccessPoints.java
package com.wifi.positioning.dto;

import com.wifi.positioning.algorithm.outlier.SimpleLOFDetector;
import com.wifi.positioning.dto.calculation.AccessPointInfo;
import com.wifi.positioning.dto.calculation.AccessPointSummary;
import com.wifi.positioning.dto.calculation.LocationInfo;
import com.wifi.positioning.dto.calculation.StatusCount;
import com.wifi.positioning.util.GeographicCentroidCalculator;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Immutable collection of WiFi access points with scan results and filtering capabilities.
 * Provides comprehensive filtering based on status, cell tower range, and statistical outlier detection.
 * Maintains immutability by returning new instances on all filter operations.
 */
@Builder(toBuilder = true)
public class WifiAccessPoints {
    
    // ===== ERROR MESSAGE CONSTANTS =====
    
    private static final String ERROR_NO_KNOWN_ACCESS_POINTS = "No known access points found in database";
    private static final String ERROR_ACCESS_POINT_NOT_FOUND = "Access point not found in database";
    private static final String ERROR_INVALID_AP_STATUS_PREFIX = "Invalid AP status: ";
    private static final String ERROR_NO_VALID_STATUS_ACCESS_POINTS = "No access points with valid status found";
    private static final String ERROR_OUTSIDE_CELL_TOWER_RANGE_FORMAT = "Outside cell tower %d effective range: %.0fm > (%.0fm + %.0fm) = %.0fm";
    private static final String ERROR_NO_ACCESS_POINTS_GLOBAL_OUTLIERS = "No access points remaining after global outlier filtering";
    private static final String ERROR_TOO_FAR_FROM_CENTROID_FORMAT = "Too far from centroid (lat: %.6f, lon: %.6f): %.0fm > %.0fm";
    private static final String ERROR_LOF_LOCAL_OUTLIER = "LOF-based local outlier (isolated from neighborhood)";
    private static final String ERROR_NO_ACCESS_POINTS_LOCAL_OUTLIERS = "No access points remaining after local outlier filtering";
    private static final String ERROR_CANNOT_CONVERT_NON_VIABLE = "Cannot convert non-viable WifiAccessPoints to WifiAPData: ";
    private static final String UNKNOWN_VALUE = "unknown";
    
    // Valid APs for positioning algorithms - no status tracking needed
    // Being in this list means they're valid and ready for use
    private final List<WifiAPWithScan> validAccessPoints;
    
    // Discarded APs grouped by discard reason (map key is the UsageStatus)
    // Makes it easy to understand WHY APs were filtered out
    @Builder.Default
    private final Map<UsageStatus, List<DiscardedAccessPoint>> discardedAccessPoints = new HashMap<>();
    
    // All original scans from request (for total count in summary)
    private final List<WifiScanResult> originalScans;
    
    private final CellTower referenceCell;
    @Builder.Default
    private final boolean viable = true;
    private final String errorMessage;
    
    // Explicit getters (must be public for external use)
    public List<WifiAPWithScan> getValidAccessPoints() {
        return validAccessPoints;
    }
    
    public Map<UsageStatus, List<DiscardedAccessPoint>> getDiscardedAccessPoints() {
        return discardedAccessPoints;
    }
    
    public List<WifiScanResult> getOriginalScans() {
        return originalScans;
    }
    
    public CellTower getReferenceCell() {
        return referenceCell;
    }
    
    public boolean isViable() {
        return viable;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    // Internal factory method for creating new instances (used by filtering methods)
    private static WifiAccessPoints createInstance(
            List<WifiAPWithScan> validAccessPoints,
            Map<UsageStatus, List<DiscardedAccessPoint>> discardedAccessPoints,
            List<WifiScanResult> originalScans,
            CellTower referenceCell,
            boolean viable,
            String errorMessage) {
        
        return WifiAccessPoints.builder()
            .validAccessPoints(validAccessPoints != null ? validAccessPoints : Collections.emptyList())
            .discardedAccessPoints(discardedAccessPoints != null ? discardedAccessPoints : new HashMap<>())
            .originalScans(originalScans != null ? originalScans : Collections.emptyList())
            .referenceCell(referenceCell)
            .viable(viable)
            .errorMessage(errorMessage)
            .build();
    }
    
    /**
     * Creates a filtering builder for WifiAccessPoints with comprehensive filtering pipeline.
     * 
     * @return new FilteringBuilder instance
     */
    public static FilteringBuilder filteringBuilder() {
        return new FilteringBuilder();
    }
    
    /**
     * Filtering builder for WifiAccessPoints with comprehensive filtering pipeline.
     * 
     * <p>This builder encapsulates the complete access point filtering logic including:
     * <ol>
     *   <li>Cell tower lookup (parallel with AP lookup)</li>
     *   <li>Top N strongest signal filtering</li>
     *   <li>Access point location lookup</li>
     *   <li>Location and status validation</li>
     *   <li>Global outlier filtering (cell or centroid-based)</li>
     *   <li>Local outlier filtering (LOF)</li>
     * </ol>
     */
    @Slf4j
    public static class FilteringBuilder {
        private List<WifiScanResult> scanResults;
        private List<CellInfo> cellInfoList;
        private Function<Set<String>, CompletableFuture<Map<String, WifiAccessPoint>>> asyncApLookupFunction;
        private Function<List<CellInfo>, CompletableFuture<Optional<CellTower>>> asyncCellTowerLookupFunction;
        private int topSignalsLimit = 20;
        private double maxWifiDistanceMeters = 500.0;
        
        public FilteringBuilder scanResults(List<WifiScanResult> scanResults) {
            this.scanResults = scanResults;
            return this;
        }
        
        public FilteringBuilder cellInfo(List<CellInfo> cellInfoList) {
            this.cellInfoList = cellInfoList;
            return this;
        }
        
        public FilteringBuilder asyncApLookup(Function<Set<String>, CompletableFuture<Map<String, WifiAccessPoint>>> function) {
            this.asyncApLookupFunction = function;
            return this;
        }
        
        public FilteringBuilder asyncCellTowerLookup(Function<List<CellInfo>, CompletableFuture<Optional<CellTower>>> function) {
            this.asyncCellTowerLookupFunction = function;
            return this;
        }
        
        public FilteringBuilder topSignalsLimit(int limit) {
            this.topSignalsLimit = limit;
            return this;
        }
        
        public FilteringBuilder maxWifiDistance(double meters) {
            this.maxWifiDistanceMeters = meters;
            return this;
        }
        
        /**
         * Builds WifiAccessPoints with complete filtering pipeline applied asynchronously.
         * 
         * <p>This method performs cell tower and access point lookups in parallel using
         * CompletableFuture composition for optimal non-blocking performance. The filtering
         * pipeline is applied once both async operations complete.
         * 
         * <p>No blocking occurs in this method - the returned CompletableFuture completes
         * when all async operations and filtering are done.
         * 
         * @return CompletableFuture that completes with WifiAccessPoints (may be non-viable with error)
         */
        public CompletableFuture<WifiAccessPoints> buildWithFiltering() {
            var pipelineStartNanos = System.nanoTime();
            var topSignals = filterTopStrongestSignals();
            
            // Execute cell tower and AP lookups in parallel
            var cellTowerStartNanos = System.nanoTime();
            CompletableFuture<CellTower> cellTowerFuture = lookupCellTowerAsync();
            
            var apLookupStartNanos = System.nanoTime();
            CompletableFuture<Map<String, WifiAccessPoint>> apFuture = lookupAPLocations(topSignals);
            
            // Compose futures without blocking - apply filtering when both complete
            return cellTowerFuture.thenCombine(apFuture, 
                (cellTower, apLocations) -> {
                    logParallelLookupsPerformance(cellTowerStartNanos, apLookupStartNanos);
                    return buildWithFiltering(topSignals, apLocations, cellTower);
                }
            );
        }
        
        /**
         * Logs performance metrics for parallel cell tower and AP lookups.
         */
        private void logParallelLookupsPerformance(long cellTowerStartNanos, long apLookupStartNanos) {
            long cellTowerDurationMs = (System.nanoTime() - cellTowerStartNanos) / 1_000_000L;
            long apLookupDurationMs = (System.nanoTime() - apLookupStartNanos) / 1_000_000L;
            log.info("[DB lookups completed in] cellTowerDurationMs={} apLookupDurationMs={}",
                cellTowerDurationMs, apLookupDurationMs);
        }
        
        
        private CompletableFuture<CellTower> lookupCellTowerAsync() {
            if (cellInfoList == null || cellInfoList.isEmpty() || asyncCellTowerLookupFunction == null) {
                return CompletableFuture.completedFuture(null);
            }
            
            return asyncCellTowerLookupFunction.apply(cellInfoList)
                    .thenApply(opt -> opt.orElse(null));
        }
        
        private List<WifiScanResult> filterTopStrongestSignals() {
            return scanResults.stream()
                    .sorted(Comparator.comparing(WifiScanResult::signalStrength).reversed())
                    .limit(topSignalsLimit)
                    .toList();
        }
        
        private CompletableFuture<Map<String, WifiAccessPoint>> lookupAPLocations(List<WifiScanResult> topSignals) {
            var macAddresses = topSignals.stream()
                    .map(WifiScanResult::macAddress)
                    .collect(Collectors.toSet());
            
            return asyncApLookupFunction.apply(macAddresses);
        }
        
        private WifiAccessPoints buildFromLookupResult(List<WifiScanResult> topSignals, Map<String, WifiAccessPoint> apMap) {

            // Partition scans into found vs not found using declarative style
            Map<Boolean, List<WifiScanResult>> partitioned = topSignals.stream()
                .collect(Collectors.partitioningBy(scan -> 
                    apMap != null && apMap.containsKey(scan.macAddress())));
            
            List<WifiAPWithScan> initialValid = partitioned.get(true).stream()
                .map(scan -> new WifiAPWithScan(apMap.get(scan.macAddress()), scan))
                .toList();
            
            List<DiscardedAccessPoint> notFound = partitioned.get(false).stream()
                .map(this::createNotFoundDiscardedAP)
                .toList();
            
            // Build initial discarded map with not found APs
            Map<UsageStatus, List<DiscardedAccessPoint>> initialDiscarded = 
                notFound.isEmpty() 
                    ? new HashMap<>()
                    : Map.of(UsageStatus.DISCARDED_NO_LOCATION, notFound);
            
            // If no valid APs found, return non-viable immediately
            if (initialValid.isEmpty()) {
                return createInstance(
                        Collections.emptyList(),
                        initialDiscarded,
                        scanResults,
                        null,
                        false,
                        ERROR_NO_KNOWN_ACCESS_POINTS
                );
            }
            
            return createInstance(
                    initialValid,
                    initialDiscarded,
                    scanResults,
                    null,
                    true,
                    null
            );
        }
        
        private DiscardedAccessPoint createNotFoundDiscardedAP(WifiScanResult scan) {
            return new DiscardedAccessPoint(
                new WifiAPWithScan(null, scan), 
                ERROR_ACCESS_POINT_NOT_FOUND);
        }
        

        private WifiAccessPoints buildWithFiltering(List<WifiScanResult> topSignals, Map<String, WifiAccessPoint> apLocations, CellTower cellTower) {
            var logger = org.slf4j.LoggerFactory.getLogger(WifiAccessPoints.class);
            var filterStartNanos = System.nanoTime();
            
            WifiAccessPoints accessPoints = buildFromLookupResult(topSignals, apLocations);
            logger.info("WifiAccessPoints.buildWithFiltering [STAGE:LOOKUP] validApCount={} discardedApCount={}", 
                accessPoints.getValidAccessPoints().size(),
                accessPoints.getDiscardedAccessPoints().values().stream().mapToInt(List::size).sum());

            if (!accessPoints.isViable()) {
                logger.warn("WifiAccessPoints.buildWithFiltering [STAGE:LOOKUP] [FAILED] error={}", accessPoints.getErrorMessage());
                return accessPoints;
            }

            var statusFilterStartNanos = System.nanoTime();
            accessPoints = accessPoints.filterByStatus();
            var statusFilterDurationMs = (System.nanoTime() - statusFilterStartNanos) / 1_000_000L;
            
            logger.info("WifiAccessPoints.buildWithFiltering [STAGE:STATUS_FILTER] durationMs={} validApCount={} discardedApCount={}", 
                statusFilterDurationMs,
                accessPoints.getValidAccessPoints().size(),
                accessPoints.getDiscardedAccessPoints().values().stream().mapToInt(List::size).sum());
            
            if (!accessPoints.isViable()) {
                logger.warn("WifiAccessPoints.buildWithFiltering [STAGE:STATUS_FILTER] [FAILED] error={}", accessPoints.getErrorMessage());
                return accessPoints;
            }
            
            var cellFilterStartNanos = System.nanoTime();
            accessPoints = applyGlobalOutlierFiltering(accessPoints, cellTower);
            var cellFilterDurationMs = (System.nanoTime() - cellFilterStartNanos) / 1_000_000L;
            
            logger.info("WifiAccessPoints.buildWithFiltering [STAGE:CELL_FILTER] durationMs={} cellTowerId={} validApCount={} discardedApCount={}", 
                cellFilterDurationMs,
                cellTower != null ? cellTower.getId() : "N/A",
                accessPoints.getValidAccessPoints().size(),
                accessPoints.getDiscardedAccessPoints().values().stream().mapToInt(List::size).sum());
            
            if (!accessPoints.isViable()) {
                logger.warn("WifiAccessPoints.buildWithFiltering [STAGE:CELL_FILTER] [FAILED] error={}", accessPoints.getErrorMessage());
                return accessPoints;
            }
            
            var totalFilterDurationMs = (System.nanoTime() - filterStartNanos) / 1_000_000L;
            logger.info("WifiAccessPoints.buildWithFiltering [FILTERING_COMPLETE] totalDurationMs={} stagesCount=3 validApCount={}", 
                totalFilterDurationMs, accessPoints.getValidAccessPoints().size());
            
            return accessPoints;
        }
        
        private WifiAccessPoints applyGlobalOutlierFiltering(WifiAccessPoints accessPoints, CellTower cellTower) {
            if (cellTower != null && cellTower.isValid()) {
                return accessPoints.filterByCellRange(cellTower, maxWifiDistanceMeters);
            } else {
                return accessPoints;
            }
        }
    }
    
    // ===== HELPER METHODS =====
    
    /**
     * Generic helper method for filtering access points based on a predicate.
     * Implements common filtering pattern: separate valid from discarded APs,
     * check for empty valid list, and build appropriate WifiAccessPoints instance.
     * 
     * @param shouldKeep predicate that returns true if AP should be kept in valid list
     * @param errorMessageGenerator function to generate error message for discarded APs
     * @param discardStatus UsageStatus to assign to discarded APs
     * @param emptyErrorMessage error message when no valid APs remain
     * @param cellTower optional cell tower to set in builder
     * @return new WifiAccessPoints with filtering applied
     */
    private WifiAccessPoints filterWithPredicate(
            Predicate<WifiAPWithScan> shouldKeep,
            Function<WifiAPWithScan, String> errorMessageGenerator,
            UsageStatus discardStatus,
            String emptyErrorMessage,
            Optional<CellTower> cellTower) {
        
        List<WifiAPWithScan> stillValid = new ArrayList<>();
        Map<UsageStatus, List<DiscardedAccessPoint>> newDiscarded = new HashMap<>(discardedAccessPoints);
        
        for (WifiAPWithScan pair : validAccessPoints) {
            if (shouldKeep.test(pair)) {
                stillValid.add(pair);
            } else {
                String errorMessage = errorMessageGenerator.apply(pair);
                newDiscarded.computeIfAbsent(discardStatus, k -> new ArrayList<>())
                    .add(new DiscardedAccessPoint(pair, errorMessage));
            }
        }
        
        // Check if no valid APs remain after filtering
        if (stillValid.isEmpty()) {
            return createInstance(
                stillValid,
                newDiscarded,
                this.originalScans,
                cellTower.orElse(this.referenceCell),
                false,
                emptyErrorMessage
            );
        }
        
        return createInstance(
            stillValid,
            newDiscarded,
            this.originalScans,
            cellTower.orElse(this.referenceCell),
            true,
            null
        );
    }
    
    /**
     * Generic helper method for filtering access points by distance from a center point.
     * Implements common filtering logic used by both cell range and centroid distance filters.
     * 
     * @param centerLat latitude of center point
     * @param centerLon longitude of center point
     * @param maxDistanceMeters maximum distance threshold in meters
     * @param discardStatus UsageStatus to assign to discarded APs
     * @param errorMessageGenerator function to generate error message for each discarded AP (receives AP and distance)
     * @param emptyErrorMessage error message when no valid APs remain
     * @param cellTower optional cell tower to set in builder
     * @return new WifiAccessPoints with distance filtering applied
     */
    private WifiAccessPoints filterByDistance(
            double centerLat,
            double centerLon,
            double maxDistanceMeters,
            UsageStatus discardStatus,
            BiFunction<WifiAPWithScan, Double, String> errorMessageGenerator,
            String emptyErrorMessage,
            Optional<CellTower> cellTower) {
        
        // Pre-compute distances to avoid duplicate calculations
        Map<WifiAPWithScan, Double> distanceCache = new HashMap<>();
        for (WifiAPWithScan pair : validAccessPoints) {
            double distance = calculateDistance(
                centerLat,
                centerLon,
                pair.wifiAccessPoint().getLatitude(),
                pair.wifiAccessPoint().getLongitude()
            );
            distanceCache.put(pair, distance);
        }
        
        return filterWithPredicate(
            pair -> distanceCache.get(pair) <= maxDistanceMeters,
            pair -> errorMessageGenerator.apply(pair, distanceCache.get(pair)),
            discardStatus,
            emptyErrorMessage,
            cellTower
        );
    }
    
    // ===== FILTERING METHODS (return new instance) =====

    /**
     * Filters access points by status, marking those with invalid status as discarded.
     * Moves invalid status APs from validAccessPoints to discardedAccessPoints.
     * 
     * @return new WifiAccessPoints with status filtering applied
     */
    public WifiAccessPoints filterByStatus() {
        return filterWithPredicate(
            pair -> {
                String status = pair.wifiAccessPoint().getStatus();
                return status != null && WifiAccessPoint.VALID_AP_STATUSES.contains(status);
            },
            pair -> ERROR_INVALID_AP_STATUS_PREFIX + pair.wifiAccessPoint().getStatus(),
            UsageStatus.DISCARDED_STATUS,
            ERROR_NO_VALID_STATUS_ACCESS_POINTS,
            Optional.empty()
        );
    }
    
    /**
     * Filters access points by cell tower range, marking those outside effective range as discarded.
     * Moves out-of-range APs from validAccessPoints to discardedAccessPoints.
     * 
     * <p>Uses effective range = cell tower range + WiFi range. APs within this combined
     * effective range are kept. This handles edge cases where the target is at the cell
     * tower boundary and mobile scans can detect WiFi APs that are slightly beyond the
     * cell tower's stated range but still within typical WiFi reach.
     * 
     * <p>Example: If cell tower range is 1000m and WiFi range is 500m, the effective range
     * is 1500m. APs beyond 1500m from the cell tower location are discarded.
     * 
     * @param cell the cell tower to use as reference point
     * @param maxWifiDistanceMeters typical WiFi range to add to cell tower range (e.g., 500m)
     * @return new WifiAccessPoints with cell range filtering applied
     */
    public WifiAccessPoints filterByCellRange(CellTower cell, double maxWifiDistanceMeters) {
        double effectiveRange = cell.getRange() + maxWifiDistanceMeters;
        
        return filterByDistance(
            cell.getLatitude(),
            cell.getLongitude(),
            effectiveRange,
            UsageStatus.DISCARDED_CELL_RANGE,
            (pair, distance) -> String.format(ERROR_OUTSIDE_CELL_TOWER_RANGE_FORMAT, 
                cell.getId(), distance, cell.getRange(), maxWifiDistanceMeters, effectiveRange),
            ERROR_NO_ACCESS_POINTS_GLOBAL_OUTLIERS,
            Optional.of(cell)
        );
    }
    
    /**
     * Filters access points by distance from geographic centroid.
     * Uses ECEF vector averaging for accurate spherical centroid calculation.
     * Moves far APs from validAccessPoints to discardedAccessPoints.
     * 
     * @param maxDistanceMeters maximum reasonable distance from centroid (e.g., 500m)
     * @return new WifiAccessPoints with centroid filtering applied
     */
    public WifiAccessPoints filterByCentroidDistance(double maxDistanceMeters) {
        if (validAccessPoints.size() < 2) {
            // Not enough points for centroid calculation
            return this;
        }
        
        // Calculate geographic centroid using ECEF vector averaging
        List<WifiAccessPoint> aps = validAccessPoints.stream()
                .map(WifiAPWithScan::wifiAccessPoint)
                .toList();
        
        var centroid = GeographicCentroidCalculator.calculateCentroid(aps);
        if (centroid == null) {
            return this;
        }
        
        return filterByDistance(
            centroid[0],
            centroid[1],
            maxDistanceMeters,
            UsageStatus.DISCARDED_GLOBAL_OUTLIER_CENTROID,
            (pair, distance) -> String.format(ERROR_TOO_FAR_FROM_CENTROID_FORMAT, 
                centroid[0], centroid[1], distance, maxDistanceMeters),
            ERROR_NO_ACCESS_POINTS_GLOBAL_OUTLIERS,
            Optional.empty()
        );
    }
    
    /**
     * Filters access points using Local Outlier Factor (LOF) algorithm.
     * Uses SimpleLOFDetector to identify access points that are significantly
     * isolated from their local neighborhood.
     * Moves LOF outliers from validAccessPoints to discardedAccessPoints.
     * 
     * @return new WifiAccessPoints with LOF filtering applied
     */
    public WifiAccessPoints filterByLocalOutliers() {
        if (validAccessPoints.size() < 3) {
            // Not enough points for LOF calculation
            return this;
        }
        
        // Detect local outliers using LOF algorithm (pass validAccessPoints)
        Set<String> outlierMacAddresses = SimpleLOFDetector.detectLocalOutliers(validAccessPoints);
        
        if (outlierMacAddresses.isEmpty()) {
            return this;
        }
        
        return filterWithPredicate(
            pair -> !outlierMacAddresses.contains(pair.wifiAccessPoint().getMacAddress()),
            pair -> ERROR_LOF_LOCAL_OUTLIER,
            UsageStatus.DISCARDED_LOCAL_OUTLIER,
            ERROR_NO_ACCESS_POINTS_LOCAL_OUTLIERS,
            Optional.empty()
        );
    }
    
    // ===== QUERY METHODS =====
    


    /**
     * Converts this WifiAccessPoints to WifiAPData for position calculation.
     * Only works when this instance is viable.
     * 
     * @return WifiAPData with complete access point information
     * @throws IllegalStateException if this instance is not viable
     */
    public WifiAPData toWifiAPData() {
        if (!viable) {
            throw new IllegalStateException(
                ERROR_CANNOT_CONVERT_NON_VIABLE + errorMessage);
        }
        
        List<WifiAccessPoint> allKnownAPs = getAllKnownAccessPoints();
        List<WifiAccessPoint> validAPs = getUsedAccessPoints();
        List<WifiScanResult> validScans = getUsedScanResults();
        

        return WifiAPData.viable(originalScans, allKnownAPs, validAPs, this);
    }
    
    /**
     * Returns all known access points (valid + discarded that have location data).
     * Useful for building comprehensive summaries.
     * 
     * @return list of all known WifiAccessPoint objects
     */
    public List<WifiAccessPoint> getAllKnownAccessPoints() {
        List<WifiAccessPoint> allKnownAPs = new ArrayList<>();
        
        // Add valid APs
        validAccessPoints.stream()
            .map(WifiAPWithScan::wifiAccessPoint)
            .forEach(allKnownAPs::add);
        
        // Add discarded APs that have location data
        discardedAccessPoints.values().stream()
            .flatMap(List::stream)
            .map(DiscardedAccessPoint::wifiAccessPoint)
            .filter(Objects::nonNull)
            .forEach(allKnownAPs::add);
        
        return allKnownAPs;
    }
    
    /**
     * Returns list of WifiAccessPoint objects from valid APs.
     * Direct access for positioning algorithms - no filtering needed.
     * 
     * @return list of WifiAccessPoint objects
     */
    public List<WifiAccessPoint> getUsedAccessPoints() {
        return validAccessPoints.stream()
            .map(WifiAPWithScan::wifiAccessPoint)
            .toList();
    }
    
    /**
     * Returns list of WifiScanResult objects from valid APs.
     * Direct access for positioning algorithms - no filtering needed.
     * 
     * @return list of scan results
     */
    public List<WifiScanResult> getUsedScanResults() {
        return validAccessPoints.stream()
            .map(WifiAPWithScan::wifiScanResult)
            .toList();
    }
    
    /**
     * Calculates access point summary for CalculationInfo.
     * Includes all original scans in total count and tracks weak signals in status counts.
     * 
     * @return AccessPointSummary with counts and status breakdown
     */
    public AccessPointSummary calculateAccessPointSummary() {
        int total = originalScans.size(); // All original scans including weak signals
        
        // Count known APs (valid + discarded that have AP data)
        int known = validAccessPoints.size(); // All valid APs are known
        known += (int) discardedAccessPoints.values().stream()
            .flatMap(List::stream)
            .filter(p -> p.wifiAccessPoint() != null)
            .count();
        
        // Used count is size of valid APs
        int used = validAccessPoints.size();
        
        // Build status counts from discarded map + USED count + weak signals
        List<StatusCount> statusCountList = new ArrayList<>();
        
        // Add USED count
        if (used > 0) {
            statusCountList.add(new StatusCount(UsageStatus.USED.name(), used));
        }
        
        // Add discarded counts
        discardedAccessPoints.forEach((status, list) -> 
            statusCountList.add(new StatusCount(status.name(), list.size()))
        );
        
        // Add count for weak signals not in top 20
        int topSignalsCount = validAccessPoints.size() + 
            discardedAccessPoints.values().stream().mapToInt(List::size).sum();
        int weakSignalCount = total - topSignalsCount;
        if (weakSignalCount > 0) {
            statusCountList.add(new StatusCount(UsageStatus.DISCARDED_WEAK_SIGNAL.name(), weakSignalCount));
        }
        
        return new AccessPointSummary(total, known, used, statusCountList);
    }
    
    /**
     * Returns list of AccessPointInfo for all top 20 strongest signals (valid + discarded).
     * Does NOT include weak signals that were filtered out before lookup.
     * Includes detailed discard reasons for discarded APs.
     * 
     * @return list of AccessPointInfo for calculation info
     */
    public List<AccessPointInfo> getAccessPointInfos() {
        List<AccessPointInfo> result = new ArrayList<>();
        
        // Add valid access points (USED) - no discard reason
        validAccessPoints.forEach(pair -> result.add(new AccessPointInfo(
            pair.wifiAccessPoint().getMacAddress(),
            new LocationInfo(
                pair.wifiAccessPoint().getLatitude(),
                pair.wifiAccessPoint().getLongitude(),
                pair.wifiAccessPoint().getAltitude()
            ),
            pair.wifiAccessPoint().getStatus() != null 
                ? pair.wifiAccessPoint().getStatus() 
                : UNKNOWN_VALUE,
            UsageStatus.USED.name(),  // All valid APs are USED
            null  // No discard reason for used APs
        )));
        
        // Add discarded access points (from top 20, but discarded for various reasons)
        // Include the detailed discard reason for diagnostics
        discardedAccessPoints.forEach((status, list) -> 
            list.stream()
                .filter(discarded -> discarded.wifiAccessPoint() != null)
                .forEach(discarded -> {
                    WifiAccessPoint ap = discarded.wifiAccessPoint();
                    result.add(new AccessPointInfo(
                        ap.getMacAddress(),
                        new LocationInfo(ap.getLatitude(), ap.getLongitude(), ap.getAltitude()),
                        ap.getStatus() != null ? ap.getStatus() : UNKNOWN_VALUE,
                        status.name(),  // Use map key as the usage status
                        discarded.discardReason()  // Include detailed discard reason
                    ));
                })
        );
        
        return result;
    }
    
    // ===== UTILITY METHODS =====
    
    /**
     * Calculates distance between two points using Haversine formula.
     * 
     * @param lat1 latitude of first point
     * @param lon1 longitude of first point
     * @param lat2 latitude of second point
     * @param lon2 longitude of second point
     * @return distance in meters
     */
    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final double EARTH_RADIUS_METERS = 6371000.0;
        
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
            + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
            * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        
        return EARTH_RADIUS_METERS * c;
    }
    
        /**
     * Calculates Geometric Dilution of Precision (GDOP) for valid access points.
     * Simplified calculation based on AP distribution.
     * 
     * @return GDOP value (lower is better)
     */
    public double calculateGDOP() {
        List<WifiAccessPoint> accessPoints = getUsedAccessPoints();
        
        if (accessPoints.size() < 3) {
            return 10.0; // Poor GDOP for insufficient APs
        }
        
        // Calculate centroid
        double sumLat = accessPoints.stream().mapToDouble(WifiAccessPoint::getLatitude).sum();
        double sumLon = accessPoints.stream().mapToDouble(WifiAccessPoint::getLongitude).sum();
        double centroidLat = sumLat / accessPoints.size();
        double centroidLon = sumLon / accessPoints.size();
        
        // Calculate average distance from centroid
        double avgDistance = accessPoints.stream()
            .mapToDouble(ap -> calculateDistance(centroidLat, centroidLon, 
                ap.getLatitude(), ap.getLongitude()))
            .average()
            .orElse(0.0);
        
        if (avgDistance < 1.0) {
            return 10.0; // Poor GDOP for co-located APs
        }
        
        // Calculate standard deviation of distances (measure of distribution)
        double variance = accessPoints.stream()
            .mapToDouble(ap -> {
                double dist = calculateDistance(centroidLat, centroidLon, 
                    ap.getLatitude(), ap.getLongitude());
                return Math.pow(dist - avgDistance, 2);
            })
            .average()
            .orElse(0.0);
        
        double stdDev = Math.sqrt(variance);
        
        // GDOP inversely proportional to distribution quality
        // Well-distributed APs have high stdDev relative to avgDistance
        double distributionRatio = avgDistance > 0 ? stdDev / avgDistance : 0.0;
        
        if (distributionRatio > 0.5) {
            return 2.0; // Excellent distribution
        } else if (distributionRatio > 0.3) {
            return 3.5; // Good distribution
        } else if (distributionRatio > 0.1) {
            return 5.5; // Fair distribution
        } else {
            return 8.0; // Poor distribution (collinear or clustered)
        }
    }
    
}


