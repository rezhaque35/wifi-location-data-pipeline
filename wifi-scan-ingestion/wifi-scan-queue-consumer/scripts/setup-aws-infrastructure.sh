#!/bin/bash

# setup-aws-infrastructure.sh
# This script sets up AWS infrastructure (LocalStack) with Firehose and S3 for development
# It's idempotent and handles dependency installation

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

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

# Configuration from application.yml
AWS_REGION="us-east-1"
AWS_ENDPOINT_URL="http://localhost:4566"
AWS_ACCESS_KEY_ID="test"
AWS_SECRET_ACCESS_KEY="test"
FIREHOSE_DELIVERY_STREAM_NAME="MVS-stream"
S3_BUCKET_NAME="wifi-scan-data-bucket"
IAM_ROLE_NAME="FirehoseDeliveryRole"
IAM_POLICY_NAME="FirehoseS3AccessPolicy"

# Check if a command exists and install if needed
check_and_install_command() {
    local cmd=$1
    local install_cmd=$2
    local package_name=${3:-$cmd}
    
    if ! command -v $cmd &> /dev/null; then
        print_warning "$cmd is not installed. Installing $package_name..."
        if eval "$install_cmd"; then
            print_success "$cmd installed successfully"
        else
            print_error "Failed to install $cmd"
            exit 1
        fi
    else
        print_info "$cmd is already installed"
    fi
}

# Install dependencies based on OS
install_dependencies() {
    print_header "INSTALLING DEPENDENCIES"
    
    # Detect OS
    if [[ "$OSTYPE" == "darwin"* ]]; then
        # macOS
        print_step "Detected macOS, installing dependencies..."
        
        # Check if Homebrew is installed
        if ! command -v brew &> /dev/null; then
            print_warning "Homebrew is not installed. Installing Homebrew..."
            /bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
        fi
        
        # Install AWS CLI
        check_and_install_command "aws" "brew install awscli" "AWS CLI"
        
        # Install jq for JSON processing
        check_and_install_command "jq" "brew install jq" "jq"
        
        # Install Docker if not present
        if ! command -v docker &> /dev/null; then
            print_warning "Docker is not installed. Please install Docker Desktop from https://www.docker.com/products/docker-desktop"
            print_info "After installing Docker Desktop, run this script again."
            exit 1
        fi
        
        # Install Docker Compose
        check_and_install_command "docker-compose" "brew install docker-compose" "Docker Compose"
        
    elif [[ "$OSTYPE" == "linux-gnu"* ]]; then
        # Linux
        print_step "Detected Linux, installing dependencies..."
        
        # Update package list
        sudo apt-get update
        
        # Install AWS CLI
        check_and_install_command "aws" "curl 'https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip' -o 'awscliv2.zip' && unzip awscliv2.zip && sudo ./aws/install && rm -rf aws awscliv2.zip" "AWS CLI"
        
        # Install jq
        check_and_install_command "jq" "sudo apt-get install -y jq" "jq"
        
        # Install Docker
        if ! command -v docker &> /dev/null; then
            print_warning "Installing Docker..."
            curl -fsSL https://get.docker.com -o get-docker.sh
            sudo sh get-docker.sh
            sudo usermod -aG docker $USER
            rm get-docker.sh
            print_warning "Please log out and log back in for Docker group changes to take effect"
            exit 1
        fi
        
        # Install Docker Compose
        check_and_install_command "docker-compose" "sudo curl -L 'https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)' -o /usr/local/bin/docker-compose && sudo chmod +x /usr/local/bin/docker-compose" "Docker Compose"
        
    else
        print_error "Unsupported OS: $OSTYPE"
        exit 1
    fi
    
    print_success "All dependencies installed successfully!"
}

# Check if LocalStack is running
check_localstack() {
    if docker ps --format "table {{.Names}}" | grep -q "localstack"; then
        return 0
    else
        return 1
    fi
}

# Start LocalStack
start_localstack() {
    print_header "STARTING LOCALSTACK"
    
    if check_localstack; then
        print_info "LocalStack is already running"
        return 0
    fi
    
    # Clean up any existing failed containers
    print_step "Cleaning up any existing LocalStack containers..."
    docker-compose -f docker-compose-localstack.yml down 2>/dev/null || true
    docker rm -f localstack 2>/dev/null || true
    
    print_step "Starting LocalStack..."
    
    # Create docker-compose file for LocalStack if it doesn't exist
    if [ ! -f "docker-compose-localstack.yml" ]; then
        cat > docker-compose-localstack.yml << EOF
services:
  localstack:
    image: localstack/localstack:latest
    container_name: localstack
    ports:
      - "4566:4566"
    environment:
      - SERVICES=s3,firehose,iam,sts
      - DEBUG=0
      - AWS_DEFAULT_REGION=${AWS_REGION}
      - AWS_ACCESS_KEY_ID=${AWS_ACCESS_KEY_ID}
      - AWS_SECRET_ACCESS_KEY=${AWS_SECRET_ACCESS_KEY}
      - PERSISTENCE=0
      - SKIP_SSL_CERT_DOWNLOAD=1
      - SKIP_INTERNAL_CALLS=1
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:4566/_localstack/health"]
      interval: 10s
      timeout: 5s
      retries: 5
    restart: unless-stopped
EOF
    fi
    
    # Start LocalStack
    if ! docker-compose -f docker-compose-localstack.yml up -d; then
        print_error "Failed to start LocalStack with docker-compose"
        print_step "Checking container status..."
        docker ps -a --filter "name=localstack"
        exit 1
    fi
    
    # Wait for LocalStack to be ready
    print_step "Waiting for LocalStack to be ready..."
    local max_attempts=45  # Increased timeout
    local attempt=1
    
    while [ $attempt -le $max_attempts ]; do
        # Check if container is running
        if ! docker ps --format "table {{.Names}}" | grep -q "localstack"; then
            print_error "LocalStack container stopped unexpectedly"
            print_step "Checking logs..."
            docker logs localstack 2>/dev/null || true
            exit 1
        fi
        
        # Check health endpoint
        if curl -s http://localhost:4566/_localstack/health > /dev/null 2>&1; then
            print_success "LocalStack is ready!"
            break
        fi
        
        printf "\r${BLUE}[INFO]${NC} Waiting for LocalStack... (attempt %d/%d)" "$attempt" "$max_attempts"
        sleep 2
        ((attempt++))
    done
    
    echo ""
    
    if [ $attempt -gt $max_attempts ]; then
        print_error "LocalStack failed to start within 90 seconds"
        print_step "Checking container logs..."
        docker logs localstack 2>/dev/null || true
        exit 1
    fi
}

# Configure AWS CLI for LocalStack
configure_aws_cli() {
    print_header "CONFIGURING AWS CLI"
    
    # Check if profile already exists
    if aws configure list-profiles | grep -q "localstack"; then
        print_info "AWS CLI profile 'localstack' already exists"
    else
        print_step "Configuring AWS CLI for LocalStack..."
        aws configure set aws_access_key_id $AWS_ACCESS_KEY_ID --profile localstack
        aws configure set aws_secret_access_key $AWS_SECRET_ACCESS_KEY --profile localstack
        aws configure set region $AWS_REGION --profile localstack
        aws configure set output json --profile localstack
        print_success "AWS CLI configured for LocalStack"
    fi
}

# Create S3 bucket
create_s3_bucket() {
    print_header "CREATING S3 BUCKET"
    
    print_step "Creating S3 bucket: $S3_BUCKET_NAME"
    
    if aws s3 ls s3://$S3_BUCKET_NAME --profile localstack --endpoint-url=$AWS_ENDPOINT_URL > /dev/null 2>&1; then
        print_info "S3 bucket '$S3_BUCKET_NAME' already exists"
    else
        aws s3 mb s3://$S3_BUCKET_NAME \
            --profile localstack \
            --endpoint-url=$AWS_ENDPOINT_URL \
            --region $AWS_REGION
        
        if [ $? -eq 0 ]; then
            print_success "S3 bucket '$S3_BUCKET_NAME' created successfully"
        else
            print_error "Failed to create S3 bucket"
            exit 1
        fi
    fi
}

# Create IAM role and policy for Firehose
create_iam_resources() {
    print_header "CREATING IAM RESOURCES"
    
    # Create IAM policy document
    local policy_document=$(cat << EOF
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Effect": "Allow",
            "Action": [
                "s3:AbortMultipartUpload",
                "s3:GetBucketLocation",
                "s3:GetObject",
                "s3:ListBucket",
                "s3:ListBucketMultipartUploads",
                "s3:PutObject"
            ],
            "Resource": [
                "arn:aws:s3:::${S3_BUCKET_NAME}",
                "arn:aws:s3:::${S3_BUCKET_NAME}/*"
            ]
        }
    ]
}
EOF
)
    
    # Create IAM policy
    print_step "Creating IAM policy: $IAM_POLICY_NAME"
    if aws iam get-policy --policy-arn arn:aws:iam::000000000000:policy/$IAM_POLICY_NAME --profile localstack --endpoint-url=$AWS_ENDPOINT_URL > /dev/null 2>&1; then
        print_info "IAM policy '$IAM_POLICY_NAME' already exists"
    else
        echo "$policy_document" > /tmp/firehose-policy.json
        aws iam create-policy \
            --policy-name $IAM_POLICY_NAME \
            --policy-document file:///tmp/firehose-policy.json \
            --profile localstack \
            --endpoint-url=$AWS_ENDPOINT_URL
        
        if [ $? -eq 0 ]; then
            print_success "IAM policy '$IAM_POLICY_NAME' created successfully"
        else
            print_error "Failed to create IAM policy"
            exit 1
        fi
        rm -f /tmp/firehose-policy.json
    fi
    
    # Create IAM role trust policy
    local trust_policy=$(cat << EOF
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Effect": "Allow",
            "Principal": {
                "Service": "firehose.amazonaws.com"
            },
            "Action": "sts:AssumeRole"
        }
    ]
}
EOF
)
    
    # Create IAM role
    print_step "Creating IAM role: $IAM_ROLE_NAME"
    if aws iam get-role --role-name $IAM_ROLE_NAME --profile localstack --endpoint-url=$AWS_ENDPOINT_URL > /dev/null 2>&1; then
        print_info "IAM role '$IAM_ROLE_NAME' already exists"
    else
        echo "$trust_policy" > /tmp/firehose-trust-policy.json
        aws iam create-role \
            --role-name $IAM_ROLE_NAME \
            --assume-role-policy-document file:///tmp/firehose-trust-policy.json \
            --profile localstack \
            --endpoint-url=$AWS_ENDPOINT_URL
        
        if [ $? -eq 0 ]; then
            print_success "IAM role '$IAM_ROLE_NAME' created successfully"
        else
            print_error "Failed to create IAM role"
            exit 1
        fi
        rm -f /tmp/firehose-trust-policy.json
    fi
    
    # Attach policy to role
    print_step "Attaching policy to role..."
    aws iam attach-role-policy \
        --role-name $IAM_ROLE_NAME \
        --policy-arn arn:aws:iam::000000000000:policy/$IAM_POLICY_NAME \
        --profile localstack \
        --endpoint-url=$AWS_ENDPOINT_URL
    
    if [ $? -eq 0 ]; then
        print_success "Policy attached to role successfully"
    else
        print_warning "Policy attachment failed (might already be attached)"
    fi
}

# Create Firehose delivery stream
create_firehose_stream() {
    print_header "CREATING FIREHOSE DELIVERY STREAM"
    
    print_step "Creating Firehose delivery stream: $FIREHOSE_DELIVERY_STREAM_NAME"
    
    # Check if stream already exists
    if aws firehose describe-delivery-stream --delivery-stream-name $FIREHOSE_DELIVERY_STREAM_NAME --profile localstack --endpoint-url=$AWS_ENDPOINT_URL > /dev/null 2>&1; then
        print_info "Firehose delivery stream '$FIREHOSE_DELIVERY_STREAM_NAME' already exists"
        return 0
    fi
    
    # Create Firehose configuration
    local firehose_config=$(cat << EOF
{
    "DeliveryStreamName": "${FIREHOSE_DELIVERY_STREAM_NAME}",
    "DeliveryStreamType": "DirectPut",
    "S3DestinationConfiguration": {
        "RoleARN": "arn:aws:iam::000000000000:role/${IAM_ROLE_NAME}",
        "BucketARN": "arn:aws:s3:::${S3_BUCKET_NAME}",
        "Prefix": "wifi-scan-data/year=!{timestamp:yyyy}/month=!{timestamp:MM}/day=!{timestamp:dd}/hour=!{timestamp:HH}/",
        "ErrorOutputPrefix": "errors/!{firehose:error-output-type}/year=!{timestamp:yyyy}/month=!{timestamp:MM}/day=!{timestamp:dd}/hour=!{timestamp:HH}/",
        "BufferingHints": {
            "SizeInMBs": 128,
            "IntervalInSeconds": 60
        },
        "CompressionFormat": "UNCOMPRESSED",
        "EncryptionConfiguration": {
            "NoEncryptionConfig": "NoEncryption"
        }
    }
}
EOF
)
    
    # Create the delivery stream
    echo "$firehose_config" > /tmp/firehose-config.json
    aws firehose create-delivery-stream \
        --cli-input-json file:///tmp/firehose-config.json \
        --profile localstack \
        --endpoint-url=$AWS_ENDPOINT_URL
    
    if [ $? -eq 0 ]; then
        print_success "Firehose delivery stream '$FIREHOSE_DELIVERY_STREAM_NAME' created successfully"
    else
        print_error "Failed to create Firehose delivery stream"
        exit 1
    fi
    
    rm -f /tmp/firehose-config.json
    
    # Wait for stream to be active
    print_step "Waiting for delivery stream to become active..."
    local max_attempts=30
    local attempt=1
    
    while [ $attempt -le $max_attempts ]; do
        local status=$(aws firehose describe-delivery-stream \
            --delivery-stream-name $FIREHOSE_DELIVERY_STREAM_NAME \
            --profile localstack \
            --endpoint-url=$AWS_ENDPOINT_URL \
            --query 'DeliveryStreamDescription.DeliveryStreamStatus' \
            --output text 2>/dev/null || echo "CREATING")
        
        if [ "$status" = "ACTIVE" ]; then
            print_success "Delivery stream is now ACTIVE!"
            break
        fi
        
        printf "\r${BLUE}[INFO]${NC} Waiting for stream to become active... (attempt %d/%d) - Status: %s" "$attempt" "$max_attempts" "$status"
        sleep 2
        ((attempt++))
    done
    
    echo ""
    
    if [ $attempt -gt $max_attempts ]; then
        print_warning "Stream did not become active within 60 seconds, but continuing..."
    fi
}

# Test Firehose functionality
test_firehose() {
    print_header "TESTING FIREHOSE FUNCTIONALITY"
    
    print_step "Testing Firehose delivery stream..."
    
    # Test data
    local test_data='{"timestamp":"2024-01-01T12:00:00Z","device_id":"test-device-001","wifi_scan_data":{"ssid":"TestWiFi","signal_strength":-45}}'
    
    # Put record to Firehose
    print_info "Putting test record to Firehose..."
    
    # Create record JSON file with base64-encoded data
    jq -n --arg data "$(echo "$test_data" | base64)" '{"Data": $data}' > /tmp/firehose-record.json
    
    aws firehose put-record \
        --delivery-stream-name $FIREHOSE_DELIVERY_STREAM_NAME \
        --record file:///tmp/firehose-record.json \
        --profile localstack \
        --endpoint-url=$AWS_ENDPOINT_URL \
        --region $AWS_REGION
    
    # Clean up
    rm -f /tmp/firehose-record.json
    
    if [ $? -eq 0 ]; then
        print_success "Test record sent to Firehose successfully"
    else
        print_error "Failed to send test record to Firehose"
        exit 1
    fi
    
    # Wait a moment for processing
    sleep 5
    
    # Check if data appears in S3 (this might take longer in LocalStack)
    print_info "Checking S3 for delivered data..."
    local s3_objects=$(aws s3 ls s3://$S3_BUCKET_NAME --recursive --profile localstack --endpoint-url=$AWS_ENDPOINT_URL 2>/dev/null | wc -l)
    
    if [ $s3_objects -gt 0 ]; then
        print_success "Data successfully delivered to S3! Found $s3_objects object(s)"
        
        # Clean up test data from S3
        print_info "Cleaning up test data from S3..."
        aws s3 rm s3://$S3_BUCKET_NAME --recursive --profile localstack --endpoint-url=$AWS_ENDPOINT_URL 2>/dev/null
        
        if [ $? -eq 0 ]; then
            print_success "Test data cleaned up from S3 successfully"
        else
            print_warning "Failed to clean up test data from S3 (this is not critical)"
        fi
    else
        print_warning "No data found in S3 yet (this is normal for LocalStack, data may take longer to appear)"
    fi
}

# Main function
main() {
    print_header "AWS INFRASTRUCTURE SETUP"
    print_info "Setting up LocalStack with Firehose and S3 integration"
    print_info "This script is idempotent - safe to run multiple times"
    
    # Install dependencies
    install_dependencies
    
    # Start LocalStack
    start_localstack
    
    # Configure AWS CLI
    configure_aws_cli
    
    # Create infrastructure
    create_s3_bucket
    create_iam_resources
    create_firehose_stream
    
    # Test the setup
    test_firehose
    
    print_header "SETUP COMPLETED SUCCESSFULLY"
    print_success "AWS infrastructure is ready for development!"
    echo ""
    print_info "Configuration Summary:"
    echo "  - AWS Region: $AWS_REGION"
    echo "  - LocalStack Endpoint: $AWS_ENDPOINT_URL"
    echo "  - Firehose Stream: $FIREHOSE_DELIVERY_STREAM_NAME"
    echo "  - S3 Bucket: $S3_BUCKET_NAME"
    echo "  - IAM Role: $IAM_ROLE_NAME"
    echo ""
    print_info "Your Spring Boot application can now use these AWS services for development."
    print_info "To stop LocalStack, run: docker-compose -f docker-compose-localstack.yml down"
}

# Run main function
main 