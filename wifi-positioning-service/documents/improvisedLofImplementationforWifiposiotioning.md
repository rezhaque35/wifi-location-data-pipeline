## Complete LOF Process for WiFi Positioning - Requirements Specification

### **Overall Objective**
Filter WiFi scan results to remove spatial outliers while preserving strong-signal APs that might be isolated but valuable for positioning.

---

## **Phase 1: Environment Analysis**
Analyze the current scan to understand what type of environment we're dealing with.

### Requirements:
1. **Count APs by signal strength categories:**
   - Strong: RSSI > -70 dBm
   - Medium: -80 < RSSI ≤ -70 dBm  
   - Weak: RSSI ≤ -80 dBm

2. **Calculate spatial characteristics:**
   - Average distance between all AP pairs (spatial spread)
   - Signal density = AP count / spatial spread
   - RSSI range = strongest - weakest signal

3. **Classify environment type:**
   - Dense: ≥20 APs visible
   - Moderate: 10-19 APs visible
   - Sparse: <10 APs visible

---

## **Phase 2: Parameter Adaptation**
Adjust all algorithm parameters based on environment analysis.

### Requirements:

#### **2.1 K-Neighbors Adaptation**
- Dense environment: k = min(10, total_aps/3)
- Moderate environment: k = min(5, total_aps/2)  
- Sparse environment: k = max(2, min(3, total_aps-1))

#### **2.2 LOF Threshold Adaptation**
| Environment | Low Threshold (slight outlier) | High Threshold (clear outlier) |
|------------|--------------------------------|--------------------------------|
| Dense      | 1.3                            | 2.2                            |
| Moderate   | 1.2                            | 2.0                            |
| Sparse     | 1.1                            | 1.8                            |

#### **2.3 Protection Count Adaptation**
Based on strong signal ratio (strong_count/total_count):
- Ratio > 0.5: Protect top 5 APs
- Ratio > 0.3: Protect top 3 APs
- Ratio ≤ 0.3: Protect ALL strong signal APs

#### **2.4 Minimum AP Requirement**
- Dense: Require at least 6 APs after filtering
- Moderate: Require at least 4 APs
- Sparse: Require at least 3 APs

---

## **Phase 3: Strong Signal Protection**
Mark the N strongest APs as protected from filtering.

### Requirements:
1. Sort all APs by RSSI (descending)
2. Mark top N as protected (N from Phase 2.3)
3. Assign these APs:
   - Trust level: "PROTECTED"
   - Position weight: 2.0
   - Cannot be filtered regardless of LOF score

---

## **Phase 4: Spatial LOF Calculation**
Calculate traditional LOF for each non-protected AP.

### Requirements:
1. For each AP, find its k nearest neighbors (k from Phase 2.1)
2. Calculate Local Reachability Density (LRD)
3. Compare AP's density to its neighbors' average density

**Critical Code Sample** (since LOF calculation is complex):
```python
def calculate_lof(ap, all_aps, k):
    # Find k nearest neighbors
    neighbors = get_k_nearest(ap, all_aps, k)
    
    # Calculate reachability distances
    reach_distances = []
    for neighbor in neighbors:
        # Reachability = max(actual_distance, neighbor's k-distance)
        reach_dist = max(
            euclidean_distance(ap, neighbor),
            neighbor.k_distance
        )
        reach_distances.append(reach_dist)
    
    # Local Reachability Density = 1 / avg_reach_distance
    lrd_ap = 1.0 / mean(reach_distances)
    
    # LOF = avg(neighbor_lrd) / lrd_ap
    neighbor_lrds = [calculate_lrd(n, all_aps, k) for n in neighbors]
    lof = mean(neighbor_lrds) / lrd_ap
    
    return lof
```

---

## **Phase 5: Signal Justification Analysis**
Determine if an AP's signal strength justifies its spatial isolation.

### Requirements:

#### **5.1 Absolute Signal Check**
If RSSI > -60 dBm → Automatically justified (very strong signal)

#### **5.2 Statistical Outlier Check**
Calculate z-score of AP's RSSI relative to all APs:
- Dense environment: Justified if z-score > 2.0
- Moderate environment: Justified if z-score > 1.5
- Sparse environment: Justified if z-score > 1.0

**Critical Formula:**
```
z_score = (ap_rssi - mean_rssi) / std_rssi
```

#### **5.3 Local Maximum Check**
Check if AP is strongest within its local area:
- Dense: Check within 50m radius
- Moderate: Check within 30m radius
- Sparse: Check within 20m radius
- Justified if AP is 5-15 dBm stronger than local neighbors (threshold depends on RSSI range)

#### **5.4 Signal-Distance Consistency**
Compare observed RSSI to expected RSSI based on distance:
- Use simplified path loss model: `Expected_RSSI = -30 - 25*log10(distance)`
- Justified if observed > expected + 10 dBm

---

## **Phase 6: Two-Factor Classification**
Classify each AP based on LOF score and signal justification.

### Decision Matrix:

| LOF Score | Signal Justified | Trust Level | Position Weight |
|-----------|-----------------|-------------|-----------------|
| < Low_Threshold | Any | HIGH | 1.5 (1.0 if weak signal) |
| Low_Threshold to High_Threshold | Yes | MEDIUM | 1.2 |
| Low_Threshold to High_Threshold | No | LOW | 0.7 |
| > High_Threshold | Yes | MEDIUM_ISOLATED | 1.0 |
| > High_Threshold | No | OUTLIER | 0.3 |

*Note: Thresholds from Phase 2.2*

---

## **Phase 7: Graduated Filtering**
Build final AP list using trust levels.

### Requirements:

#### **7.1 Priority Order**
1. Include all PROTECTED APs
2. Include all HIGH trust APs
3. Include MEDIUM and MEDIUM_ISOLATED APs
4. If below minimum count, add LOW trust APs (sorted by RSSI)
5. If still below minimum, add OUTLIER APs (sorted by RSSI)

#### **7.2 Minimum AP Guarantee**
Never return fewer than the minimum required APs (from Phase 2.4)

#### **7.3 Weight Adjustment**
If forced to include outliers for minimum count, reduce their weight to 0.2

---

## **Phase 8: Output Generation**

### Final Output for Each AP:
```python
{
    'bssid': 'xx:xx:xx:xx:xx:xx',
    'rssi': -65,
    'location': (x, y),
    'trust_level': 'HIGH',  # or PROTECTED, MEDIUM, LOW, OUTLIER
    'position_weight': 1.5,  # Weight for positioning calculation
    'spatial_lof': 1.2,      # LOF score
    'is_protected': False,   # True for top-N strongest
    'isolation_justified': True  # True if signal justifies isolation
}
```

---

## **Key Configuration Parameters**

```yaml
adaptive_lof_config:
  # Environment thresholds
  dense_ap_count: 20
  moderate_ap_count: 10
  
  # Signal strength categories (dBm)
  strong_signal: -70
  medium_signal: -80
  
  # Spatial spread categories (meters)
  large_area: 100
  medium_area: 50
  
  # Protection rules
  max_protected: 5
  min_protected: 2
  
  # Minimum APs for positioning
  min_aps_sparse: 3
  min_aps_moderate: 4
  min_aps_dense: 6
  
  # Path loss model constants
  reference_power: -30  # dBm at 1 meter
  path_loss_exponent: 2.5  # Indoor typical
```

---

## **Critical Insights**

1. **Why Adaptive Parameters Matter**: Different indoor environments have varying structures requiring environment-specific parameter adaptation

2. **Core Innovation**: The two-factor approach recognizes that **spatial isolation + strong signal = likely closest AP**, not an outlier

3. **Graduated Trust**: Using weights instead of binary filtering preserves information while reducing impact of unreliable APs

4. **Environment Awareness**: Dense environments can afford aggressive filtering; sparse environments must preserve most APs

5. **Signal Protection**: The strongest signals are most likely to be from nearby APs, regardless of spatial distribution

---

## **Implementation Checklist**

- [ ] Implement environment analysis (Phase 1)
- [ ] Create parameter adaptation logic (Phase 2)
- [ ] Implement strong signal protection (Phase 3)
- [ ] Add spatial LOF calculation (Phase 4)
- [ ] Implement signal justification checks (Phase 5)
- [ ] Create two-factor decision matrix (Phase 6)
- [ ] Implement graduated filtering with minimum guarantee (Phase 7)
- [ ] Generate weighted output for positioning (Phase 8)
- [ ] Add logging for debugging and monitoring
- [ ] Test with various environment types (dense office, sparse warehouse, etc.)

This requirements specification provides the complete process with adaptive parameters, explaining why each decision is made while keeping code samples minimal and focused only on the most complex calculations.