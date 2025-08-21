

1. Input data quality 
  
   - *** service to do ***   
    - print CalculationInfo.SelectionContextInfo
    - print number of total AP  

   - *** Splunk to report *** 

    - Pie chart of each selection context 
    - Mean , Median, MAX and MIN  of AP per request.
    - Pei chart  requet with AP 1,2,3,4,4+

2. AP Data quiality  - 

 - *** service to do ***   
    - if Frisco reported location with calculation info
         -  print the calculationInfo.accessPoints and calculationInfo.accessPointSummary and  caculate  used accessPointSummary
         - print the calculationInfo status ration : status acount / total AP ???

    -  if Frisco reported error "No known access points found in database" or  "No access points with valid status found"    
        - print calculationInfo.accessPointSummary  
        
              accessPointSummary": {
               "total": AP count from the reuest ,
               "used": 0,
               "statusCounts": [
                 {
                   "status": "unknown",
                   "count": AP count from the reuest
                 }
                 ]
                }



  - *** Splunk to report *** 
  
   - Mean , Median, MAX and MIN  Number of AP location used  
   - Pei chart AP location used  1,2,3,4,4+
   - Pei chart AP location status from ratio ???
   - 
   used AP counts by status . meaning breakdown of active/imported/confirmed status of used APS -  have to derived and print from calculationInfo.accessPoints 
   ALL AP count by status  -  from calculationInfo.accessPointSummary
   
   
3. Algorithm Usage 
 - *** service to do ***  
    - print Used Algorithms    
   
    *** Splunk to report *** 
        - Pie chart of Algorithm types

4. Frisco Service  Performace 

  - *** service to do ***  
       - if Frisco report location 
        - print location , accuracy and confidence
       - if Frisco fails to report location     
        - print error , accuracy=0 and confidence=0
    
 -  *** Splunk to report ***      
    - % of success reponse 
    - Mean , Median, MAX and MIN accuracy 
    - Mean , Median, MAX and MIN confidence 
   
   
   
5. *** VLSS VS Frisco Service Performance ***

    - When both services report location:
      - Location Type = WIFI
      - Distance = haversine_distance(VLSS_lat_lon, Frisco_lat_lon)

      - If Distance = 0:
        - Agreement Analysis = PERFECT AGREEMENT

      - Else if VLSS accuracy >= 250:
        - Agreement Analysis = WIFI VS CELL DISAGREEMENT

      - Else:
        - Expected uncertainty = √(VLSS_accuracy² + Frisco_accuracy²)

        - If Distance < Expected uncertainty:
          - Agreement Analysis = GOOD AGREEMENT

        - Else: (Distance > Expected uncertainty)
          - Calculate ratios for FRISCO:       
            - Frisco_Ratio = Distance / Frisco_Accuracy 
          - Identify more overconfident service (higher ratio):
              - 0 < ratio ≤ 1.0: Agreement Analysis = FRISCO WITHIN BOUNDS
              - 1.0 < ratio ≤ 1.5: Agreement Analysis = FRISCO MODERATELY OVERCONFIDENT
              - 1.5 < ratio ≤ 2.5: Agreement Analysis = FRISCO OVERCONFIDENT
              - ratio > 2.5: Agreement Analysis = FRISCO EXTREMELY OVERCONFIDENT

    - When VLSS reports but Frisco reports error "No known access points found in database" or  "No access points with valid status found" 
      -  if VLSS VLSS accuracy >= 250:
         - Location Type = CELL
         - Distance = N/A (not 0, since only one position exists)
         - Agreement Analysis =  = NO WIFI COVERAGE
      -  else if VLSS VLSS accuracy < 250:
         - Location Type = WIFI
         - Distance = N/A (not 0, since only one position exists)
         - Agreement Analysis =  = FRISCO MISSING AP

    - When VLSS reports but Frisco has other errors:
      - Location Type = (VLSS accuracy < 250) ? WIFI : CELL
      - Distance = N/A
      - Agreement Analysis =  = (VLSS accuracy < 250) ? FRISCO FAILURE : NO WIFI COVERAGE   



 - *** Splunk to report *** 
    -  On Location Type
     - Pie chart of Location Type
    - On Distance:
     - Aggregate statistics:
        - Mean inter-service distance: 42m
        - Median inter-service distance: 35m  
        - 90th percentile distance: 95m
        - Maximum distance: 180m
        - Standard deviation of inter-service distance
        - Standard deviation of inter-service distance by AP count 1,2,3,4,4+

    - On Expected uncertainty:  
      - Pie chart of Accuracy Confidence
      
    -  mean, mdedian, min, max  Accuracy by AP count 1,2,3,4,4+ for both service   
    -  mean, mdedian, min, max  Accuracy by AP GeometricQualityFactor  EXCELLENT_GDOP, GOOD_GDOP, FAIR_GDOP, POOR_GDOP for both service  
    -  mean, mdedian, min, max  Accuracy by AP SignalQualityFactor  STRONG_SIGNAL, MEDIUM_SIGNAL, WEAK_SIGNAL, VERY_WEAK_SIGNAL for both service  
    -  mean, mdedian, min, max  Accuracy by AP SignalDistributionFactor  UNIFORM_SIGNALS, MIXED_SIGNALS, SIGNAL_OUTLIERS for both service  
    
    - Aggreement Rate 
        - total number of Distance  bellow 5 / total number  of  Location Type = wifi * 100
        - total number of Distance  bellow 10 / total number  of  Location Type = wifi * 100
        - total number of Distance  bellow 20 / total number  of  Location Type = wifi * 100
        - total number of Distance  bellow 30 / total number  of  Location Type = wifi * 100
        - total number of Distance  bellow 50 / total number  of  Location Type = wifi * 100
        - total number of Distance  bellow 100 / total number  of  Location Type = wifi * 100
        
    
    - Correlation between Frisco confidence and inter-service distance
     - *** splunk query sample ***
          - | stats 
        avg(frisco_confidence) as mean_conf,
        avg(inter_service_distance) as mean_dist,
        sum(eval((frisco_confidence - mean_conf) * (inter_service_distance - mean_dist))) as numerator,
        sum(eval(pow(frisco_confidence - mean_conf, 2))) as sum_sq_conf,
        sum(eval(pow(inter_service_distance - mean_dist, 2))) as sum_sq_dist
    | eval correlation = numerator / sqrt(sum_sq_conf * sum_sq_dist)    
    
    - *** interpretation ***
    
    
       | Correlation  Value   |  Interpretation                 |   What it means for Frisco                   |
       | ---------------------|---------------------------------|----------------------------------------------|
         0.7 to  -1.0           Strong negative                    Excellent! High confidence = good agreement 
        -0.4 to -0.7          Moderate negative                    Good - confidence is meaningful
        -0.2 to -0.4          Weak negative                        Some relationship but not strong
        -0.2 to +0.2          No correlation                       Confidence doesn't predict agreement
        +0.2 to +1.0          PositiveProblem!                     High confidence = poor agreement
  
      