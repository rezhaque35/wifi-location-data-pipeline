#!/bin/bash
# wifi-scan-ingestion/firehose-ingestion/scripts/setup.sh
# -----------------------------------------------------------------------------
# Master setup script for WiFi scan ingestion pipeline infrastructure
#
# PURPOSE:
#   - Automates the full setup of the WiFi scan ingestion pipeline for local development/testing.
#   - Installs dependencies, starts LocalStack, deploys infrastructure, and verifies everything.
#   - IDEMPOTENT: Safe to run multiple times - skips already completed steps.
#
# USAGE:
#   - Run this script from the 'scripts' directory:
#       cd wifi-scan-ingestion/firehose-ingestion/scripts
#       ./setup.sh
#   - Or run with full path from anywhere:
#       /full/path/to/wifi-scan-ingestion/firehose-ingestion/scripts/setup.sh
#
# WHAT IT DOES:
#   1. Checks and installs required dependencies (Docker, Terraform, AWS CLI, jq, etc.)
#   2. Checks and starts LocalStack (local AWS cloud emulator) if not running
#   3. Checks and deploys infrastructure using Terraform if not already deployed
#   4. Verifies that all components are healthy and ready
#
# IDEMPOTENT DESIGN:
#   - Each step checks if work is already done before proceeding
#   - Shows clear status of what's completed vs what needs work
#   - Safe to interrupt and resume
#
# REQUIREMENTS:
#   - Bash shell (macOS/Linux)
#   - Sudo/admin access for dependency installation (first run only)
#   - Docker must be running (will be installed if missing)
# -----------------------------------------------------------------------------

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Script directory (should be run from scripts/ or with full path)
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# Function to print colored output
print_status() {
    local status=$1
    local message=$2
    case $status in
        "INFO")     echo -e "${BLUE}[INFO]${NC} $message" ;;
        "SUCCESS")  echo -e "${GREEN}[SUCCESS]${NC} $message" ;;
        "WARNING")  echo -e "${YELLOW}[WARNING]${NC} $message" ;;
        "ERROR")    echo -e "${RED}[ERROR]${NC} $message" ;;
        "SKIP")     echo -e "${CYAN}[SKIP]${NC} $message" ;;
        "CHECK")    echo -e "${YELLOW}[CHECK]${NC} $message" ;;
    esac
}

# Function to check if command exists
command_exists() {
    command -v "$1" >/dev/null 2>&1
}

# Function to check if LocalStack is running and healthy
localstack_running() {
    if curl -s http://localhost:4566/_localstack/health >/dev/null 2>&1; then
        return 0
    else
        return 1
    fi
}

# Function to check if infrastructure is deployed and up to date
infrastructure_deployed() {
    if [ -f "$PROJECT_ROOT/terraform/terraform.tfstate" ]; then
        cd "$PROJECT_ROOT/terraform"
        if terraform show >/dev/null 2>&1; then
            # Check if resources exist by counting them
            local resource_count=$(terraform state list 2>/dev/null | wc -l)
            if [ "$resource_count" -gt 0 ]; then
                # Also verify that a key resource actually exists in LocalStack
                # Check if S3 bucket exists as a sanity check
                if aws --endpoint-url=http://localhost:4566 s3 ls s3://ingested-wifiscan-data >/dev/null 2>&1; then
                    return 0
                else
                    print_status "WARNING" "Terraform state exists but resources not found in LocalStack - will redeploy"
                    return 1
                fi
            fi
        fi
    fi
    return 1
}

# Function to check dependencies
check_dependencies() {
    local all_installed=0  # 0 = true (success), 1 = false (failure) in bash
    local tools=("docker" "terraform" "aws" "jq" "curl")
    
    print_status "CHECK" "Checking required dependencies..."
    
    for tool in "${tools[@]}"; do
        if command_exists "$tool"; then
            print_status "SUCCESS" "$tool is installed"
        else
            print_status "WARNING" "$tool is NOT installed"
            all_installed=1
        fi
    done
    
    return $all_installed
}

# Function to check Docker service status
check_docker_running() {
    if command_exists docker && docker info >/dev/null 2>&1; then
        print_status "SUCCESS" "Docker is running"
        return 0
    else
        print_status "WARNING" "Docker is not running or not accessible"
        return 1
    fi
}

# Function to check LocalStack container status
check_localstack_status() {
    if docker ps --format "{{.Names}}" | grep -q "^wifi-localstack$"; then
        if localstack_running; then
            print_status "SUCCESS" "LocalStack is running and healthy"
            return 0
        else
            print_status "WARNING" "LocalStack container exists but is not healthy"
            return 1
        fi
    else
        print_status "WARNING" "LocalStack container is not running"
        return 1
    fi
}

# -----------------------------------------------------------------------------
# MAIN SETUP STEPS WITH IDEMPOTENT CHECKS
# -----------------------------------------------------------------------------

print_status "INFO" "Starting WiFi Scan Ingestion Pipeline Setup"
print_status "INFO" "Project Root: $PROJECT_ROOT"
print_status "INFO" "Performing idempotent setup - already completed steps will be skipped"

echo ""
print_status "INFO" "=== CHECKING CURRENT STATE ==="

# Pre-flight checks to determine what needs to be done
step1_needed=true
step2_needed=true
step3_needed=true

# Check Step 1: Dependencies
if check_dependencies && check_docker_running; then
    print_status "SKIP" "Step 1: All dependencies are already installed and Docker is running"
    step1_needed=false
else
    print_status "CHECK" "Step 1: Dependencies need installation/setup"
fi

# Check Step 2: LocalStack
if check_localstack_status; then
    print_status "SKIP" "Step 2: LocalStack is already running and healthy"
    step2_needed=false
else
    print_status "CHECK" "Step 2: LocalStack needs to be started"
fi

# Check Step 3: Infrastructure
if infrastructure_deployed; then
    print_status "SKIP" "Step 3: Infrastructure is already deployed"
    step3_needed=false
else
    print_status "CHECK" "Step 3: Infrastructure needs to be deployed"
fi

echo ""
print_status "INFO" "=== EXECUTING REQUIRED STEPS ==="

# Step 1: Install dependencies (only if needed)
if [ "$step1_needed" = true ]; then
    print_status "INFO" "Step 1: Installing dependencies..."
    if ! "$SCRIPT_DIR/install-dependencies.sh"; then
        print_status "ERROR" "Failed to install dependencies"
        exit 1
    fi
    print_status "SUCCESS" "Step 1 completed: Dependencies installed"
else
    print_status "SKIP" "Step 1 skipped: Dependencies already satisfied"
fi

# Step 2: Start LocalStack (only if needed)
if [ "$step2_needed" = true ]; then
    print_status "INFO" "Step 2: Starting LocalStack..."
    if ! "$SCRIPT_DIR/start-localstack.sh"; then
        print_status "ERROR" "Failed to start LocalStack"
        exit 1
    fi
    print_status "SUCCESS" "Step 2 completed: LocalStack started"
else
    print_status "SKIP" "Step 2 skipped: LocalStack already running and healthy"
fi

# Step 3: Deploy infrastructure (only if needed)
if [ "$step3_needed" = true ]; then
    print_status "INFO" "Step 3: Deploying infrastructure..."
    if ! "$SCRIPT_DIR/deploy-infrastructure.sh"; then
        print_status "ERROR" "Failed to deploy infrastructure"
        exit 1
    fi
    print_status "SUCCESS" "Step 3 completed: Infrastructure deployed"
else
    print_status "SKIP" "Step 3 skipped: Infrastructure already deployed"
fi

# Step 4: Always verify deployment (quick operation, good to confirm)
print_status "INFO" "Step 4: Verifying deployment (always performed)..."
if ! "$SCRIPT_DIR/verify-deployment.sh"; then
    print_status "ERROR" "Deployment verification failed"
    print_status "INFO" "This could indicate infrastructure drift or service issues"
    exit 1
fi

echo ""
print_status "SUCCESS" "WiFi Scan Ingestion Pipeline setup completed successfully!"

# Summary of what was done vs skipped
echo ""
print_status "INFO" "=== SETUP SUMMARY ==="
[ "$step1_needed" = true ] && print_status "INFO" "✓ Dependencies were installed" || print_status "INFO" "→ Dependencies were already satisfied"
[ "$step2_needed" = true ] && print_status "INFO" "✓ LocalStack was started" || print_status "INFO" "→ LocalStack was already running"
[ "$step3_needed" = true ] && print_status "INFO" "✓ Infrastructure was deployed" || print_status "INFO" "→ Infrastructure was already deployed"
print_status "INFO" "✓ Deployment was verified"

echo ""
print_status "INFO" "=== NEXT STEPS ==="
print_status "INFO" "  - Run './test_message_compression.sh' to test message compression"
print_status "INFO" "  - Run './ingestion_integration_test.sh' to test the full data pipeline"
print_status "INFO" "  - Use './cleanup.sh' to tear down the infrastructure"
print_status "INFO" "  - Check LocalStack logs: docker logs wifi-localstack"

echo ""
print_status "INFO" "Setup script completed in idempotent mode - safe to run again anytime!" 