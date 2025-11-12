## Complete LOF Process for WiFi Positioning - Detailed Requirements with Implementation

### **Overall Objective**
Filter WiFi scan results to remove spatial outliers while preserving strong-signal APs that might be isolated but valuable for positioning.

---

## **Phase 1: Environment Analysis**

### **What This Phase Does:**
Analyzes the current WiFi scan to understand the characteristics of the environment we're dealing with. This creates a "fingerprint" of the current situation.

### **Why We Need This:**
Different environments require different filtering strategies. A dense office with 30 APs needs aggressive filtering, while a sparse warehouse with 5 APs needs to preserve every possible signal. Without understanding the environment, we'd apply the same rules everywhere, leading to poor positioning.

### **Role in the System:**
This phase acts as the "intelligence gatherer" that informs all subsequent decisions. It's like a scout that reports back about the terrain before we plan our strategy.

### Implementation:
```python
def analyze_scan_environment(scan_results):
    """
    Analyze current scan to understand the environment characteristics
    """
    analysis = {
        # Basic counts - tells us how many APs we have to work with
        'ap_count': len(scan_results),
        'strong_signal_count': sum(1 for ap in scan_results if ap.rssi > -70),
        'medium_signal_count': sum(1 for ap in scan_results if -80 < ap.rssi <= -70),
        'weak_signal_count': sum(1 for ap in scan_results if ap.rssi <= -80),
    }
    
    # RSSI range tells us if we have consistent signals or high variance
    # High variance might indicate multiple floors or large spaces
    rssi_values = [ap.rssi for ap in scan_results]
    analysis['rssi_range'] = max(rssi_values) - min(rssi_values)
    analysis['rssi_mean'] = sum(rssi_values) / len(rssi_values)
    analysis['rssi_std'] = (sum((x - analysis['rssi_mean'])**2 for x in rssi_values) / len(rssi_values))**0.5
    
    # Spatial spread tells us if APs are clustered or distributed
    # Small spread = small room, Large spread = large building/floor
    total_distance = 0
    count = 0
    for i, ap1 in enumerate(scan_results):
        for ap2 in scan_results[i+1:]:
            distance = ((ap1.location[0] - ap2.location[0])**2 + 
                       (ap1.location[1] - ap2.location[1])**2)**0.5
            total_distance += distance
            count += 1
    
    analysis['spatial_spread'] = total_distance / count if count > 0 else 0
    
    # Strong signal ratio tells us signal quality
    # High ratio = close to many APs, Low ratio = far from most APs
    analysis['strong_signal_ratio'] = analysis['strong_signal_count'] / analysis['ap_count']
    
    # Environment classification drives parameter selection
    if analysis['ap_count'] >= 20:
        analysis['environment_type'] = 'dense'  # Like an office building
    elif analysis['ap_count'] >= 10:
        analysis['environment_type'] = 'moderate'  # Like a home or small store
    else:
        analysis['environment_type'] = 'sparse'  # Like a warehouse
    
    return analysis
```

---

## **Phase 2: Parameter Adaptation**

### **What This Phase Does:**
Takes the environment analysis and generates specific parameters tailored to the current situation. It's like adjusting your camera settings based on lighting conditions.

### **Why We Need This:**
Fixed parameters fail in varying environments. LOF with k=5 works in dense environments but fails when you only have 4 APs total. Similarly, strict outlier thresholds work when you have many APs but would eliminate all APs in sparse environments.

### **Role in the System:**
This phase acts as the "strategist" that decides how aggressive or conservative to be with filtering based on what we learned about the environment.

### Implementation:
```python
def adapt_parameters(scan_analysis):
    """
    Generate adaptive parameters based on environment analysis
    Each parameter is chosen for a specific reason based on the environment
    """
    params = {}
    env_type = scan_analysis['environment_type']
    ap_count = scan_analysis['ap_count']
    
    # K-NEIGHBORS ADAPTATION
    # k determines how many neighbors we consider for density calculation
    # Dense: More neighbors available, use more for better statistics
    # Sparse: Few neighbors available, must use fewer
    if env_type == 'dense':
        params['k_neighbors'] = min(10, ap_count // 3)  # Use 1/3 of APs, max 10
        # Why 10? Beyond 10, computation increases but accuracy doesn't improve much
    elif env_type == 'moderate':
        params['k_neighbors'] = min(5, ap_count // 2)  # Use 1/2 of APs, max 5
        # Why 5? Balance between statistical significance and having enough APs
    else:  # sparse
        params['k_neighbors'] = max(2, min(3, ap_count - 1))
        # Why 2-3? Minimum for meaningful density comparison
    
    # LOF THRESHOLD ADAPTATION
    # These thresholds determine when an AP is considered an outlier
    # Dense: Can be strict because we have many APs to choose from
    # Sparse: Must be lenient to preserve enough APs
    if env_type == 'dense':
        params['lof_threshold_low'] = 1.3   # 30% more isolated than neighbors
        params['lof_threshold_high'] = 2.2  # 2.2x more isolated than neighbors
    elif env_type == 'moderate':
        params['lof_threshold_low'] = 1.2   # 20% more isolated
        params['lof_threshold_high'] = 2.0  # 2x more isolated
    else:  # sparse
        params['lof_threshold_low'] = 1.1   # Only 10% more isolated
        params['lof_threshold_high'] = 1.8  # 1.8x more isolated
    
    # PROTECTION COUNT ADAPTATION
    # Determines how many strong signals to protect from filtering
    strong_ratio = scan_analysis['strong_signal_ratio']
    if strong_ratio > 0.5:
        # Many strong signals - can afford to protect more
        params['top_n_protected'] = min(5, ap_count // 3)
    elif strong_ratio > 0.3:
        # Moderate strong signals - protect top 3
        params['top_n_protected'] = 3
    else:
        # Few strong signals - they're precious, protect them all
        params['top_n_protected'] = max(2, scan_analysis['strong_signal_count'])
    
    # MINIMUM AP REQUIREMENT
    # Minimum needed for reliable positioning (trilateration needs 3+)
    if env_type == 'dense':
        params['min_aps_required'] = 6  # Can be selective
    elif env_type == 'moderate':
        params['min_aps_required'] = 4  # Standard minimum
    else:
        params['min_aps_required'] = 3  # Absolute minimum for positioning
    
    # Z-SCORE THRESHOLD FOR SIGNAL OUTLIERS
    # Determines when a signal is statistically stronger than others
    # High variance needs stricter threshold to find true outliers
    if scan_analysis['rssi_range'] > 40:
        params['z_score_threshold'] = 2.0  # 2 standard deviations
    elif scan_analysis['rssi_range'] > 25:
        params['z_score_threshold'] = 1.5
    else:
        params['z_score_threshold'] = 1.0  # Low variance, be sensitive
    
    # LOCAL AREA CHECK PARAMETERS
    # Defines the radius for checking if AP is local maximum
    if scan_analysis['spatial_spread'] > 100:
        params['local_check_radius'] = 50  # Large area, check 50m radius
        params['local_signal_advantage'] = 15  # Need 15dBm advantage
    elif scan_analysis['spatial_spread'] > 50:
        params['local_check_radius'] = 30
        params['local_signal_advantage'] = 10
    else:
        params['local_check_radius'] = 20  # Small area, check 20m radius
        params['local_signal_advantage'] = 7  # Need only 7dBm advantage
    
    return params
```

---

## **Phase 3: Strong Signal Protection**

### **What This Phase Does:**
Identifies and protects the N strongest signals from being filtered out, regardless of their spatial properties.

### **Why We Need This:**
Research shows that maximum RSSI values often provide better positioning accuracy than averaged values. The strongest signals are statistically most likely to come from the nearest APs, which are most valuable for positioning.

### **Role in the System:**
Acts as a "safety net" ensuring we never lose the most valuable signals, even if they appear as spatial outliers.

### Implementation:
```python
def protect_strong_signals(scan_results, params):
    """
    Protect the strongest N signals from being filtered
    These are given immunity from LOF-based filtering
    """
    # Sort by signal strength to find the strongest
    sorted_aps = sorted(scan_results, key=lambda ap: ap.rssi, reverse=True)
    
    # Mark top N as protected based on environment
    n_to_protect = params['top_n_protected']
    
    for i in range(min(n_to_protect, len(sorted_aps))):
        sorted_aps[i].is_protected = True
        sorted_aps[i].trust_level = "PROTECTED"
        sorted_aps[i].position_weight = 2.0  # Double weight for positioning
        
        # Why protect these?
        # 1. Strongest signals = likely closest APs
        # 2. Even if spatially odd, strong signal indicates proximity
        # 3. These provide the most reliable distance estimates
    
    return scan_results
```

---

## **Phase 4: Spatial LOF Calculation**

### **What This Phase Does:**
Calculates the Local Outlier Factor for each AP based on its physical location relative to other APs. This identifies which APs are spatially isolated.

### **Why We Need This:**
Spatial outliers might be measurement errors, moved APs, or APs from adjacent floors/buildings. LOF is better than simple distance because it considers local density - an AP 30m away might be normal in a warehouse but an outlier in a dense office.

### **Role in the System:**
This is the "anomaly detector" that finds APs that don't fit the spatial pattern. However, unlike traditional LOF usage, we don't immediately discard these - we investigate further in Phase 5.

### Implementation:
```python
def calculate_spatial_lof(ap, all_aps, k):
    """
    Calculate Local Outlier Factor based on spatial location
    LOF compares local density of a point to its neighbors
    
    The algorithm works in steps:
    1. Find k nearest neighbors
    2. Calculate reachability distance (smoothed distance)
    3. Calculate local density
    4. Compare to neighbor densities
    """
    
    # STEP 1: Find k nearest neighbors
    # We need neighbors to understand local density
    distances = []
    for other_ap in all_aps:
        if other_ap.bssid != ap.bssid:
            dist = ((ap.location[0] - other_ap.location[0])**2 + 
                   (ap.location[1] - other_ap.location[1])**2)**0.5
            distances.append((dist, other_ap))
    
    distances.sort(key=lambda x: x[0])
    k_neighbors = distances[:k]
    
    # STEP 2: Calculate k-distance
    # This is the distance to the kth neighbor - defines "local"
    k_distance = k_neighbors[-1][0] if k_neighbors else 0
    
    # STEP 3: Calculate reachability distances
    # Reachability distance prevents statistical anomalies from very close points
    # It's the maximum of actual distance and neighbor's k-distance
    reach_distances = []
    for dist, neighbor in k_neighbors:
        neighbor_k_dist = get_k_distance(neighbor, all_aps, k)
        reach_dist = max(dist, neighbor_k_dist)
        # Why max? Prevents instability when points are too close
        reach_distances.append(reach_dist)
    
    # STEP 4: Calculate Local Reachability Density (LRD)
    # LRD = 1 / (average reachability distance)
    # High LRD = dense area, Low LRD = sparse area
    avg_reach_dist = sum(reach_distances) / len(reach_distances) if reach_distances else 1
    lrd_ap = 1.0 / avg_reach_dist
    
    # STEP 5: Calculate neighbor LRDs
    # We need to know how dense the neighborhoods of our neighbors are
    neighbor_lrds = []
    for _, neighbor in k_neighbors:
        neighbor_lrd = calculate_lrd_for_ap(neighbor, all_aps, k)
        neighbor_lrds.append(neighbor_lrd)
    
    # STEP 6: Calculate LOF
    # LOF = (average density of neighbors) / (density of point)
    # LOF ≈ 1: Similar density to neighbors (normal)
    # LOF > 1: Lower density than neighbors (outlier)
    # LOF < 1: Higher density than neighbors (in a cluster)
    avg_neighbor_lrd = sum(neighbor_lrds) / len(neighbor_lrds) if neighbor_lrds else 1
    lof = avg_neighbor_lrd / lrd_ap if lrd_ap > 0 else 2.0
    
    return lof
```

---

## **Phase 5: Signal Justification Analysis**

### **What This Phase Does:**
Determines whether an AP's signal strength provides a valid reason for it being spatially isolated. This is the key innovation that prevents discarding valuable APs.

### **Why We Need This:**
A spatially isolated AP might be an outlier OR it might be the closest AP to the user's actual position. Signal strength helps us distinguish between these cases. Strong isolated signals often indicate proximity despite spatial oddity.

### **Role in the System:**
This phase acts as the "detective" that investigates whether spatial outliers have a good reason to be outliers. It's what makes this approach superior to traditional spatial-only LOF.

### **5.1 Absolute Signal Check**
```python
def check_absolute_signal(ap):
    """
    Very strong signals are always valuable regardless of position
    
    Why -60 dBm threshold?
    - Signals stronger than -60 dBm indicate very close proximity (usually <5m)
    - At this strength, the AP is almost certainly one of the closest
    - Path loss models show -60 dBm requires direct line of sight or very short distance
    """
    if ap.rssi > -60:
        return True
    return False
```

### **5.2 Statistical Outlier Check**
```python
def check_statistical_outlier(ap, scan_analysis, params):
    """
    Check if signal is statistically stronger than other APs
    
    Why use z-score?
    - Identifies signals that are unusually strong for this environment
    - Adaptive to the current signal distribution
    - A high z-score means this AP is likely closer than others
    """
    z_score = (ap.rssi - scan_analysis['rssi_mean']) / scan_analysis['rssi_std']
    
    if z_score > params['z_score_threshold']:
        # This signal is significantly stronger than average
        # Likely indicates proximity despite spatial position
        return True
    return False
```

### **5.3 Local Maximum Check**
```python
def check_local_maximum(ap, all_aps, params):
    """
    Check if AP is the strongest in its local area
    
    Why this matters:
    - An AP that dominates its local area is likely the closest AP for users in that area
    - Even if isolated from other APs, it serves an important coverage area
    - Removing it would leave a coverage gap
    """
    local_radius = params['local_check_radius']  # e.g., 30 meters
    signal_advantage = params['local_signal_advantage']  # e.g., 10 dBm
    
    # Find all APs within the local radius
    nearby_aps = []
    for other_ap in all_aps:
        if other_ap.bssid != ap.bssid:
            distance = ((ap.location[0] - other_ap.location[0])**2 + 
                       (ap.location[1] - other_ap.location[1])**2)**0.5
            if distance <= local_radius:
                nearby_aps.append(other_ap)
    
    if not nearby_aps:
        # No nearby APs - this AP provides unique coverage
        return True
    
    # Check if this AP dominates its area
    max_nearby_rssi = max(other_ap.rssi for other_ap in nearby_aps)
    
    if ap.rssi > max_nearby_rssi + signal_advantage:
        # This AP is significantly stronger than neighbors
        # It likely provides primary coverage for this area
        return True
    
    return False
```

### **5.4 Signal-Distance Consistency Check**
```python
def check_signal_distance_consistency(ap, all_aps):
    """
    Check if signal strength is consistent with distance using path loss model
    
    Why this works:
    - WiFi signals follow predictable path loss: weaker with distance
    - If signal is much stronger than expected for its distance, it might be:
      1. Actually closer than its reported position (position error)
      2. Having better propagation path (less obstacles)
    - Either way, it's valuable for positioning
    """
    # Calculate weighted center (signals vote for center with their strength)
    total_weight = 0
    weighted_x = 0
    weighted_y = 0
    
    for other_ap in all_aps:
        # Convert RSSI to linear scale for proper weighting
        # Why? RSSI is logarithmic, linear weights are more intuitive
        weight = 10 ** (other_ap.rssi / 10)
        weighted_x += other_ap.location[0] * weight
        weighted_y += other_ap.location[1] * weight
        total_weight += weight
    
    cluster_center_x = weighted_x / total_weight
    cluster_center_y = weighted_y / total_weight
    
    # Distance from AP to weighted center
    distance_to_center = ((ap.location[0] - cluster_center_x)**2 + 
                         (ap.location[1] - cluster_center_y)**2)**0.5
    
    # Expected RSSI using indoor path loss model
    # Formula: RSSI = P_ref - 10*n*log10(d)
    # P_ref = -30: Typical WiFi power at 1 meter
    # n = 2.5: Typical indoor path loss exponent
    import math
    P_ref = -30
    n = 2.5
    
    if distance_to_center <= 1:
        expected_rssi = P_ref
    else:
        expected_rssi = P_ref - 10 * n * math.log10(distance_to_center)
    
    # Check if observed is much stronger than expected
    rssi_difference = ap.rssi - expected_rssi
    
    if rssi_difference > 10:  # 10 dBm stronger than expected
        # Signal too strong for this distance
        # Likely closer than position suggests
        return True
    
    return False
```

### **5.5 Combined Signal Justification**
```python
def check_signal_justifies_isolation(ap, all_aps, params, scan_analysis):
    """
    Combine all checks to determine if isolation is justified
    
    Why combine multiple checks?
    - Each check catches different scenarios
    - Redundancy prevents missing valuable APs
    - Any one justification is sufficient to preserve the AP
    """
    # Check 1: Absolute strength - closest APs
    if check_absolute_signal(ap):
        return True
    
    # Check 2: Statistical outlier - relatively strong
    if check_statistical_outlier(ap, scan_analysis, params):
        return True
    
    # Check 3: Local maximum - area coverage
    if check_local_maximum(ap, all_aps, params):
        return True
    
    # Check 4: Signal-distance inconsistency - position errors
    if check_signal_distance_consistency(ap, all_aps):
        return True
    
    return False
```

---

## **Phase 6: Two-Factor Classification**

### **What This Phase Does:**
Makes the final decision about each AP by combining spatial LOF score with signal justification. This creates a nuanced classification instead of binary keep/discard.

### **Why We Need This:**
Traditional LOF uses a single threshold: above = outlier, below = normal. This fails for WiFi because spatial outliers might be valuable. The two-factor approach recognizes that isolation + strong signal = likely proximity, while isolation + weak signal = likely outlier.

### **Role in the System:**
This is the "judge" that weighs evidence from spatial analysis and signal analysis to make a fair decision about each AP's trustworthiness.

### Implementation:
```python
def classify_ap_two_factor(ap, params, signal_justified):
    """
    Classify AP based on two factors: spatial isolation and signal justification
    
    The decision matrix logic:
    - Low LOF + Any signal = HIGH trust (spatially normal)
    - Medium LOF + Good signal = MEDIUM trust (isolation explained)
    - Medium LOF + Weak signal = LOW trust (suspicious)
    - High LOF + Good signal = MEDIUM_ISOLATED (valuable despite isolation)
    - High LOF + Weak signal = OUTLIER (likely error)
    """
    lof = ap.spatial_lof
    low_threshold = params['lof_threshold_low']   # e.g., 1.2
    high_threshold = params['lof_threshold_high']  # e.g., 2.0
    
    if lof < low_threshold:
        # LOW LOF: Not isolated - normal spatial position
        ap.trust_level = "HIGH"
        # Why HIGH trust? Spatially consistent with other APs
        # Weight based on signal quality
        if ap.rssi > -70:
            ap.position_weight = 1.5  # Strong + normal = very reliable
        else:
            ap.position_weight = 1.0  # Weak + normal = reliable
        
    elif lof >= low_threshold and lof < high_threshold:
        # MEDIUM LOF: Moderately isolated
        if signal_justified:
            # Signal explains the isolation
            ap.trust_level = "MEDIUM"
            ap.position_weight = 1.2
            # Why MEDIUM? Unusual position but good reason
        else:
            # Isolated without good reason
            ap.trust_level = "LOW"
            ap.position_weight = 0.7
            # Why LOW? Suspicious but not conclusively bad
            
    else:  # lof >= high_threshold
        # HIGH LOF: Highly isolated
        if signal_justified:
            # Strong signal despite isolation
            ap.trust_level = "MEDIUM_ISOLATED"
            ap.position_weight = 1.0
            # Why keep it? Might be closest AP despite odd position
        else:
            # Isolated and weak
            ap.trust_level = "OUTLIER"
            ap.position_weight = 0.3
            # Why OUTLIER? No justification for isolation
    
    return ap.trust_level, ap.position_weight
```

---

## **Phase 7: Graduated Filtering**

### **What This Phase Does:**
Builds the final filtered AP list using trust levels, ensuring we maintain the minimum required APs for positioning while prioritizing high-quality APs.

### **Why We Need This:**
Instead of binary filtering (keep/discard), graduated filtering preserves information with appropriate weights. Even outliers might contribute useful information if weighted properly. Also ensures we never have too few APs for positioning.

### **Role in the System:**
This is the "final assembler" that builds the optimal AP set for positioning, balancing quality with quantity.

### Implementation:
```python
def apply_graduated_filtering(scan_results, params):
    """
    Apply graduated filtering based on trust levels
    
    Why graduated instead of binary?
    1. Preserves information - even weak signals contribute something
    2. Weights ensure unreliable APs have less influence
    3. Guarantees minimum APs for positioning algorithms
    """
    # Separate APs by trust level for organized selection
    protected = [ap for ap in scan_results if ap.is_protected]
    high_trust = [ap for ap in scan_results if ap.trust_level == "HIGH"]
    medium_trust = [ap for ap in scan_results if ap.trust_level in ["MEDIUM", "MEDIUM_ISOLATED"]]
    low_trust = [ap for ap in scan_results if ap.trust_level == "LOW"]
    outliers = [ap for ap in scan_results if ap.trust_level == "OUTLIER"]
    
    filtered_results = []
    
    # Step 1: Always include protected APs
    # Why? These are strongest signals, most likely to be closest
    filtered_results.extend(protected)
    
    # Step 2: Include high trust APs
    # Why? Spatially consistent and reasonable signals
    for ap in high_trust:
        if ap not in filtered_results:
            filtered_results.append(ap)
    
    # Step 3: Include medium trust APs
    # Why? Valuable despite some uncertainty
    for ap in medium_trust:
        if ap not in filtered_results:
            filtered_results.append(ap)
    
    # Check if we have enough APs
    min_required = params['min_aps_required']
    
    # Step 4: Add low trust if needed
    # Why? Better to have weighted uncertain data than no data
    if len(filtered_results) < min_required:
        low_trust.sort(key=lambda x: x.rssi, reverse=True)
        for ap in low_trust:
            if ap not in filtered_results:
                filtered_results.append(ap)
                if len(filtered_results) >= min_required:
                    break
    
    # Step 5: Add outliers as last resort
    # Why? Positioning algorithms need minimum APs to work
    if len(filtered_results) < min_required:
        outliers.sort(key=lambda x: x.rssi, reverse=True)
        for ap in outliers:
            if ap not in filtered_results:
                ap.position_weight = 0.2  # Minimal influence
                filtered_results.append(ap)
                if len(filtered_results) >= min_required:
                    break
    
    return filtered_results
```

---

## **Complete Pipeline Integration**

### **What This Does:**
Orchestrates all phases in the correct order to produce the final filtered AP list.

### **Why This Structure:**
Each phase depends on the previous one. Environment analysis informs parameters, parameters control LOF calculation, LOF and signals determine classification, and classification drives filtering.

### **Role:**
This is the "conductor" that ensures all components work together harmoniously.

```python
def apply_adaptive_lof_filtering(scan_results):
    """
    Complete pipeline integrating all phases
    
    The flow:
    1. Understand environment (reconnaissance)
    2. Adapt strategy (planning)
    3. Protect valuable assets (preservation)
    4. Analyze spatial patterns (detection)
    5. Investigate anomalies (investigation)
    6. Classify findings (judgment)
    7. Build optimal set (selection)
    """
    # Phase 1: Understand what we're dealing with
    scan_analysis = analyze_scan_environment(scan_results)
    print(f"Environment: {scan_analysis['environment_type']}")
    print(f"Signal quality: {scan_analysis['strong_signal_ratio']:.2%} strong")
    
    # Phase 2: Adapt our approach to the environment
    params = adapt_parameters(scan_analysis)
    print(f"Strategy: k={params['k_neighbors']}, protecting top {params['top_n_protected']}")
    
    # Phase 3: Protect the most valuable signals
    protect_strong_signals(scan_results, params)
    
    # Phase 4, 5, 6: Analyze and classify
    # This is where the magic happens - spatial + signal analysis
    apply_two_factor_classification(scan_results, params, scan_analysis)
    
    # Phase 7: Build final set with quality guarantees
    filtered_results = apply_graduated_filtering(scan_results, params)
    
    # Summary for debugging/monitoring
    print(f"\nResults:")
    print(f"  Input: {len(scan_results)} APs")
    print(f"  Output: {len(filtered_results)} APs")
    print(f"  Protected: {sum(1 for ap in filtered_results if ap.is_protected)}")
    print(f"  High trust: {sum(1 for ap in filtered_results if ap.trust_level == 'HIGH')}")
    print(f"  Medium trust: {sum(1 for ap in filtered_results if 'MEDIUM' in ap.trust_level)}")
    print(f"  Low trust: {sum(1 for ap in filtered_results if ap.trust_level == 'LOW')}")
    print(f"  Outliers included: {sum(1 for ap in filtered_results if ap.trust_level == 'OUTLIER')}")
    
    return filtered_results
```

---

## **Why This Approach Works**

1. **Adaptive to Environment:** Parameters adjust to dense vs sparse scenarios automatically
2. **Preserves Strong Signals:** Never loses the most valuable positioning information
3. **Two-Factor Decision:** Distinguishes between bad outliers and valuable isolated APs
4. **Graduated Trust:** Weights preserve information while reducing impact of unreliable data
5. **Minimum Guarantees:** Always provides enough APs for positioning algorithms
6. **Physics-Based:** Uses signal propagation models to validate measurements

This complete system provides robust WiFi positioning by intelligently filtering outliers while preserving valuable signals, even when those signals come from spatially isolated APs.