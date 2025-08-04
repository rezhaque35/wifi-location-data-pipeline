#!/bin/bash
# wifi-scan-ingestion/firehose-ingestion/scripts/start-localstack.sh
# Start LocalStack container with required services

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
CONTAINER_NAME="wifi-localstack"
LOCALSTACK_VERSION="latest"
LOCALSTACK_PORT="4566"
SERVICES="kinesis,firehose,s3,events,sqs,iam,logs,sts"

# Function to print colored output
print_status() {
    local status=$1
    local message=$2
    case $status in
        "INFO")  echo -e "${BLUE}[INFO]${NC} $message" ;;
        "SUCCESS") echo -e "${GREEN}[SUCCESS]${NC} $message" ;;
        "WARNING") echo -e "${YELLOW}[WARNING]${NC} $message" ;;
        "ERROR") echo -e "${RED}[ERROR]${NC} $message" ;;
    esac
}

# Function to check if Docker is running
check_docker() {
    if ! docker info >/dev/null 2>&1; then
        print_status "ERROR" "Docker is not running. Please start Docker Desktop/service first"
        print_status "INFO" "On macOS: Open Docker Desktop application"
        print_status "INFO" "On Linux: sudo systemctl start docker"
        return 1
    fi
    print_status "SUCCESS" "Docker is running"
}

# Function to check if container exists
container_exists() {
    docker ps -a --format "{{.Names}}" | grep -q "^${CONTAINER_NAME}$"
}

# Function to check if container is running
container_running() {
    docker ps --format "{{.Names}}" | grep -q "^${CONTAINER_NAME}$"
}

# Function to check LocalStack health
check_localstack_health() {
    local max_attempts=30
    local attempt=1
    
    print_status "INFO" "Waiting for LocalStack to be ready..."
    
    while [ $attempt -le $max_attempts ]; do
        if curl -s http://localhost:${LOCALSTACK_PORT}/_localstack/health >/dev/null 2>&1; then
            print_status "SUCCESS" "LocalStack is healthy and ready"
            return 0
        fi
        
        echo -n "."
        sleep 2
        attempt=$((attempt + 1))
    done
    
    print_status "ERROR" "LocalStack health check failed after ${max_attempts} attempts"
    return 1
}

# Function to verify required services
verify_services() {
    print_status "INFO" "Verifying required services are available..."
    
    local health_response
    health_response=$(curl -s http://localhost:${LOCALSTACK_PORT}/_localstack/health || echo "")
    
    if [ -z "$health_response" ]; then
        print_status "ERROR" "Unable to get LocalStack health status"
        return 1
    fi
    
    # Parse services from response
    local required_services=(s3 kinesis firehose sqs iam events logs sts)
    local missing_services=()
    
    for service in "${required_services[@]}"; do
        if ! echo "$health_response" | jq -r ".services.${service}" | grep -q "available"; then
            missing_services+=("$service")
        fi
    done
    
    if [ ${#missing_services[@]} -gt 0 ]; then
        print_status "WARNING" "Some services are not available: ${missing_services[*]}"
        print_status "INFO" "This may be normal for LocalStack community edition"
    else
        print_status "SUCCESS" "All required services are available"
    fi
    
    # Display service status
    print_status "INFO" "Service status:"
    echo "$health_response" | jq '.services' 2>/dev/null || echo "$health_response"
}

# Function to start LocalStack container
start_localstack() {
    print_status "INFO" "Starting LocalStack container: $CONTAINER_NAME"
    
    docker run -d \
        --name "$CONTAINER_NAME" \
        -p ${LOCALSTACK_PORT}:4566 \
        -e SERVICES="$SERVICES" \
        -e DEBUG=1 \
        -e PERSISTENCE=0 \
        -e LAMBDA_EXECUTOR=docker \
        -e DOCKER_HOST=unix:///var/run/docker.sock \
        -v /var/run/docker.sock:/var/run/docker.sock \
        localstack/localstack:${LOCALSTACK_VERSION}
    
    print_status "SUCCESS" "LocalStack container started"
}

# Function to stop existing container
stop_container() {
    print_status "INFO" "Stopping existing LocalStack container"
    docker stop "$CONTAINER_NAME" >/dev/null 2>&1 || true
    docker rm "$CONTAINER_NAME" >/dev/null 2>&1 || true
    print_status "SUCCESS" "Existing container stopped and removed"
}

# Function to pull LocalStack image
pull_localstack_image() {
    print_status "INFO" "Pulling LocalStack image: localstack/localstack:${LOCALSTACK_VERSION}"
    docker pull localstack/localstack:${LOCALSTACK_VERSION}
    print_status "SUCCESS" "LocalStack image pulled"
}

# Main function
main() {
    print_status "INFO" "Starting LocalStack setup..."
    
    # Check Docker
    if ! check_docker; then
        exit 1
    fi
    
    # Check if container already exists and is running
    if container_running; then
        print_status "INFO" "LocalStack container is already running"
        
        # Check health
        if check_localstack_health; then
            verify_services
            print_status "SUCCESS" "LocalStack is already running and healthy"
            return 0
        else
            print_status "WARNING" "LocalStack container is running but not healthy, restarting..."
            stop_container
        fi
    elif container_exists; then
        print_status "INFO" "LocalStack container exists but is not running, removing..."
        stop_container
    fi
    
    # Pull latest image
    pull_localstack_image
    
    # Start LocalStack
    start_localstack
    
    # Wait for health check
    if check_localstack_health; then
        verify_services
        print_status "SUCCESS" "LocalStack started successfully"
        
        # Display connection info
        print_status "INFO" "LocalStack endpoints:"
        print_status "INFO" "  Health: http://localhost:${LOCALSTACK_PORT}/_localstack/health"
        print_status "INFO" "  AWS endpoint: http://localhost:${LOCALSTACK_PORT}"
        print_status "INFO" "  Container logs: docker logs $CONTAINER_NAME"
        print_status "INFO" "  Stop container: docker stop $CONTAINER_NAME"
    else
        print_status "ERROR" "LocalStack failed to start properly"
        print_status "INFO" "Check container logs with: docker logs $CONTAINER_NAME"
        exit 1
    fi
}

# Run main function
main "$@" 