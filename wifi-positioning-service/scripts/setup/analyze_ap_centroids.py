#!/usr/bin/env python3
"""
Analyze access point coordinates to determine which need adjustment for centroid filtering.
Calculates geographic centroids using ECEF vector averaging and distances for each test case.

This tool is essential for validating new test cases and ensuring they pass without cell tower information.
"""

import math
from typing import List, Tuple, Dict

def to_radians(degrees: float) -> float:
    """Convert degrees to radians."""
    return degrees * math.pi / 180

def to_degrees(radians: float) -> float:
    """Convert radians to degrees."""
    return radians * 180 / math.pi

def calculate_distance(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    """
    Calculate distance between two points using Haversine formula.
    Returns distance in meters.
    """
    R = 6371000  # Earth radius in meters
    
    lat1_rad = to_radians(lat1)
    lat2_rad = to_radians(lat2)
    delta_lat = to_radians(lat2 - lat1)
    delta_lon = to_radians(lon2 - lon1)
    
    a = (math.sin(delta_lat / 2) ** 2 +
         math.cos(lat1_rad) * math.cos(lat2_rad) *
         math.sin(delta_lon / 2) ** 2)
    c = 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))
    
    return R * c

def calculate_geographic_centroid(coords: List[Tuple[float, float]]) -> Tuple[float, float]:
    """
    Calculate geographic centroid using ECEF vector averaging.
    This matches the implementation in GeographicCentroidCalculator.java
    
    Args:
        coords: List of (latitude, longitude) tuples
    
    Returns:
        (latitude, longitude) of centroid
    """
    if not coords:
        return None
    
    # Convert to ECEF vectors and sum
    sum_x = 0.0
    sum_y = 0.0
    sum_z = 0.0
    
    for lat, lon in coords:
        lat_rad = to_radians(lat)
        lon_rad = to_radians(lon)
        
        # Convert to ECEF unit vector
        x = math.cos(lat_rad) * math.cos(lon_rad)
        y = math.cos(lat_rad) * math.sin(lon_rad)
        z = math.sin(lat_rad)
        
        sum_x += x
        sum_y += y
        sum_z += z
    
    # Normalize
    magnitude = math.sqrt(sum_x**2 + sum_y**2 + sum_z**2)
    if magnitude == 0:
        return None
    
    norm_x = sum_x / magnitude
    norm_y = sum_y / magnitude
    norm_z = sum_z / magnitude
    
    # Convert back to lat/lon
    result_lat = to_degrees(math.asin(norm_z))
    result_lon = to_degrees(math.atan2(norm_y, norm_x))
    
    return (result_lat, result_lon)

# Current AP coordinates from wifi-positioning-test-data.sh
AP_COORDS = {
    "00:11:22:33:44:01": (37.7749, -122.4194),
    "00:11:22:33:44:02": (37.7750, -122.4195),
    "00:11:22:33:44:03": (37.7751, -122.4196),
    "00:11:22:33:44:04": (37.7752, -122.4197),
    "00:11:22:33:44:05": (37.7753, -122.4198),
    "00:11:22:33:44:06": (37.7754, -122.4194),  # Collinear start
    "00:11:22:33:44:07": (37.7755, -122.4194),
    "00:11:22:33:44:08": (37.7756, -122.4194),
    "00:11:22:33:44:09": (37.7757, -122.4194),
    "00:11:22:33:44:10": (37.7758, -122.4194),
    "00:11:22:33:44:11": (37.7760, -122.4200),
    "00:11:22:33:44:12": (37.7762, -122.4202),
    "00:11:22:33:44:13": (37.7764, -122.4204),
    "00:11:22:33:44:14": (37.7766, -122.4206),
    "00:11:22:33:44:15": (37.7768, -122.4208),
    "00:11:22:33:44:16": (37.7770, -122.4210),
    "00:11:22:33:44:17": (37.7773, -122.4211),
    "00:11:22:33:44:18": (37.7776, -122.4212),
    "00:11:22:33:44:19": (37.7779, -122.4213),
    "00:11:22:33:44:20": (37.7782, -122.4214),
    "00:11:22:33:44:21": (37.7780, -122.4220),
    "00:11:22:33:44:22": (37.7780, -122.4220),  # Same location
    "00:11:22:33:44:23": (37.7780, -122.4220),
    "00:11:22:33:44:24": (37.7780, -122.4220),
    "00:11:22:33:44:25": (37.7780, -122.4220),
    "00:11:22:33:44:26": (37.7791, -122.4230),
    "00:11:22:33:44:27": (37.7792, -122.4230),
    "00:11:22:33:44:28": (37.7793, -122.4230),
    "00:11:22:33:44:29": (37.7794, -122.4230),
    "00:11:22:33:44:30": (37.7795, -122.4230),
    "00:11:22:33:44:31": (37.7800, -122.4240),
    "00:11:22:33:44:32": (37.7800, -122.4240),  # Same location
    "00:11:22:33:44:36": (91.0, -182.0),  # Invalid coordinates (error case)
    "00:11:22:33:44:41": (37.7820, -122.4260),
    "00:11:22:33:44:42": (37.7820, -122.4260),
    "00:11:22:33:44:43": (37.7820, -122.4260),
    "00:11:22:33:44:44": (37.7820, -122.4260),
    "00:11:22:33:44:45": (37.7820, -122.4260),
    "00:11:22:33:44:55": (37.7844, -122.4276),
    "AA:BB:CC:00:00:50": (37.7750, -122.4194),
    "AA:BB:CC:00:00:51": (37.7750, -122.4194),
    "AA:BB:CC:00:00:52": (37.7755, -122.4199),
    "AA:BB:CC:00:00:53": (37.7755, -122.4196),
    "AA:BB:CC:00:00:54": (37.7763, -122.4204),
    "AA:BB:CC:00:00:55": (37.7771, -122.4212),
    "AA:BB:CC:00:00:56": (37.7760, -122.4195),
    "AA:BB:CC:00:00:57": (37.7765, -122.4190),
}

# Test case groupings (which APs are used together)
TEST_CASES = {
    "Test 1 (Single AP)": ["00:11:22:33:44:01"],
    "Test 2 (Two APs)": ["00:11:22:33:44:02", "00:11:22:33:44:03"],
    "Test 3 (Three APs)": ["00:11:22:33:44:03", "00:11:22:33:44:04", "00:11:22:33:44:05"],
    "Test 4 (Multiple APs)": ["00:11:22:33:44:04", "00:11:22:33:44:05", "00:11:22:33:44:06", "00:11:22:33:44:07"],
    "Test 5 (Weak Signal)": ["00:11:22:33:44:05"],
    "Test 6-10 (Collinear)": ["00:11:22:33:44:06", "00:11:22:33:44:07", "00:11:22:33:44:08"],
    "Test 11-15 (High Density)": ["00:11:22:33:44:11", "00:11:22:33:44:12", "00:11:22:33:44:13", "00:11:22:33:44:14"],
    "Test 16-20 (Mixed Signal)": ["00:11:22:33:44:16", "00:11:22:33:44:17", "00:11:22:33:44:18"],
    "Test 21-25 (Time Series)": ["00:11:22:33:44:21", "00:11:22:33:44:22"],
    "Test 26-30 (Path Loss)": ["00:11:22:33:44:26", "00:11:22:33:44:27"],
    "Test 31-35 (Stable)": ["00:11:22:33:44:31", "00:11:22:33:44:32"],
    "Test 36 (Invalid)": ["00:11:22:33:44:36"],
    "Test 38 (Very Weak)": ["00:11:22:33:44:55"],
    "Test 40 (Status Filter)": ["00:11:22:33:44:41", "00:11:22:33:44:42", "00:11:22:33:44:43", "00:11:22:33:44:44", "00:11:22:33:44:45"],
    "Test Missing Freq": ["00:11:22:33:44:04", "00:11:22:33:44:05", "00:11:22:33:44:06", "00:11:22:33:44:07"],
    "Test 2D-1 (Single AP)": ["AA:BB:CC:00:00:50"],
    "Test 2D-2 (Two APs)": ["AA:BB:CC:00:00:51", "AA:BB:CC:00:00:52"],
    "Test 2D-3 (Three APs)": ["AA:BB:CC:00:00:53", "AA:BB:CC:00:00:54", "AA:BB:CC:00:00:55"],
    "Test Mixed 2D/3D": ["AA:BB:CC:00:00:56", "AA:BB:CC:00:00:57"],
}

def analyze_test_case(name: str, mac_addresses: List[str]) -> Dict:
    """Analyze a test case and return centroid, distances, and recommendations."""
    # Skip single AP test cases (no centroid filtering)
    if len(mac_addresses) == 1:
        return {
            "name": name,
            "ap_count": 1,
            "needs_adjustment": False,
            "reason": "Single AP - no centroid filtering applied"
        }
    
    # Get coordinates
    coords = []
    for mac in mac_addresses:
        if mac in AP_COORDS:
            coords.append(AP_COORDS[mac])
        else:
            print(f"Warning: {mac} not found in AP_COORDS")
    
    if len(coords) < 2:
        return {
            "name": name,
            "ap_count": len(coords),
            "needs_adjustment": False,
            "reason": "Not enough valid coordinates"
        }
    
    # Calculate centroid
    centroid = calculate_geographic_centroid(coords)
    
    # Calculate distances from centroid
    distances = []
    max_distance = 0
    max_mac = None
    
    for i, mac in enumerate(mac_addresses):
        if i < len(coords):
            lat, lon = coords[i]
            dist = calculate_distance(centroid[0], centroid[1], lat, lon)
            distances.append((mac, dist))
            if dist > max_distance:
                max_distance = dist
                max_mac = mac
    
    # Determine if adjustment needed (400m threshold with 100m safety margin)
    needs_adjustment = max_distance > 400
    
    return {
        "name": name,
        "ap_count": len(mac_addresses),
        "mac_addresses": mac_addresses,
        "centroid": centroid,
        "distances": distances,
        "max_distance": max_distance,
        "max_mac": max_mac,
        "needs_adjustment": needs_adjustment,
        "reason": f"Max distance: {max_distance:.1f}m {'> 400m NEEDS ADJUSTMENT' if needs_adjustment else '<= 400m OK'}"
    }

def main():
    """Analyze all test cases and print results."""
    print("=" * 80)
    print("ACCESS POINT CENTROID ANALYSIS")
    print("=" * 80)
    print()
    
    results = []
    needs_adjustment_count = 0
    
    for test_name, mac_list in TEST_CASES.items():
        result = analyze_test_case(test_name, mac_list)
        results.append(result)
        
        print(f"{result['name']}")
        print(f"  APs: {result['ap_count']}")
        
        if result['ap_count'] == 1:
            print(f"  Status: ✓ {result['reason']}")
        else:
            print(f"  Centroid: ({result['centroid'][0]:.6f}, {result['centroid'][1]:.6f})")
            print(f"  Max Distance: {result['max_distance']:.1f}m from {result['max_mac']}")
            
            status_symbol = "✗" if result['needs_adjustment'] else "✓"
            print(f"  Status: {status_symbol} {result['reason']}")
            
            if result['needs_adjustment']:
                needs_adjustment_count += 1
                print(f"  Distances:")
                for mac, dist in result['distances']:
                    marker = " ← EXCEEDS 400m" if dist > 400 else ""
                    print(f"    {mac}: {dist:.1f}m{marker}")
        
        print()
    
    print("=" * 80)
    print(f"SUMMARY: {needs_adjustment_count} test cases need coordinate adjustment")
    print("=" * 80)
    
    # Identify shared APs
    print("\nSHARED APs (appear in multiple test cases):")
    ap_usage = {}
    for test_name, mac_list in TEST_CASES.items():
        for mac in mac_list:
            if mac not in ap_usage:
                ap_usage[mac] = []
            ap_usage[mac].append(test_name)
    
    shared_aps = {mac: tests for mac, tests in ap_usage.items() if len(tests) > 1}
    for mac, tests in sorted(shared_aps.items()):
        print(f"  {mac}: {len(tests)} test cases")
        for test in tests:
            print(f"    - {test}")
        print()

if __name__ == "__main__":
    main()

