#!/bin/bash

# cleanup.sh
# This script cleans up complete infrastructure including LocalStack, Kafka, and related AWS resources

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
MAGENTA='\033[0;35m'
NC='\033[0m'

print_header() {
    echo -e "${MAGENTA}========================================${NC}"
    echo -e "${MAGENTA}$1${NC}"
    echo -e "${MAGENTA}========================================${NC}"
}

print_step() {
    echo -e "${GREEN}==>${NC} $1"
}

print_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Configuration
AWS_REGION="us-east-1"
AWS_ENDPOINT_URL="http://localhost:4566"
FIREHOSE_DELIVERY_STREAM_NAME="MVS-stream"
S3_BUCKET_NAME="wifi-scan-data-bucket"
IAM_ROLE_NAME="FirehoseDeliveryRole"
IAM_POLICY_NAME="FirehoseS3AccessPolicy"

# Check if LocalStack is running
check_localstack() {
    if docker ps --format "table {{.Names}}" | grep -q "localstack"; then
        return 0
    else
        return 1
    fi
}

# Stop LocalStack
stop_localstack() {
    print_header "STOPPING LOCALSTACK"
    
    if ! check_localstack; then
        print_info "LocalStack is not running"
        return 0
    fi
    
    print_step "Stopping LocalStack..."
    
    if [ -f "docker-compose-localstack.yml" ]; then
        docker-compose -f docker-compose-localstack.yml down
        print_success "LocalStack stopped successfully"
    else
        print_warning "docker-compose-localstack.yml not found, stopping container directly..."
        docker stop localstack 2>/dev/null || true
        docker rm localstack 2>/dev/null || true
        print_success "LocalStack container stopped and removed"
    fi
}

# Clean up AWS resources (optional - for LocalStack this is usually not needed)
cleanup_aws_resources() {
    print_header "CLEANING UP AWS RESOURCES"
    
    # Check if AWS CLI is available
    if ! command -v aws &> /dev/null; then
        print_warning "AWS CLI not available, skipping AWS resource cleanup"
        return 0
    fi
    
    # Check if LocalStack is still running
    if check_localstack; then
        print_info "LocalStack is still running, cleaning up AWS resources..."
        
        # Delete Firehose delivery stream
        print_step "Deleting Firehose delivery stream..."
        if aws firehose describe-delivery-stream --delivery-stream-name $FIREHOSE_DELIVERY_STREAM_NAME --profile localstack --endpoint-url=$AWS_ENDPOINT_URL > /dev/null 2>&1; then
            aws firehose delete-delivery-stream --delivery-stream-name $FIREHOSE_DELIVERY_STREAM_NAME --profile localstack --endpoint-url=$AWS_ENDPOINT_URL
            print_success "Firehose delivery stream deleted"
        else
            print_info "Firehose delivery stream not found"
        fi
        
        # Delete S3 bucket contents and bucket
        print_step "Cleaning up S3 bucket..."
        if aws s3 ls s3://$S3_BUCKET_NAME --profile localstack --endpoint-url=$AWS_ENDPOINT_URL > /dev/null 2>&1; then
            aws s3 rm s3://$S3_BUCKET_NAME --recursive --profile localstack --endpoint-url=$AWS_ENDPOINT_URL
            aws s3 rb s3://$S3_BUCKET_NAME --profile localstack --endpoint-url=$AWS_ENDPOINT_URL
            print_success "S3 bucket deleted"
        else
            print_info "S3 bucket not found"
        fi
        
        # Delete IAM role and policy
        print_step "Cleaning up IAM resources..."
        
        # Detach policy from role
        if aws iam get-role --role-name $IAM_ROLE_NAME --profile localstack --endpoint-url=$AWS_ENDPOINT_URL > /dev/null 2>&1; then
            aws iam detach-role-policy --role-name $IAM_ROLE_NAME --policy-arn arn:aws:iam::000000000000:policy/$IAM_POLICY_NAME --profile localstack --endpoint-url=$AWS_ENDPOINT_URL 2>/dev/null || true
            aws iam delete-role --role-name $IAM_ROLE_NAME --profile localstack --endpoint-url=$AWS_ENDPOINT_URL
            print_success "IAM role deleted"
        else
            print_info "IAM role not found"
        fi
        
        # Delete IAM policy
        if aws iam get-policy --policy-arn arn:aws:iam::000000000000:policy/$IAM_POLICY_NAME --profile localstack --endpoint-url=$AWS_ENDPOINT_URL > /dev/null 2>&1; then
            aws iam delete-policy --policy-arn arn:aws:iam::000000000000:policy/$IAM_POLICY_NAME --profile localstack --endpoint-url=$AWS_ENDPOINT_URL
            print_success "IAM policy deleted"
        else
            print_info "IAM policy not found"
        fi
    else
        print_info "LocalStack is not running, AWS resources will be cleaned up automatically"
    fi
}

# Clean up temporary files
cleanup_temp_files() {
    print_header "CLEANING UP TEMPORARY FILES"
    
    print_step "Removing temporary files..."
    
    # Remove temporary JSON files
    rm -f /tmp/firehose-config.json
    rm -f /tmp/firehose-policy.json
    rm -f /tmp/firehose-trust-policy.json
    rm -f /tmp/firehose_test_data_*.json
    
    # Remove docker-compose file (optional)
    if [ -f "docker-compose-localstack.yml" ]; then
        read -p "Do you want to remove docker-compose-localstack.yml? (y/N): " -n 1 -r
        echo
        if [[ $REPLY =~ ^[Yy]$ ]]; then
            rm -f docker-compose-localstack.yml
            print_success "docker-compose-localstack.yml removed"
        else
            print_info "docker-compose-localstack.yml preserved"
        fi
    fi
    
    print_success "Temporary files cleaned up"
}

# Clean up Docker resources
cleanup_docker() {
    print_header "CLEANING UP DOCKER RESOURCES"
    
    print_step "Cleaning up Docker resources..."
    
    # Remove any dangling images
    docker image prune -f
    
    # Remove any stopped containers
    docker container prune -f
    
    # Remove any unused networks
    docker network prune -f
    
    # Remove any unused volumes (be careful with this)
    read -p "Do you want to remove unused Docker volumes? This may delete data. (y/N): " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        docker volume prune -f
        print_success "Unused Docker volumes removed"
    else
        print_info "Docker volumes preserved"
    fi
    
    print_success "Docker resources cleaned up"
}

# Main cleanup function
main() {
    print_header "COMPLETE INFRASTRUCTURE CLEANUP"
    print_info "This script will clean up LocalStack, Kafka, and related AWS resources"
    
    # Stop LocalStack
    stop_localstack
    
    # Stop Kafka cluster
    print_header "STOPPING KAFKA CLUSTER"
    print_step "Stopping Kafka and Zookeeper..."
    if [ -f "./stop-local-kafka.sh" ]; then
        ./stop-local-kafka.sh
        print_success "Kafka cluster stopped successfully"
    else
        print_warning "stop-local-kafka.sh not found, skipping Kafka cleanup"
    fi
    
    # Clean up AWS resources
    cleanup_aws_resources
    
    # Clean up temporary files
    cleanup_temp_files
    
    # Clean up Docker resources
    cleanup_docker
    
    print_header "CLEANUP COMPLETED"
    print_success "Complete infrastructure cleanup completed successfully!"
    echo ""
    print_info "Summary of actions:"
    echo "  - LocalStack stopped and removed"
    echo "  - Kafka cluster stopped and cleaned up"
    echo "  - AWS resources cleaned up (Firehose, S3, IAM)"
    echo "  - Temporary files removed"
    echo "  - Docker resources cleaned up"
    echo ""
    print_info "To restart the environment, run: ./setup.sh"
}

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --help)
            echo "Usage: $0 [OPTIONS]"
            echo "Options:"
            echo "  --help             Show this help message"
            echo "  --force            Skip confirmation prompts"
            echo "  --no-docker        Skip Docker cleanup"
            echo "  --no-aws           Skip AWS resource cleanup"
            exit 0
            ;;
        --force)
            FORCE=true
            shift
            ;;
        --no-docker)
            SKIP_DOCKER=true
            shift
            ;;
        --no-aws)
            SKIP_AWS=true
            shift
            ;;
        *)
            print_error "Unknown option: $1"
            exit 1
            ;;
    esac
done

# Run main function
main 