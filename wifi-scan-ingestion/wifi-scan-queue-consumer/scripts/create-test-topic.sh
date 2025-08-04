#!/bin/bash

# wifi-database/wifi-scan-collection/wifi-scan-queue-consumer/scripts/create-test-topic.sh
# Script to create test topics in local Kafka cluster

set -e  # Exit on any error

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

# Configuration
DEFAULT_TOPIC="test-topic"
DEFAULT_PARTITIONS=3
DEFAULT_REPLICATION_FACTOR=1
KAFKA_SSL_PORT=9093
KAFKA_PLAIN_PORT=9092

# Function to show usage
show_usage() {
    echo "Usage: $0 [topic-name] [partitions] [replication-factor] [--ssl]"
    echo ""
    echo "Create a topic in the local Kafka cluster"
    echo ""
    echo "Parameters:"
    echo "  topic-name           Name of the topic to create (default: $DEFAULT_TOPIC)"
    echo "  partitions          Number of partitions (default: $DEFAULT_PARTITIONS)"
    echo "  replication-factor  Replication factor (default: $DEFAULT_REPLICATION_FACTOR)"
    echo "  --ssl               Use SSL connection (default: plaintext)"
    echo ""
    echo "Examples:"
    echo "  $0                                    # Create '$DEFAULT_TOPIC' with defaults"
    echo "  $0 my-topic                          # Create 'my-topic' with defaults"
    echo "  $0 my-topic 5                        # Create 'my-topic' with 5 partitions"
    echo "  $0 my-topic 5 1 --ssl               # Create using SSL connection"
    echo ""
}

# Function to check prerequisites
check_prerequisites() {
    print_status "Checking prerequisites..."
    
    # Check if Kafka containers are running
    if ! docker ps | grep -q "kafka"; then
        print_error "Kafka container is not running. Please run ./start-local-kafka.sh first."
        exit 1
    fi
    
    print_success "Prerequisites verified!"
}

# Function to create SSL client properties
create_ssl_client_properties() {
    CLIENT_PROPS_FILE="/tmp/kafka-ssl-client.properties"
    cat > "$CLIENT_PROPS_FILE" << EOF
security.protocol=SSL
ssl.truststore.location=/etc/kafka/secrets/kafka.truststore.jks
ssl.truststore.password=kafka123
ssl.keystore.location=/etc/kafka/secrets/kafka.keystore.jks
ssl.keystore.password=kafka123
ssl.key.password=kafka123
EOF
    
    # Copy client properties to container
    docker cp "$CLIENT_PROPS_FILE" kafka:/tmp/kafka-ssl-client.properties
    
    # Cleanup local file
    rm -f "$CLIENT_PROPS_FILE"
}

# Function to create topic
create_topic() {
    local topic_name="$1"
    local partitions="$2"
    local replication_factor="$3"
    local use_ssl="$4"
    
    print_status "Creating topic '$topic_name' with $partitions partitions and replication factor $replication_factor..."
    
    # Determine bootstrap server and additional options
    local bootstrap_server
    local additional_options=""
    
    if [ "$use_ssl" == "true" ]; then
        bootstrap_server="localhost:$KAFKA_SSL_PORT"
        additional_options="--command-config /tmp/kafka-ssl-client.properties"
        print_status "Using SSL connection on port $KAFKA_SSL_PORT"
    else
        bootstrap_server="localhost:$KAFKA_PLAIN_PORT"
        print_status "Using plaintext connection on port $KAFKA_PLAIN_PORT"
    fi
    
    # Check if topic already exists
    print_status "Checking if topic '$topic_name' already exists..."
    
    local existing_topics
    if [ "$use_ssl" == "true" ]; then
        existing_topics=$(docker exec kafka kafka-topics --bootstrap-server "$bootstrap_server" $additional_options --list 2>/dev/null || echo "")
    else
        existing_topics=$(docker exec kafka kafka-topics --bootstrap-server "$bootstrap_server" --list 2>/dev/null || echo "")
    fi
    
    if echo "$existing_topics" | grep -q "^${topic_name}$"; then
        print_warning "Topic '$topic_name' already exists!"
        
        # Show existing topic details
        print_status "Existing topic details:"
        if [ "$use_ssl" == "true" ]; then
            docker exec kafka kafka-topics --bootstrap-server "$bootstrap_server" $additional_options --describe --topic "$topic_name"
        else
            docker exec kafka kafka-topics --bootstrap-server "$bootstrap_server" --describe --topic "$topic_name"
        fi
        return 0
    fi
    
    # Create the topic
    if [ "$use_ssl" == "true" ]; then
        docker exec kafka kafka-topics \
            --bootstrap-server "$bootstrap_server" \
            $additional_options \
            --create \
            --topic "$topic_name" \
            --partitions "$partitions" \
            --replication-factor "$replication_factor"
    else
        docker exec kafka kafka-topics \
            --bootstrap-server "$bootstrap_server" \
            --create \
            --topic "$topic_name" \
            --partitions "$partitions" \
            --replication-factor "$replication_factor"
    fi
    
    if [ $? -eq 0 ]; then
        print_success "Topic '$topic_name' created successfully!"
    else
        print_error "Failed to create topic '$topic_name'"
        return 1
    fi
}

# Function to verify topic creation
verify_topic() {
    local topic_name="$1"
    local use_ssl="$2"
    
    print_status "Verifying topic '$topic_name'..."
    
    local bootstrap_server
    local additional_options=""
    
    if [ "$use_ssl" == "true" ]; then
        bootstrap_server="localhost:$KAFKA_SSL_PORT"
        additional_options="--command-config /tmp/kafka-ssl-client.properties"
    else
        bootstrap_server="localhost:$KAFKA_PLAIN_PORT"
    fi
    
    # Describe the topic
    if [ "$use_ssl" == "true" ]; then
        docker exec kafka kafka-topics --bootstrap-server "$bootstrap_server" $additional_options --describe --topic "$topic_name"
    else
        docker exec kafka kafka-topics --bootstrap-server "$bootstrap_server" --describe --topic "$topic_name"
    fi
    
    if [ $? -eq 0 ]; then
        print_success "Topic '$topic_name' verified successfully!"
    else
        print_error "Failed to verify topic '$topic_name'"
        return 1
    fi
}

# Function to list all topics
list_all_topics() {
    local use_ssl="$1"
    
    print_status "Listing all topics..."
    
    local bootstrap_server
    local additional_options=""
    
    if [ "$use_ssl" == "true" ]; then
        bootstrap_server="localhost:$KAFKA_SSL_PORT"
        additional_options="--command-config /tmp/kafka-ssl-client.properties"
        print_status "Using SSL connection"
    else
        bootstrap_server="localhost:$KAFKA_PLAIN_PORT"
        print_status "Using plaintext connection"
    fi
    
    echo "Available topics:"
    if [ "$use_ssl" == "true" ]; then
        docker exec kafka kafka-topics --bootstrap-server "$bootstrap_server" $additional_options --list
    else
        docker exec kafka kafka-topics --bootstrap-server "$bootstrap_server" --list
    fi
}

# Main execution
main() {
    # Parse command line arguments
    local topic_name="${1:-$DEFAULT_TOPIC}"
    local partitions="${2:-$DEFAULT_PARTITIONS}"
    local replication_factor="${3:-$DEFAULT_REPLICATION_FACTOR}"
    local use_ssl="false"
    
    # Check for SSL flag
    for arg in "$@"; do
        if [ "$arg" == "--ssl" ]; then
            use_ssl="true"
            break
        fi
    done
    
    # Show help if requested
    if [[ "$1" == "--help" || "$1" == "-h" ]]; then
        show_usage
        exit 0
    fi
    
    echo "================================"
    echo "📝 Creating Kafka Topic"
    echo "================================"
    
    echo "Configuration:"
    echo "- Topic Name: $topic_name"
    echo "- Partitions: $partitions"
    echo "- Replication Factor: $replication_factor"
    echo "- SSL: $use_ssl"
    echo ""
    
    check_prerequisites
    
    # Create SSL client properties if needed
    if [ "$use_ssl" == "true" ]; then
        create_ssl_client_properties
    fi
    
    create_topic "$topic_name" "$partitions" "$replication_factor" "$use_ssl"
    verify_topic "$topic_name" "$use_ssl"
    
    echo ""
    list_all_topics "$use_ssl"
    
    print_success "Topic creation completed!"
    echo ""
    echo "Next steps:"
    echo "1. Send messages: ./send-test-message.sh 'Hello $topic_name!' $topic_name"
    echo "2. Consume messages: ./consume-test-messages.sh $topic_name"
    echo ""
}

# Run main function
main "$@" 