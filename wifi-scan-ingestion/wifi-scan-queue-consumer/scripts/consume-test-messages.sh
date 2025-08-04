#!/bin/bash

# wifi-database/wifi-scan-collection/wifi-scan-queue-consumer/scripts/consume-test-messages.sh
# Script to consume test messages from Kafka topics

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
DEFAULT_GROUP="local-test-group"
KAFKA_SSL_PORT=9093
KAFKA_PLAIN_PORT=9092

# Function to show usage
show_usage() {
    echo "Usage: $0 [topic-name] [--ssl] [--group group-id] [--from-beginning] [--max-messages N] [--timeout N]"
    echo ""
    echo "Consume messages from a Kafka topic"
    echo ""
    echo "Parameters:"
    echo "  topic-name          Topic to consume from (default: '$DEFAULT_TOPIC')"
    echo "  --ssl               Use SSL connection (default: plaintext)"
    echo "  --group group-id    Consumer group ID (default: '$DEFAULT_GROUP')"
    echo "  --from-beginning    Read from beginning of topic"
    echo "  --max-messages N    Maximum messages to consume (default: unlimited)"
    echo "  --timeout N         Timeout in seconds (default: 30)"
    echo ""
    echo "Examples:"
    echo "  $0                                          # Consume from default topic"
    echo "  $0 my-topic                                # Consume from custom topic"
    echo "  $0 my-topic --ssl                          # Consume using SSL"
    echo "  $0 my-topic --from-beginning               # Read all messages from start"
    echo "  $0 my-topic --ssl --group my-group         # Use custom consumer group"
    echo "  $0 my-topic --max-messages 10 --timeout 60 # Consume max 10 messages with 60s timeout"
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
group.id=$1
auto.offset.reset=latest
enable.auto.commit=true
EOF
    
    # Copy client properties to container
    docker cp "$CLIENT_PROPS_FILE" kafka:/tmp/kafka-ssl-consumer.properties
    
    # Cleanup local file
    rm -f "$CLIENT_PROPS_FILE"
}

# Function to verify topic exists
verify_topic_exists() {
    local topic_name="$1"
    local use_ssl="$2"
    
    print_status "Verifying topic '$topic_name' exists..."
    
    local bootstrap_server
    local additional_options=""
    
    if [ "$use_ssl" == "true" ]; then
        bootstrap_server="localhost:$KAFKA_SSL_PORT"
        additional_options="--command-config /tmp/kafka-ssl-consumer.properties"
    else
        bootstrap_server="localhost:$KAFKA_PLAIN_PORT"
    fi
    
    # List topics and check if target topic exists
    local existing_topics
    if [ "$use_ssl" == "true" ]; then
        existing_topics=$(docker exec kafka kafka-topics --bootstrap-server "$bootstrap_server" $additional_options --list 2>/dev/null || echo "")
    else
        existing_topics=$(docker exec kafka kafka-topics --bootstrap-server "$bootstrap_server" --list 2>/dev/null || echo "")
    fi
    
    if ! echo "$existing_topics" | grep -q "^${topic_name}$"; then
        print_error "Topic '$topic_name' does not exist!"
        print_status "Available topics:"
        echo "$existing_topics"
        print_status "Create the topic first: ./create-test-topic.sh $topic_name"
        exit 1
    else
        print_success "Topic '$topic_name' exists!"
        
        # Show topic details
        print_status "Topic details:"
        if [ "$use_ssl" == "true" ]; then
            docker exec kafka kafka-topics --bootstrap-server "$bootstrap_server" $additional_options --describe --topic "$topic_name"
        else
            docker exec kafka kafka-topics --bootstrap-server "$bootstrap_server" --describe --topic "$topic_name"
        fi
    fi
}

# Function to show consumer group info
show_consumer_group_info() {
    local group_id="$1"
    local use_ssl="$2"
    local topic_name="$3"
    
    print_status "Consumer group information:"
    
    local bootstrap_server
    local additional_options=""
    
    if [ "$use_ssl" == "true" ]; then
        bootstrap_server="localhost:$KAFKA_SSL_PORT"
        additional_options="--command-config /tmp/kafka-ssl-consumer.properties"
    else
        bootstrap_server="localhost:$KAFKA_PLAIN_PORT"
    fi
    
    # Show consumer group details (if exists)
    print_status "Checking consumer group '$group_id'..."
    if [ "$use_ssl" == "true" ]; then
        docker exec kafka kafka-consumer-groups --bootstrap-server "$bootstrap_server" $additional_options --describe --group "$group_id" 2>/dev/null || print_warning "Consumer group '$group_id' does not exist yet (will be created)"
    else
        docker exec kafka kafka-consumer-groups --bootstrap-server "$bootstrap_server" --describe --group "$group_id" 2>/dev/null || print_warning "Consumer group '$group_id' does not exist yet (will be created)"
    fi
}

# Function to consume messages
consume_messages() {
    local topic_name="$1"
    local use_ssl="$2"
    local group_id="$3"
    local from_beginning="$4"
    local max_messages="$5"
    local timeout_seconds="$6"
    
    print_status "Starting message consumption from topic '$topic_name'..."
    print_status "Consumer group: $group_id"
    print_status "SSL: $use_ssl"
    print_status "From beginning: $from_beginning"
    print_status "Max messages: ${max_messages:-unlimited}"
    print_status "Timeout: ${timeout_seconds}s"
    echo ""
    
    local bootstrap_server
    local consumer_command=""
    local additional_options=""
    
    if [ "$use_ssl" == "true" ]; then
        bootstrap_server="localhost:$KAFKA_SSL_PORT"
        additional_options="--consumer.config /tmp/kafka-ssl-consumer.properties"
        print_status "Using SSL connection on port $KAFKA_SSL_PORT"
    else
        bootstrap_server="localhost:$KAFKA_PLAIN_PORT"
        print_status "Using plaintext connection on port $KAFKA_PLAIN_PORT"
    fi
    
    # Build consumer command
    consumer_command="kafka-console-consumer --bootstrap-server $bootstrap_server --topic $topic_name"
    
    if [ "$use_ssl" != "true" ]; then
        consumer_command="$consumer_command --group $group_id"
    fi
    
    if [ "$from_beginning" == "true" ]; then
        consumer_command="$consumer_command --from-beginning"
    fi
    
    if [ -n "$max_messages" ]; then
        consumer_command="$consumer_command --max-messages $max_messages"
    fi
    
    consumer_command="$consumer_command $additional_options"
    
    print_success "Starting consumer... (Press Ctrl+C to stop)"
    echo "================== MESSAGES =================="
    
    # Start consuming with timeout
    if timeout "$timeout_seconds" docker exec kafka $consumer_command; then
        print_success "Message consumption completed successfully!"
    else
        local exit_code=$?
        if [ $exit_code -eq 124 ]; then
            print_warning "Consumer timed out after ${timeout_seconds} seconds"
        else
            print_warning "Consumer stopped (exit code: $exit_code)"
        fi
    fi
    
    echo "=============================================="
}

# Function to show post-consumption info
show_post_consumption_info() {
    local group_id="$1"
    local use_ssl="$2"
    local topic_name="$3"
    
    echo ""
    print_status "Post-consumption information:"
    
    local bootstrap_server
    local additional_options=""
    
    if [ "$use_ssl" == "true" ]; then
        bootstrap_server="localhost:$KAFKA_SSL_PORT"
        additional_options="--command-config /tmp/kafka-ssl-consumer.properties"
    else
        bootstrap_server="localhost:$KAFKA_PLAIN_PORT"
    fi
    
    # Show consumer group lag
    print_status "Consumer group lag:"
    if [ "$use_ssl" == "true" ]; then
        docker exec kafka kafka-consumer-groups --bootstrap-server "$bootstrap_server" $additional_options --describe --group "$group_id" 2>/dev/null || print_warning "Consumer group information not available"
    else
        docker exec kafka kafka-consumer-groups --bootstrap-server "$bootstrap_server" --describe --group "$group_id" 2>/dev/null || print_warning "Consumer group information not available"
    fi
}

# Function to show next steps
show_next_steps() {
    local topic_name="$1"
    local use_ssl="$2"
    local group_id="$3"
    
    echo ""
    echo "Next steps:"
    if [ "$use_ssl" == "true" ]; then
        echo "1. Send more messages: ./send-test-message.sh 'New message' $topic_name --ssl"
        echo "2. Consume again: $0 $topic_name --ssl --group $group_id"
        echo "3. View consumer groups: docker exec kafka kafka-consumer-groups --bootstrap-server localhost:9093 --command-config /tmp/kafka-ssl-consumer.properties --list"
    else
        echo "1. Send more messages: ./send-test-message.sh 'New message' $topic_name"
        echo "2. Consume again: $0 $topic_name --group $group_id"
        echo "3. View consumer groups: docker exec kafka kafka-consumer-groups --bootstrap-server localhost:9092 --list"
    fi
    echo "4. Reset consumer group: docker exec kafka kafka-consumer-groups --bootstrap-server localhost:9092 --reset-offsets --group $group_id --topic $topic_name --to-earliest --execute"
    echo ""
}

# Main execution
main() {
    # Parse command line arguments
    local topic_name="${1:-$DEFAULT_TOPIC}"
    local use_ssl="false"
    local group_id="$DEFAULT_GROUP"
    local from_beginning="false"
    local max_messages=""
    local timeout_seconds=30
    
    # Parse options
    local args=("$@")
    for i in "${!args[@]}"; do
        case "${args[i]}" in
            --ssl)
                use_ssl="true"
                ;;
            --group)
                if [ $((i+1)) -lt ${#args[@]} ]; then
                    group_id="${args[$((i+1))]}"
                else
                    print_error "--group requires a group ID"
                    exit 1
                fi
                ;;
            --from-beginning)
                from_beginning="true"
                ;;
            --max-messages)
                if [ $((i+1)) -lt ${#args[@]} ]; then
                    max_messages="${args[$((i+1))]}"
                    if ! [[ "$max_messages" =~ ^[0-9]+$ ]] || [ "$max_messages" -lt 1 ]; then
                        print_error "Invalid max-messages: $max_messages. Must be a positive integer."
                        exit 1
                    fi
                else
                    print_error "--max-messages requires a number"
                    exit 1
                fi
                ;;
            --timeout)
                if [ $((i+1)) -lt ${#args[@]} ]; then
                    timeout_seconds="${args[$((i+1))]}"
                    if ! [[ "$timeout_seconds" =~ ^[0-9]+$ ]] || [ "$timeout_seconds" -lt 1 ]; then
                        print_error "Invalid timeout: $timeout_seconds. Must be a positive integer."
                        exit 1
                    fi
                else
                    print_error "--timeout requires a number"
                    exit 1
                fi
                ;;
        esac
    done
    
    # Show help if requested
    if [[ "$1" == "--help" || "$1" == "-h" ]]; then
        show_usage
        exit 0
    fi
    
    echo "==============================="
    echo "📥 Consuming Messages from Kafka"
    echo "==============================="
    
    echo "Configuration:"
    echo "- Topic: $topic_name"
    echo "- SSL: $use_ssl"
    echo "- Consumer Group: $group_id"
    echo "- From Beginning: $from_beginning"
    echo "- Max Messages: ${max_messages:-unlimited}"
    echo "- Timeout: ${timeout_seconds}s"
    echo ""
    
    check_prerequisites
    
    # Create SSL client properties if needed
    if [ "$use_ssl" == "true" ]; then
        create_ssl_client_properties "$group_id"
    fi
    
    verify_topic_exists "$topic_name" "$use_ssl"
    show_consumer_group_info "$group_id" "$use_ssl" "$topic_name"
    consume_messages "$topic_name" "$use_ssl" "$group_id" "$from_beginning" "$max_messages" "$timeout_seconds"
    show_post_consumption_info "$group_id" "$use_ssl" "$topic_name"
    
    print_success "Message consumption session completed!"
    show_next_steps "$topic_name" "$use_ssl" "$group_id"
}

# Run main function
main "$@" 