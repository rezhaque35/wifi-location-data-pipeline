# Health Checks Configuration

This document explains the health check configuration for the WiFi Measurements Transformer Service.

## Health Check Endpoints

The service provides the following health check endpoints:

### 1. Liveness Probe (`/actuator/health/liveness`)
- **Purpose**: Determines if the application is alive and responsive
- **Components Checked**:
  - `livenessState`: Application state (UP/DOWN)
  - `memory`: Memory usage and availability
- **When to Use**: Kubernetes liveness probe, load balancer health checks

### 2. Readiness Probe (`/actuator/health/readiness`)
- **Purpose**: Determines if the application is ready to receive traffic
- **Components Checked**:
  - `readinessState`: Application readiness state
  - `sqs`: SQS queue connectivity and status
  - `firehose`: Firehose delivery stream status
- **When to Use**: Kubernetes readiness probe, service mesh health checks

### 3. Overall Health (`/actuator/health`)
- **Purpose**: Comprehensive health status with detailed component information
- **Components Checked**: All health indicators
- **When to Use**: Manual health checks, monitoring dashboards

## Configuration in `application.yml`

```yaml
management:
  health:
    group:
      liveness:
        include:
          - livenessState
          - memory
        show-details: always
      readiness:
        include:
          - readinessState
          - sqs
          - firehose
        show-details: always
```

## Health Indicators

### Built-in Indicators
- **livenessState**: Spring Boot's built-in liveness state
- **readinessState**: Spring Boot's built-in readiness state
- **memory**: Memory usage monitoring

### Custom Indicators
- **sqs**: Custom SQS health indicator checking queue connectivity
- **firehose**: Custom Firehose health indicator checking delivery stream status

## Usage Examples

### Check Liveness
```bash
curl http://localhost:8081/actuator/health/liveness
```

### Check Readiness
```bash
curl http://localhost:8081/actuator/health/readiness
```

### Check Overall Health
```bash
curl http://localhost:8081/actuator/health
```

## Response Format

Health checks return JSON responses with the following structure:

```json
{
  "status": "UP",
  "components": {
    "livenessState": {
      "status": "UP"
    },
    "memory": {
      "status": "UP",
      "details": {
        "free": 123456789,
        "total": 987654321
      }
    }
  }
}
```

## Monitoring Integration

The health endpoints are designed to work with:
- Kubernetes probes
- Load balancers
- Service mesh health checks
- Monitoring systems (Prometheus, etc.) 