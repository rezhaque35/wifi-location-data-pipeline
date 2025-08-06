#!/bin/bash

# wifi-database/wifi-scan-collection/wifi-scan-queue-consumer/scripts/setup-local-kafka.sh
# Complete setup script for local Kafka SSL development environment

set -e  # Exit on any error

echo "🚀 Setting up Local Kafka SSL Development Environment..."

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
    
    # Check Docker
    if ! command -v docker &> /dev/null; then
        print_error "Docker is not installed. Please install Docker Desktop for Mac."
        exit 1
    fi
    
    # Check Docker Compose
    if ! command -v docker-compose &> /dev/null; then
        print_error "Docker Compose is not installed. Please install Docker Desktop for Mac."
        exit 1
    fi
    
    # Check if Docker is running
    if ! docker info &> /dev/null; then
        print_error "Docker is not running. Please start Docker Desktop."
        exit 1
    fi
    
    # Check Java
    if ! command -v java &> /dev/null; then
        print_error "Java is not installed. Please install Java 21."
        exit 1
    fi
    
    # Check keytool
    if ! command -v keytool &> /dev/null; then
        print_error "keytool is not found. Please ensure Java is properly installed."
        exit 1
    fi
    
    print_success "All prerequisites verified!"
}

# Function to create directory structure
create_directories() {
    print_status "Creating directory structure..."
    
    # Create kafka secrets directory
    mkdir -p scripts/kafka/secrets
    
    print_success "Directory structure created!"
}

# Function to create Docker Compose file
create_docker_compose() {
    print_status "Creating Docker Compose configuration..."
    
    cat > scripts/docker-compose.yml << 'EOF'
version: '3.8'

services:
  zookeeper:
    image: confluentinc/cp-zookeeper:7.4.0
    hostname: zookeeper
    container_name: zookeeper
    ports:
      - "2181:2181"
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181
      ZOOKEEPER_TICK_TIME: 2000
    networks:
      - kafka-network

  kafka:
    image: confluentinc/cp-kafka:7.4.0
    hostname: kafka
    container_name: kafka
    depends_on:
      - zookeeper
    ports:
      - "9092:9092"
      - "9093:9093"
    volumes:
      - ./kafka/secrets:/etc/kafka/secrets
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: 'zookeeper:2181'
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: PLAINTEXT:PLAINTEXT,SSL:SSL
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9092,SSL://localhost:9093
      KAFKA_LISTENERS: PLAINTEXT://0.0.0.0:9092,SSL://0.0.0.0:9093
      KAFKA_INTER_BROKER_LISTENER_NAME: PLAINTEXT
      KAFKA_SSL_KEYSTORE_FILENAME: kafka.keystore.p12
      KAFKA_SSL_KEYSTORE_CREDENTIALS: keystore_creds
      KAFKA_SSL_KEY_CREDENTIALS: key_creds
      KAFKA_SSL_TRUSTSTORE_FILENAME: kafka.truststore.p12
      KAFKA_SSL_TRUSTSTORE_CREDENTIALS: truststore_creds
      KAFKA_SSL_KEYSTORE_TYPE: PKCS12
      KAFKA_SSL_TRUSTSTORE_TYPE: PKCS12
      KAFKA_SSL_CLIENT_AUTH: required
      KAFKA_SECURITY_PROTOCOL: SSL
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
      KAFKA_TRANSACTION_STATE_LOG_MIN_ISR: 1
      KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: 1
      KAFKA_AUTO_CREATE_TOPICS_ENABLE: 'true'
    networks:
      - kafka-network

networks:
  kafka-network:
    driver: bridge
EOF
    
    print_success "Docker Compose configuration created!"
}

# Main execution
main() {
    echo "======================================"
    echo "🔧 Kafka SSL Local Development Setup"
    echo "======================================"
    
    check_prerequisites
    create_directories
    create_docker_compose
    
    # Generate SSL certificates
    print_status "Generating SSL certificates..."
    ./generate-ssl-certs.sh
    
    print_success "Local Kafka SSL environment setup completed!"
    echo ""
    echo "Next steps:"
    echo "1. Start Kafka: ./start-local-kafka.sh"
    echo "2. Test SSL: ./test-ssl-connection.sh"
    echo "3. Create topic: ./create-test-topic.sh"
    echo "4. Send message: ./send-test-message.sh 'Hello SSL!'"
    echo "5. Consume messages: ./consume-test-messages.sh"
    echo ""
}

# Run main function
main "$@" 