#!/bin/bash

# setup-dev-environment.sh
# This script sets up the complete development environment for the Kafka SSL consumer service
# on a new Mac development machine.

set -e  # Exit on any error

# Get the directory where this script is located
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# Get the parent scripts directory
SCRIPTS_DIR="$(dirname "$SCRIPT_DIR")"

# Parse command line arguments
FORCE_REGENERATE_CERTS=false
while [[ $# -gt 0 ]]; do
    case $1 in
        --force-certs)
            FORCE_REGENERATE_CERTS=true
            shift
            ;;
        --help)
            echo "Usage: $0 [OPTIONS]"
            echo ""
            echo "🚀 Complete Development Environment Setup"
            echo ""
            echo "Options:"
            echo "  --force-certs    Force regenerate SSL certificates (requires service restart)"
            echo "  --help           Show this help message"
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            echo "Use --help for usage information"
            exit 1
            ;;
    esac
done

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Print with color
print_step() {
    echo -e "${GREEN}==>${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}Warning:${NC} $1"
}

print_error() {
    echo -e "${RED}Error:${NC} $1"
}

# Check if a command exists
check_command() {
    if ! command -v $1 &> /dev/null; then
        print_error "$1 is not installed. Please install it first."
        exit 1
    fi
}

# Check if Homebrew is installed
check_homebrew() {
    if ! command -v brew &> /dev/null; then
        print_error "Homebrew is not installed."
        echo ""
        echo -e "${YELLOW}Homebrew is required to install missing dependencies.${NC}"
        echo ""
        echo "To install Homebrew, run the following command:"
        echo ""
        echo -e "${GREEN}/bin/bash -c \"\$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)\"${NC}"
        echo ""
        echo "After installing Homebrew, run this setup script again."
        exit 1
    fi
}

# Install Docker Compose using Homebrew
install_docker_compose() {
    print_step "Docker Compose not found. Installing via Homebrew..."
    
    # Check if Homebrew is available
    check_homebrew
    
    # Install Docker Compose
    if brew install docker-compose; then
        print_step "Docker Compose installed successfully!"
    else
        print_error "Failed to install Docker Compose via Homebrew."
        exit 1
    fi
}

# Check Docker Compose installation
check_docker_compose() {
    if ! command -v docker-compose &> /dev/null; then
        print_warning "Docker Compose is not installed."
        
        # Check if Homebrew is available
        if command -v brew &> /dev/null; then
            read -p "Would you like to install Docker Compose using Homebrew? (y/n) " -n 1 -r
            echo
            if [[ $REPLY =~ ^[Yy]$ ]]; then
                install_docker_compose
            else
                print_error "Docker Compose is required to continue. Exiting."
                exit 1
            fi
        else
            check_homebrew  # This will show the Homebrew installation message
        fi
    else
        print_step "Docker Compose is already installed."
    fi
}

# Check prerequisites
check_prerequisites() {
    print_step "Checking prerequisites..."
    
    # Check Docker
    check_command docker
    if ! docker info &> /dev/null; then
        print_error "Docker is not running. Please start Docker Desktop."
        exit 1
    fi
    
    # Check Docker Compose (with auto-install capability)
    check_docker_compose
    
    # Check Java
    check_command java
    java_version=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}')
    if [[ ! $java_version == *"21"* ]]; then
        print_error "Java 21 is required. Found version: $java_version"
        print_warning "Please install Java 21 using: brew install openjdk@21"
        exit 1
    fi
    
    # Check Maven
    check_command mvn
    
    # Check keytool
    check_command keytool
    
    # Check openssl
    check_command openssl
    
    # Check AWS CLI (will be installed by AWS setup script if needed)
    if ! command -v aws &> /dev/null; then
        print_warning "AWS CLI not found - will be installed by AWS setup script"
    fi
    
    # Check jq (will be installed by AWS setup script if needed)
    if ! command -v jq &> /dev/null; then
        print_warning "jq not found - will be installed by AWS setup script"
    fi
    
    print_step "All prerequisites are satisfied!"
}

# Make scripts executable
make_scripts_executable() {
    print_step "Making scripts executable..."
    
    # Make setup scripts executable
    cd "$SCRIPT_DIR"
    for script in *.sh; do
        if [ -f "$script" ]; then
            chmod +x "$script"
        fi
    done
    
    # Make main scripts executable
    cd "$SCRIPTS_DIR"
    for script in *.sh; do
        if [ -f "$script" ]; then
            chmod +x "$script"
        fi
    done
    
    # Make test scripts executable
    if [ -d "test" ]; then
        for script in test/*.sh; do
            if [ -f "$script" ]; then
                chmod +x "$script"
            fi
        done
    fi
}

# Clean up any existing environment
cleanup_environment() {
    print_step "Cleaning up any existing environment..."
    cd "$SCRIPT_DIR"
    ./stop-local-kafka.sh 2>/dev/null || true
    docker system prune -f
}

# Main setup process
main() {
    print_step "Starting development environment setup..."
    print_step "Script directory: $SCRIPT_DIR"
    print_step "Scripts directory: $SCRIPTS_DIR"
    
    # Check prerequisites
    check_prerequisites
    
    # Make scripts executable
    make_scripts_executable
    
    # Clean up existing environment
    cleanup_environment
    
    # Run the complete setup
    print_step "Setting up local Kafka environment..."
    cd "$SCRIPT_DIR"
    if [ "$FORCE_REGENERATE_CERTS" = true ]; then
        ./setup-local-kafka.sh --force-certs
    else
        ./setup-local-kafka.sh
    fi
    
    # Start Kafka cluster (with better error handling)
    print_step "Starting Kafka cluster..."
    cd "$SCRIPT_DIR"
    if ! ./start-local-kafka.sh; then
        print_error "Failed to start Kafka cluster"
        print_step "Checking container status..."
        docker ps -a --filter "name=kafka" --filter "name=zookeeper"
        print_step "Checking for port conflicts..."
        lsof -i :2181 -i :9092 -i :9093 || true
        exit 1
    fi
    
    # Add a brief pause to ensure services are fully ready
    print_step "Allowing services to fully initialize..."
    sleep 5
    
    # Test the setup
    print_step "Testing SSL connection..."
    cd "$SCRIPT_DIR"
    if ! ./test-ssl-connection.sh; then
        print_warning "SSL connection test failed, but continuing with setup..."
    fi
    
    print_step "Creating test topic..."
    ./create-test-topic.sh
    
    print_step "Sending test message..."
    cd "$SCRIPTS_DIR"
    ./test/send-test-message.sh "Test message from setup script"
    
    print_step "Consuming test message..."
    cd "$SCRIPT_DIR"
    ./consume-test-messages.sh
    
    # Setup AWS infrastructure
    print_step "Setting up AWS infrastructure (LocalStack)..."
    cd "$SCRIPT_DIR"
    if ! ./setup-aws-infrastructure.sh; then
        print_warning "AWS infrastructure setup had issues, but continuing with setup..."
    fi
    
    print_step "Development environment setup completed successfully!"
    echo -e "\n${GREEN}Next steps:${NC}"
    echo "1. Start your Spring Boot application"
    echo "2. Monitor the application logs"
    echo "3. Use the test scripts to verify message flow"
    echo "4. Run Firehose integration tests: cd $SCRIPTS_DIR && ./test/validate-firehose-integration.sh"
    echo -e "\nTo stop the environment, run: ${YELLOW}cd $SCRIPT_DIR && ./stop-local-kafka.sh${NC}"
    echo -e "To stop LocalStack, run: ${YELLOW}cd $SCRIPT_DIR && docker-compose -f docker-compose-localstack.yml down${NC}"
}

# Run main function
main

