#!/bin/bash
# wifi-scan-ingestion/firehose-ingestion/scripts/deploy-infrastructure.sh
# Deploy Terraform infrastructure to LocalStack

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Script directory and project paths
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
TERRAFORM_DIR="$PROJECT_ROOT/terraform"

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

# Function to check if LocalStack is running
check_localstack() {
    if ! curl -s http://localhost:4566/_localstack/health >/dev/null 2>&1; then
        print_status "ERROR" "LocalStack is not running. Please start LocalStack first"
        print_status "INFO" "Run: ./scripts/start-localstack.sh"
        return 1
    fi
    print_status "SUCCESS" "LocalStack is running"
}

# Function to check if Terraform is initialized
terraform_initialized() {
    [ -d "$TERRAFORM_DIR/.terraform" ] && [ -f "$TERRAFORM_DIR/.terraform.lock.hcl" ]
}

# Function to check if infrastructure is already deployed
infrastructure_deployed() {
    [ -f "$TERRAFORM_DIR/terraform.tfstate" ] && \
    cd "$TERRAFORM_DIR" && \
    terraform show >/dev/null 2>&1
}

# Function to initialize Terraform
initialize_terraform() {
    print_status "INFO" "Initializing Terraform..."
    cd "$TERRAFORM_DIR"
    
    if terraform_initialized; then
        print_status "INFO" "Terraform already initialized, running init to ensure modules are up to date"
        terraform init -upgrade
    else
        print_status "INFO" "Running initial Terraform initialization"
        terraform init
    fi
    
    print_status "SUCCESS" "Terraform initialized"
}

# Function to validate Terraform configuration
validate_terraform() {
    print_status "INFO" "Validating Terraform configuration..."
    cd "$TERRAFORM_DIR"
    
    terraform validate
    print_status "SUCCESS" "Terraform configuration is valid"
}

# Function to plan Terraform deployment
plan_terraform() {
    print_status "INFO" "Creating Terraform deployment plan..."
    cd "$TERRAFORM_DIR"
    
    terraform plan -out=tfplan
    print_status "SUCCESS" "Terraform plan created"
}

# Function to apply Terraform deployment
apply_terraform() {
    print_status "INFO" "Applying Terraform deployment..."
    cd "$TERRAFORM_DIR"
    
    terraform apply -auto-approve tfplan
    
    # Clean up plan file
    rm -f tfplan
    
    print_status "SUCCESS" "Terraform deployment completed"
}

# Function to get deployment outputs
show_outputs() {
    print_status "INFO" "Deployment outputs:"
    cd "$TERRAFORM_DIR"
    
    terraform output -json | jq '.' 2>/dev/null || terraform output
}

# Function to check current infrastructure state
check_infrastructure_state() {
    print_status "INFO" "Checking current infrastructure state..."
    cd "$TERRAFORM_DIR"
    
    if ! infrastructure_deployed; then
        print_status "INFO" "No existing infrastructure found"
        return 1
    fi
    
    # Check if current state matches configuration
    terraform plan -detailed-exitcode >/dev/null 2>&1
    local exit_code=$?
    
    case $exit_code in
        0)
            print_status "SUCCESS" "Infrastructure is up to date"
            return 0
            ;;
        1)
            print_status "ERROR" "Terraform plan failed"
            return 1
            ;;
        2)
            print_status "WARNING" "Infrastructure exists but has changes to apply"
            return 2
            ;;
    esac
}

# Function to handle existing infrastructure
handle_existing_infrastructure() {
    local state_check_result=$1
    
    case $state_check_result in
        0)
            print_status "SUCCESS" "Infrastructure is already deployed and up to date - skipping deployment"
            print_status "INFO" "Showing current infrastructure outputs..."
            show_outputs
            return 0
            ;;
        2)
            print_status "WARNING" "Infrastructure exists but has pending changes"
            print_status "INFO" "Updating infrastructure..."
            plan_terraform
            apply_terraform
            show_outputs
            return 0
            ;;
    esac
}

# Function to deploy fresh infrastructure
deploy_fresh_infrastructure() {
    print_status "INFO" "Deploying fresh infrastructure..."
    
    validate_terraform
    plan_terraform
    apply_terraform
    show_outputs
}

# Function to set AWS credentials for LocalStack
set_aws_credentials() {
    export AWS_ACCESS_KEY_ID=test
    export AWS_SECRET_ACCESS_KEY=test
    export AWS_DEFAULT_REGION=us-east-1
    print_status "INFO" "AWS credentials set for LocalStack"
}

# Main function
main() {
    print_status "INFO" "Starting infrastructure deployment..."
    print_status "INFO" "Terraform directory: $TERRAFORM_DIR"
    
    # Check prerequisites
    if ! command -v terraform >/dev/null 2>&1; then
        print_status "ERROR" "Terraform is not installed"
        exit 1
    fi
    
    if [ ! -d "$TERRAFORM_DIR" ]; then
        print_status "ERROR" "Terraform directory not found: $TERRAFORM_DIR"
        exit 1
    fi
    
    # Check LocalStack
    if ! check_localstack; then
        exit 1
    fi
    
    # Set AWS credentials for LocalStack
    set_aws_credentials
    
    # Initialize Terraform
    initialize_terraform
    
    # Check current state and attempt to deploy
    if ! deploy_or_update_infrastructure; then
        print_status "ERROR" "Failed to deploy or update infrastructure"
        exit 1
    fi
    
    print_status "SUCCESS" "Infrastructure deployment completed successfully!"
    
    # Display helpful information
    print_status "INFO" "Next steps:"
    print_status "INFO" "  - Run './verify-deployment.sh' to verify all resources"
    print_status "INFO" "  - Run './ingestion_integration_test.sh' to test the data flow"
    print_status "INFO" "  - Check Terraform state: cd terraform && terraform show"
}

# New function to consolidate deployment logic
deploy_or_update_infrastructure() {
    print_status "INFO" "Checking infrastructure status and deploying if necessary..."
    cd "$TERRAFORM_DIR"

    # Validate configuration first
    if ! terraform validate; then
        print_status "ERROR" "Terraform configuration is invalid"
        return 1
    fi
    
    # Plan the changes
    if ! terraform plan -out=tfplan; then
        print_status "ERROR" "Terraform plan failed. This can happen if state is out of sync."
        print_status "INFO" "Attempting to apply directly..."
    fi

    # Apply the plan (or apply directly if plan failed)
    if ! terraform apply -auto-approve; then
        print_status "ERROR" "Terraform apply failed"
        return 1
    fi

    # Clean up plan file if it exists
    rm -f tfplan
    
    print_status "SUCCESS" "Infrastructure deployed/updated successfully"
    show_outputs
    return 0
}


# Run main function
main "$@" 