#!/bin/bash
# wifi-scan-ingestion/firehose-ingestion/scripts/cleanup.sh
# Clean up all infrastructure resources and LocalStack container

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

# Container name
CONTAINER_NAME="wifi-localstack"

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

# Function to confirm action
confirm_action() {
    local action=$1
    
    print_status "WARNING" "This will $action"
    read -p "Are you sure? (y/N): " -n 1 -r
    echo
    
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        print_status "INFO" "Operation cancelled"
        return 1
    fi
    
    return 0
}

# Function to destroy Terraform infrastructure
destroy_terraform() {
    print_status "INFO" "Destroying Terraform infrastructure..."
    
    if [ ! -d "$TERRAFORM_DIR" ]; then
        print_status "WARNING" "Terraform directory not found: $TERRAFORM_DIR"
        return 0
    fi
    
    cd "$TERRAFORM_DIR"
    
    if [ ! -f "terraform.tfstate" ]; then
        print_status "WARNING" "No Terraform state found, nothing to destroy"
        return 0
    fi
    
    # Set AWS credentials for LocalStack
    export AWS_ACCESS_KEY_ID=test
    export AWS_SECRET_ACCESS_KEY=test
    export AWS_DEFAULT_REGION=us-east-1
    
    # Check if LocalStack is running
    if ! curl -s http://localhost:4566/_localstack/health >/dev/null 2>&1; then
        print_status "WARNING" "LocalStack is not running, cannot destroy resources cleanly"
        print_status "INFO" "Will clean up Terraform state files instead"
        
        # Remove state files
        rm -f terraform.tfstate*
        rm -f tfplan
        rm -rf .terraform/
        
        print_status "SUCCESS" "Terraform state files cleaned up"
        return 0
    fi
    
    # Destroy infrastructure
    if terraform destroy -auto-approve; then
        print_status "SUCCESS" "Terraform infrastructure destroyed"
    else
        print_status "ERROR" "Failed to destroy some resources, cleaning up state files"
        rm -f terraform.tfstate*
        rm -f tfplan
    fi
    
    # Clean up plan file
    rm -f tfplan
}

# Function to stop and remove LocalStack container
stop_localstack() {
    print_status "INFO" "Stopping LocalStack container..."
    
    if docker ps --format "{{.Names}}" | grep -q "^${CONTAINER_NAME}$"; then
        print_status "INFO" "Stopping running LocalStack container"
        docker stop "$CONTAINER_NAME" >/dev/null 2>&1 || true
    else
        print_status "INFO" "LocalStack container is not running"
    fi
    
    if docker ps -a --format "{{.Names}}" | grep -q "^${CONTAINER_NAME}$"; then
        print_status "INFO" "Removing LocalStack container"
        docker rm "$CONTAINER_NAME" >/dev/null 2>&1 || true
    else
        print_status "INFO" "LocalStack container does not exist"
    fi
    
    print_status "SUCCESS" "LocalStack container cleaned up"
}

# Function to clean up Docker volumes and networks
cleanup_docker_resources() {
    print_status "INFO" "Cleaning up Docker volumes and networks..."
    
    # Remove LocalStack volumes
    if docker volume ls | grep -q localstack; then
        print_status "INFO" "Removing LocalStack volumes"
        docker volume ls | grep localstack | awk '{print $2}' | xargs -r docker volume rm 2>/dev/null || true
    fi
    
    # Clean up unused Docker resources
    print_status "INFO" "Cleaning up unused Docker resources"
    docker system prune -f >/dev/null 2>&1 || true
    
    print_status "SUCCESS" "Docker resources cleaned up"
}

# Function to clean up temporary files
cleanup_temp_files() {
    print_status "INFO" "Cleaning up temporary files..."
    
    # Remove LocalStack temp directory
    if [ -d "/tmp/localstack" ]; then
        rm -rf /tmp/localstack 2>/dev/null || true
        print_status "INFO" "Removed /tmp/localstack directory"
    fi
    
    # Remove any .terraform.lock.hcl backup files
    find "$PROJECT_ROOT" -name "*.terraform.lock.hcl.backup" -delete 2>/dev/null || true
    
    # Remove any plan files
    find "$PROJECT_ROOT" -name "tfplan*" -delete 2>/dev/null || true
    
    print_status "SUCCESS" "Temporary files cleaned up"
}

# Function to show cleanup status
show_cleanup_status() {
    print_status "INFO" "=== CLEANUP STATUS ==="
    
    # Check Terraform state
    if [ -f "$TERRAFORM_DIR/terraform.tfstate" ]; then
        echo "  Terraform State:  ❌ Still exists"
    else
        echo "  Terraform State:  ✅ Cleaned up"
    fi
    
    # Check LocalStack container
    if docker ps -a --format "{{.Names}}" | grep -q "^${CONTAINER_NAME}$"; then
        echo "  LocalStack:       ❌ Container still exists"
    else
        echo "  LocalStack:       ✅ Container removed"
    fi
    
    # Check LocalStack volumes
    if docker volume ls | grep -q localstack; then
        echo "  Docker Volumes:   ❌ LocalStack volumes still exist"
    else
        echo "  Docker Volumes:   ✅ Cleaned up"
    fi
    
    # Check temp directory
    if [ -d "/tmp/localstack" ]; then
        echo "  Temp Directory:   ❌ Still exists"
    else
        echo "  Temp Directory:   ✅ Cleaned up"
    fi
}

# Main function
main() {
    local mode=${1:-"all"}
    
    print_status "INFO" "WiFi Scan Ingestion Pipeline Cleanup"
    
    case $mode in
        "terraform"|"tf")
            if confirm_action "destroy all Terraform infrastructure"; then
                destroy_terraform
            fi
            ;;
        "localstack"|"ls")
            if confirm_action "stop and remove LocalStack container"; then
                stop_localstack
            fi
            ;;
        "docker")
            if confirm_action "clean up Docker resources"; then
                cleanup_docker_resources
            fi
            ;;
        "all"|"full")
            if confirm_action "destroy all infrastructure, stop LocalStack, and clean up all resources"; then
                destroy_terraform
                stop_localstack
                cleanup_docker_resources
                cleanup_temp_files
            fi
            ;;
        "force")
            print_status "WARNING" "Force cleanup - destroying everything without confirmation"
            destroy_terraform
            stop_localstack
            cleanup_docker_resources
            cleanup_temp_files
            ;;
        "help"|"-h"|"--help")
            echo "Usage: $0 [mode]"
            echo ""
            echo "Modes:"
            echo "  all|full     - Clean up everything (default)"
            echo "  terraform|tf - Destroy only Terraform infrastructure"
            echo "  localstack|ls - Stop and remove only LocalStack container"
            echo "  docker       - Clean up Docker volumes and networks"
            echo "  force        - Force cleanup without confirmation"
            echo "  help         - Show this help message"
            exit 0
            ;;
        *)
            print_status "ERROR" "Unknown mode: $mode"
            print_status "INFO" "Run '$0 help' for usage information"
            exit 1
            ;;
    esac
    
    # Show final status
    show_cleanup_status
    
    print_status "SUCCESS" "Cleanup completed!"
    print_status "INFO" "To restart the pipeline, run: ./scripts/setup.sh"
}

# Run main function
main "$@" 