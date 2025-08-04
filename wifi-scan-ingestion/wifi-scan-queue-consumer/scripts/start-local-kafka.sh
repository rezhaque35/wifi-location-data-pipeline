#!/bin/bash

# wifi-database/wifi-scan-collection/wifi-scan-queue-consumer/scripts/start-local-kafka.sh
# Script to start local Kafka cluster with SSL support

set -e  # Exit on any error

echo "🚀 Starting Local Kafka SSL Cluster..."

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

# Function to check prerequisites
check_prerequisites() {
    print_status "Checking prerequisites..."
    
    # Check if Docker is running
    if ! docker info &> /dev/null; then
        print_error "Docker is not running. Please start Docker Desktop."
        exit 1
    fi
    
    # Check if docker-compose.yml exists
    if [ ! -f "scripts/docker-compose.yml" ]; then
        print_error "scripts/docker-compose.yml not found. Please run ./setup-local-kafka.sh first."
        exit 1
    fi
    
    # Check if SSL certificates exist
    if [ ! -f "scripts/kafka/secrets/kafka.keystore.p12" ] || [ ! -f "scripts/kafka/secrets/kafka.truststore.p12" ]; then
        print_error "SSL certificates not found. Please run ./setup-local-kafka.sh first."
        exit 1
    fi
    
    print_success "Prerequisites verified!"
}

# Function to check if services are already running
check_existing_services() {
    print_status "Checking for existing services..."
    
    if docker ps | grep -q "kafka\|zookeeper"; then
        print_warning "Kafka or Zookeeper containers are already running."
        echo "Existing containers:"
        docker ps --filter "name=kafka" --filter "name=zookeeper" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
        echo ""
        read -p "Do you want to stop existing containers and restart? (y/N): " -n 1 -r
        echo
        if [[ $REPLY =~ ^[Yy]$ ]]; then
            print_status "Stopping existing containers..."
            ./scripts/stop-local-kafka.sh
        else
            print_warning "Keeping existing containers running."
            exit 0
        fi
    fi
}

# Function to start services
start_services() {
    print_status "Starting Kafka and Zookeeper containers..."
    
    # Start services in detached mode
    cd scripts
    docker-compose up -d
    cd ..
    
    print_success "Containers started successfully!"
}

# Function to wait for services to be ready
wait_for_services() {
    print_status "Waiting for services to be ready..."
    
    # Wait for Zookeeper
    print_status "Waiting for Zookeeper to be ready..."
    local zk_ready=false
    for i in {1..30}; do
        # Primary check: Use the whitelisted 'srvr' command to check zookeeper status
        if docker exec zookeeper bash -c "echo 'srvr' | nc localhost 2181" 2>/dev/null | grep -q "Zookeeper version"; then
            zk_ready=true
            break
        fi
        
        # Fallback check: Try to connect to zookeeper port from host
        if timeout 3 bash -c "echo > /dev/tcp/localhost/2181" 2>/dev/null; then
            # Double-check with srvr command after successful connection
            if docker exec zookeeper bash -c "echo 'srvr' | nc localhost 2181" 2>/dev/null | grep -q "Mode:"; then
                zk_ready=true
                break
            fi
        fi
        
        echo -n "."
        sleep 2
    done
    
    if [ "$zk_ready" = false ]; then
        print_error "Zookeeper failed to start within 60 seconds"
        print_status "Checking Zookeeper logs..."
        docker logs zookeeper --tail 20
        print_status "Checking container status..."
        docker ps --filter "name=zookeeper" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
        exit 1
    fi
    print_success "Zookeeper is ready!"
    
    # Wait for Kafka
    print_status "Waiting for Kafka to be ready..."
    local kafka_ready=false
    for i in {1..60}; do
        if docker exec kafka kafka-broker-api-versions --bootstrap-server localhost:9092 &> /dev/null; then
            kafka_ready=true
            break
        fi
        echo -n "."
        sleep 2
    done
    
    if [ "$kafka_ready" = false ]; then
        print_error "Kafka failed to start within 120 seconds"
        print_status "Checking Kafka logs..."
        docker logs kafka --tail 50
        exit 1
    fi
    print_success "Kafka is ready!"
    
    # Test SSL port
    print_status "Testing SSL connectivity..."
    if timeout 10 bash -c "echo > /dev/tcp/localhost/9093" &> /dev/null; then
        print_success "SSL port 9093 is accessible!"
    else
        print_warning "SSL port 9093 might not be ready yet. This could be normal during startup."
    fi
}

# Function to display service status
show_service_status() {
    print_status "Service Status:"
    echo ""
    
    # Show running containers
    echo "Running Containers:"
    docker ps --filter "name=kafka" --filter "name=zookeeper" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
    echo ""
    
    # Show resource usage
    echo "Resource Usage:"
    docker stats --no-stream --format "table {{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}" kafka zookeeper 2>/dev/null || true
    echo ""
    
    # Show service endpoints
    echo "Service Endpoints:"
    echo "- Zookeeper: localhost:2181"
    echo "- Kafka (PLAINTEXT): localhost:9092"
    echo "- Kafka (SSL): localhost:9093"
    echo ""
    
    # Show log tails
    print_status "Recent Kafka logs:"
    docker logs kafka --tail 10 2>/dev/null || true
}

# Function to create a test topic
create_test_topic() {
    print_status "Creating test topic..."
    
    # Check if test-topic already exists
    if docker exec kafka kafka-topics --bootstrap-server localhost:9092 --list | grep -q "test-topic"; then
        print_warning "test-topic already exists"
    else
        docker exec kafka kafka-topics --bootstrap-server localhost:9092 \
            --create --topic test-topic --partitions 1 --replication-factor 1
        print_success "test-topic created successfully!"
    fi
}

# Main execution
main() {
    echo "================================"
    echo "🚀 Starting Local Kafka Cluster"
    echo "================================"
    
    check_prerequisites
    check_existing_services
    start_services
    wait_for_services
    create_test_topic
    show_service_status
    
    print_success "Local Kafka SSL cluster is now running!"
    echo ""
    echo "Next steps:"
    echo "1. Test SSL connection: ./test-ssl-connection.sh"
    echo "2. Send test message: ./send-test-message.sh 'Hello Kafka!'"
    echo "3. Consume messages: ./consume-test-messages.sh"
    echo "4. Stop cluster: ./scripts/stop-local-kafka.sh"
    echo ""
    echo "Useful commands:"
    echo "- View logs: docker logs kafka -f"
    echo "- Check topics: docker exec kafka kafka-topics --bootstrap-server localhost:9092 --list"
    echo "- Access Kafka shell: docker exec -it kafka bash"
    echo ""
}

# Run main function
main "$@" 