package com.wifi.scan.consume.health;

import com.wifi.scan.consume.service.KafkaMonitoringService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Health indicator for Kafka consumer group status and partition assignments.
 * Checks if the consumer is properly registered with the consumer group and has partition assignments.
 */
@Slf4j
@Component("kafkaConsumerGroup")
public class KafkaConsumerGroupHealthIndicator implements HealthIndicator {

    private final KafkaMonitoringService kafkaMonitoringService;

    @Autowired
    public KafkaConsumerGroupHealthIndicator(KafkaMonitoringService kafkaMonitoringService) {
        this.kafkaMonitoringService = kafkaMonitoringService;
    }

    @Override
    public Health health() {
        try {
            log.debug("Checking Kafka consumer group health");
            
            boolean isConsumerConnected = kafkaMonitoringService.isConsumerConnected();
            boolean isConsumerGroupActive = kafkaMonitoringService.isConsumerGroupActive();
            
            Health.Builder healthBuilder = Health.up();
            
            if (!isConsumerConnected) {
                healthBuilder = Health.down()
                        .withDetail("reason", "Consumer is not connected to Kafka cluster");
            } else if (!isConsumerGroupActive) {
                healthBuilder = Health.down()
                        .withDetail("reason", "Consumer group is not active in Kafka cluster");
            }
            
            return healthBuilder
                    .withDetail("consumerConnected", isConsumerConnected)
                    .withDetail("consumerGroupActive", isConsumerGroupActive)
                    .withDetail("clusterNodeCount", kafkaMonitoringService.getClusterNodeCount())
                    .withDetail("checkTimestamp", System.currentTimeMillis())
                    .build();
            
        } catch (Exception e) {
            log.error("Error checking Kafka consumer group health", e);
            return Health.down()
                    .withDetail("error", e.getMessage())
                    .withDetail("checkTimestamp", System.currentTimeMillis())
                    .build();
        }
    }
} 