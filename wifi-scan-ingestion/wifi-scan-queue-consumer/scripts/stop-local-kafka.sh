#!/bin/bash

# wifi-database/wifi-scan-collection/wifi-scan-queue-consumer/scripts/stop-local-kafka.sh
# Script to stop and cleanup local Kafka cluster

set -e  # Exit on any error

echo "🛑 Stopping Local Kafka SSL Cluster..."

# Define colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Function to print colored output
print_status() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Function to check if services are running
check_running_services() {
    print_status "Checking for running Kafka services..."
    
    if docker ps | grep -q "kafka\|zookeeper"; then
        echo "Found running containers:"
        docker ps --filter "name=kafka" --filter "name=zookeeper" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
        echo ""
        return 0
    else
        print_warning "No Kafka or Zookeeper containers are currently running."
        return 1
    fi
}

# Function to gracefully stop services
stop_services() {
    # Stop services using docker-compose if available
    if [ -f "scripts/docker-compose.yml" ]; then
        print_status "Stopping Kafka and Zookeeper containers gracefully..."
        cd scripts
        docker-compose down
        cd ..
        print_success "Services stopped using docker-compose!"
    else
        print_warning "scripts/docker-compose.yml not found. Stopping containers manually..."
        
        # Stop containers manually
        if docker ps --format "{{.Names}}" | grep -q "kafka"; then
            print_status "Stopping Kafka container..."
            docker stop kafka --time 30 || true
        fi
        
        if docker ps --format "{{.Names}}" | grep -q "zookeeper"; then
            print_status "Stopping Zookeeper container..."
            docker stop zookeeper --time 30 || true
        fi
        
        print_success "Containers stopped manually!"
    fi
}

# Function to remove containers
remove_containers() {
    print_status "Removing stopped containers..."
    
    # Remove containers if they exist
    if docker ps -a --format "{{.Names}}" | grep -q "kafka"; then
        docker rm kafka || true
        print_status "Kafka container removed"
    fi
    
    if docker ps -a --format "{{.Names}}" | grep -q "zookeeper"; then
        docker rm zookeeper || true
        print_status "Zookeeper container removed"
    fi
    
    print_success "Containers removed successfully!"
}

# Function to cleanup networks
cleanup_networks() {
    print_status "Cleaning up Docker networks..."
    
    # Remove custom networks if they exist and are not in use
    if docker network ls --format "{{.Name}}" | grep -q "scripts_kafka-network"; then
        docker network rm scripts_kafka-network 2>/dev/null || true
        print_status "Kafka network removed"
    fi
    
    print_success "Network cleanup completed!"
}

# Function to cleanup volumes (optional)
cleanup_volumes() {
    print_status "Checking for unused volumes..."
    
    # List unused volumes
    unused_volumes=$(docker volume ls -qf dangling=true | wc -l | tr -d ' ')
    
    if [ "$unused_volumes" -gt 0 ]; then
        echo "Found $unused_volumes unused volume(s)."
        read -p "Do you want to remove unused volumes? (y/N): " -n 1 -r
        echo
        if [[ $REPLY =~ ^[Yy]$ ]]; then
            docker volume prune -f
            print_success "Unused volumes removed!"
        else
            print_status "Keeping unused volumes."
        fi
    else
        print_status "No unused volumes found."
    fi
}

# Function to verify cleanup
verify_cleanup() {
    print_status "Verifying cleanup..."
    
    # Check for any remaining Kafka/Zookeeper containers
    remaining_containers=$(docker ps -a --filter "name=kafka" --filter "name=zookeeper" --format "{{.Names}}" | wc -l | tr -d ' ')
    
    if [ "$remaining_containers" -eq 0 ]; then
        print_success "All Kafka and Zookeeper containers have been removed!"
    else
        print_warning "Some containers may still exist:"
        docker ps -a --filter "name=kafka" --filter "name=zookeeper" --format "table {{.Names}}\t{{.Status}}"
    fi
    
    # Check if ports are free
    if ! lsof -i :9092 &> /dev/null && ! lsof -i :9093 &> /dev/null && ! lsof -i :2181 &> /dev/null; then
        print_success "All Kafka ports (9092, 9093, 2181) are now free!"
    else
        print_warning "Some ports may still be in use:"
        lsof -i :9092 -i :9093 -i :2181 2>/dev/null || true
    fi
}

# Function to show system status after cleanup
show_system_status() {
    print_status "System Status After Cleanup:"
    echo ""
    
    # Show Docker system info
    echo "Docker System Usage:"
    docker system df 2>/dev/null || true
    echo ""
    
    # Show running containers (should be empty for Kafka/Zookeeper)
    echo "All Running Containers:"
    docker ps --format "table {{.Names}}\t{{.Image}}\t{{.Status}}" || true
    echo ""
}

# Function for force cleanup
force_cleanup() {
    print_warning "Performing force cleanup..."
    
    # Force kill and remove all Kafka/Zookeeper containers
    docker ps -aq --filter "name=kafka" --filter "name=zookeeper" | xargs -r docker rm -f
    
    # Force remove networks
    docker network ls --filter "name=kafka" --format "{{.Name}}" | xargs -r docker network rm 2>/dev/null || true
    
    print_success "Force cleanup completed!"
}

# Main execution
main() {
    echo "================================="
    echo "🛑 Stopping Local Kafka Cluster"
    echo "================================="
    
    # Check if force cleanup is requested
    if [[ "$1" == "--force" || "$1" == "-f" ]]; then
        force_cleanup
        verify_cleanup
        show_system_status
        return 0
    fi
    
    # Normal cleanup process
    if check_running_services; then
        stop_services
        remove_containers
        cleanup_networks
        cleanup_volumes
        verify_cleanup
        show_system_status
        
        print_success "Local Kafka SSL cluster has been stopped and cleaned up!"
        echo ""
        echo "To start again, run: ./start-local-kafka.sh"
        echo "For force cleanup: ./stop-local-kafka.sh --force"
        echo ""
    else
        print_status "Performing cleanup of any remaining resources..."
        remove_containers
        cleanup_networks
        verify_cleanup
        print_success "Cleanup completed!"
    fi
}

# Show usage if help is requested
if [[ "$1" == "--help" || "$1" == "-h" ]]; then
    echo "Usage: $0 [OPTIONS]"
    echo ""
    echo "Stop and cleanup local Kafka SSL cluster"
    echo ""
    echo "Options:"
    echo "  -f, --force    Force cleanup (kill containers immediately)"
    echo "  -h, --help     Show this help message"
    echo ""
    echo "Examples:"
    echo "  $0              # Normal graceful shutdown"
    echo "  $0 --force      # Force cleanup"
    echo ""
    exit 0
fi

# Run main function
main "$@" 