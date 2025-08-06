#!/bin/bash

# setup-dev-environment.sh
# This script sets up the complete development environment for the Kafka SSL consumer service
# on a new Mac development machine.

set -e  # Exit on any error

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

# Check prerequisites
check_prerequisites() {
    print_step "Checking prerequisites..."
    
    # Check Docker
    check_command docker
    if ! docker info &> /dev/null; then
        print_error "Docker is not running. Please start Docker Desktop."
        exit 1
    fi
    
    # Check Docker Compose
    check_command docker-compose
    
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
    chmod +x *.sh
    chmod +x test/*.sh
}

# Clean up any existing environment
cleanup_environment() {
    print_step "Cleaning up any existing environment..."
    ./stop-local-kafka.sh 2>/dev/null || true
    docker system prune -f
}

# Main setup process
main() {
    print_step "Starting development environment setup..."
    
    # Check prerequisites
    check_prerequisites
    
    # Make scripts executable
    make_scripts_executable
    
    # Clean up existing environment
    cleanup_environment
    
    # Run the complete setup
    print_step "Setting up local Kafka environment..."
    ./setup-local-kafka.sh
    
    # Start Kafka cluster (with better error handling)
    print_step "Starting Kafka cluster..."
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
    if ! ./test/test-ssl-connection.sh; then
        print_warning "SSL connection test failed, but continuing with setup..."
    fi
    
    print_step "Creating test topic..."
    ./test/create-test-topic.sh
    
    print_step "Sending test message..."
    ./test/send-test-message.sh "Test message from setup script"
    
    print_step "Consuming test message..."
    ./test/consume-test-messages.sh
    
    # Setup AWS infrastructure
    print_step "Setting up AWS infrastructure (LocalStack)..."
    if ! ./setup-aws-infrastructure.sh; then
        print_warning "AWS infrastructure setup had issues, but continuing with setup..."
    fi
    
    print_step "Development environment setup completed successfully!"
    echo -e "\n${GREEN}Next steps:${NC}"
    echo "1. Start your Spring Boot application"
    echo "2. Monitor the application logs"
    echo "3. Use the test scripts to verify message flow"
    echo "4. Run Firehose integration tests: ./test/validate-firehose-integration.sh"
    echo -e "\nTo stop the environment, run: ${YELLOW}./stop-local-kafka.sh${NC}"
    echo -e "To stop LocalStack, run: ${YELLOW}docker-compose -f docker-compose-localstack.yml down${NC}"
}

# Run main function
main 