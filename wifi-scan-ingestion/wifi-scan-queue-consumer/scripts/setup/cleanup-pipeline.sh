#!/bin/bash

# cleanup-pipeline.sh
# Cleanup script for the entire data pipeline (ingestion + store stages)

set -e

# Get script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

# Configuration
LOCALSTACK_ENDPOINT="http://localhost:4566"
AWS_REGION="us-east-1"

export AWS_ACCESS_KEY_ID="test"
export AWS_SECRET_ACCESS_KEY="test"
export AWS_DEFAULT_REGION="$AWS_REGION"

print_step() {
    echo -e "${GREEN}==>${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

# Parse arguments
FULL_CLEANUP=false
if [ "$1" = "--full" ]; then
    FULL_CLEANUP=true
fi

echo -e "${CYAN}╔════════════════════════════════════════╗${NC}"
echo -e "${CYAN}║     DATA PIPELINE CLEANUP SCRIPT       ║${NC}"
echo -e "${CYAN}╚════════════════════════════════════════╝${NC}"
echo ""

if [ "$FULL_CLEANUP" = true ]; then
    print_warning "Full cleanup mode - will remove all containers and data"
else
    print_step "Standard cleanup mode (use --full for complete cleanup)"
fi

echo ""

# Stop Java services
print_step "Checking for running Java services..."
if pgrep -f "spring-boot" > /dev/null; then
    print_warning "Found running Spring Boot services"
    echo "Please stop them manually:"
    ps aux | grep "[s]pring-boot" | awk '{print "  PID "$2": "$11}'
    read -p "Stop these services now? (y/n) " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        pkill -f "spring-boot" || true
        print_success "Services stopped"
    fi
else
    print_success "No Java services running"
fi

# Clean up LocalStack data
if curl -s $LOCALSTACK_ENDPOINT/health > /dev/null 2>&1; then
    print_step "Cleaning up LocalStack resources..."
    
    # Delete SQS queues
    print_step "Deleting SQS queues..."
    for queue in "wifi-scan-events" "wifi-scan-events-dlq"; do
        aws --endpoint-url=$LOCALSTACK_ENDPOINT sqs delete-queue \
            --queue-url "$LOCALSTACK_ENDPOINT/000000000000/$queue" 2>/dev/null || true
    done
    
    # Delete S3 buckets and contents
    print_step "Deleting S3 buckets..."
    for bucket in "wifi-scan-data-bucket" "wifi-measurements-table"; do
        aws --endpoint-url=$LOCALSTACK_ENDPOINT s3 rb s3://$bucket --force 2>/dev/null || true
    done
    
    # Delete Firehose streams
    print_step "Deleting Firehose streams..."
    for stream in "MVS-stream" "wifi-measurements-stream"; do
        aws --endpoint-url=$LOCALSTACK_ENDPOINT firehose delete-delivery-stream \
            --delivery-stream-name "$stream" 2>/dev/null || true
    done
    
    print_success "LocalStack resources cleaned up"
fi

# Stop Kafka
print_step "Stopping Kafka cluster..."
cd "$SCRIPT_DIR"
./stop-local-kafka.sh 2>/dev/null || true
print_success "Kafka cluster stopped"

# Stop LocalStack
print_step "Stopping LocalStack..."
docker stop localstack 2>/dev/null || true
docker stop localstack-wifi-transformer 2>/dev/null || true
docker-compose -f docker-compose-localstack.yml down 2>/dev/null || true
print_success "LocalStack stopped"

# Full cleanup
if [ "$FULL_CLEANUP" = true ]; then
    print_step "Performing full cleanup..."
    
    # Remove containers
    print_step "Removing containers..."
    docker rm -f localstack localstack-wifi-transformer kafka zookeeper 2>/dev/null || true
    
    # Remove volumes
    print_step "Removing volumes..."
    docker volume prune -f
    
    # Clean Docker system
    print_step "Pruning Docker system..."
    docker system prune -af
    
    # Clean up SSL certificates (optional)
    read -p "Remove SSL certificates? (y/n) " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        rm -rf "$SCRIPT_DIR/../../src/main/resources/ssl" 2>/dev/null || true
        print_success "SSL certificates removed"
    fi
    
    print_success "Full cleanup completed"
else
    print_success "Standard cleanup completed"
fi

echo ""
echo -e "${CYAN}Cleanup Summary:${NC}"
echo "  ✓ Java services checked"
echo "  ✓ LocalStack resources deleted"
echo "  ✓ Kafka cluster stopped"
echo "  ✓ LocalStack stopped"

if [ "$FULL_CLEANUP" = true ]; then
    echo "  ✓ Containers removed"
    echo "  ✓ Volumes cleaned"
    echo "  ✓ Docker system pruned"
fi

echo ""
echo -e "${GREEN}Pipeline cleanup completed!${NC}"
echo ""
echo "To restart the pipeline, run:"
echo "  ./setup-ingestion-store-stage.sh"

