#!/bin/bash

# wifi-database/wifi-scan-collection/wifi-scan-queue-consumer/scripts/send-test-message.sh
# Script to send test messages to Kafka topics

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
DEFAULT_MESSAGE="Hello Kafka SSL!"
KAFKA_SSL_PORT=9093
KAFKA_PLAIN_PORT=9092

# Function to show usage
show_usage() {
    echo "Usage: $0 [message] [topic-name] [--ssl] [--count N]"
    echo ""
    echo "Send test messages to a Kafka topic"
    echo ""
    echo "Parameters:"
    echo "  message              Message to send (default: '$DEFAULT_MESSAGE')"
    echo "  topic-name          Target topic (default: '$DEFAULT_TOPIC')"
    echo "  --ssl               Use SSL connection (default: plaintext)"
    echo "  --count N           Send N messages (default: 1)"
    echo ""
    echo "Examples:"
    echo "  $0                                        # Send default message to default topic"
    echo "  $0 'Hello World!'                        # Send custom message to default topic"
    echo "  $0 'Hello SSL!' my-topic                 # Send message to custom topic"
    echo "  $0 'Hello SSL!' my-topic --ssl           # Send using SSL"
    echo "  $0 'Test msg' my-topic --ssl --count 5   # Send 5 messages using SSL"
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
ssl.truststore.location=/etc/kafka/secrets/kafka.truststore.p12
ssl.truststore.password=kafka123
ssl.truststore.type=PKCS12
ssl.keystore.location=/etc/kafka/secrets/kafka.keystore.p12
ssl.keystore.password=kafka123
ssl.keystore.type=PKCS12
ssl.key.password=kafka123
EOF
    
    # Copy client properties to container
    docker cp "$CLIENT_PROPS_FILE" kafka:/tmp/kafka-ssl-client.properties
    
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
        additional_options="--command-config /tmp/kafka-ssl-client.properties"
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
        print_warning "Topic '$topic_name' does not exist. Creating it..."
        
        # Create the topic
        if [ "$use_ssl" == "true" ]; then
            docker exec kafka kafka-topics \
                --bootstrap-server "$bootstrap_server" \
                $additional_options \
                --create \
                --topic "$topic_name" \
                --partitions 3 \
                --replication-factor 1
        else
            docker exec kafka kafka-topics \
                --bootstrap-server "$bootstrap_server" \
                --create \
                --topic "$topic_name" \
                --partitions 3 \
                --replication-factor 1
        fi
        
        print_success "Topic '$topic_name' created successfully!"
    else
        print_success "Topic '$topic_name' exists!"
    fi
}

# Function to send messages
send_messages() {
    local message="$1"
    local topic_name="$2"
    local use_ssl="$3"
    local message_count="$4"
    
    print_status "Sending $message_count message(s) to topic '$topic_name'..."
    
    local bootstrap_server
    local additional_options=""
    
    if [ "$use_ssl" == "true" ]; then
        bootstrap_server="localhost:$KAFKA_SSL_PORT"
        additional_options="--producer.config /tmp/kafka-ssl-client.properties"
        print_status "Using SSL connection on port $KAFKA_SSL_PORT"
    else
        bootstrap_server="localhost:$KAFKA_PLAIN_PORT"
        print_status "Using plaintext connection on port $KAFKA_PLAIN_PORT"
    fi
    
    # Generate and send messages
    for i in $(seq 1 "$message_count"); do
        local actual_message
        local display_message
        
        # Check if message looks like JSON - if so, don't append timestamp
        if [[ "$message" =~ ^\{.*\}$ ]]; then
            # JSON message - send as-is
            if [ "$message_count" -eq 1 ]; then
                actual_message="$message"
                display_message="$message"
            else
                actual_message="$message"
                display_message="$message (message #$i)"
            fi
        else
            # Plain text message - add timestamp as before
            if [ "$message_count" -eq 1 ]; then
                actual_message="$message [$(date '+%Y-%m-%d %H:%M:%S')]"
                display_message="$actual_message"
            else
                actual_message="$message #$i [$(date '+%Y-%m-%d %H:%M:%S')]"
                display_message="$actual_message"
            fi
        fi
        
        print_status "Sending message $i/$message_count: '$display_message'"
        
        # Send the message
        if [ "$use_ssl" == "true" ]; then
            echo "$actual_message" | docker exec -i kafka kafka-console-producer \
                --bootstrap-server "$bootstrap_server" \
                --topic "$topic_name" \
                $additional_options
        else
            echo "$actual_message" | docker exec -i kafka kafka-console-producer \
                --bootstrap-server "$bootstrap_server" \
                --topic "$topic_name"
        fi
        
        if [ $? -eq 0 ]; then
            print_success "Message $i sent successfully!"
        else
            print_error "Failed to send message $i"
            return 1
        fi
        
        # Small delay between messages if sending multiple
        if [ "$message_count" -gt 1 ] && [ "$i" -lt "$message_count" ]; then
            sleep 0.5
        fi
    done
}

# Function to verify message delivery
verify_message_delivery() {
    local topic_name="$1"
    local use_ssl="$2"
    local expected_count="$3"
    
    print_status "Verifying message delivery to topic '$topic_name'..."
    
    local bootstrap_server
    local additional_options=""
    
    if [ "$use_ssl" == "true" ]; then
        bootstrap_server="localhost:$KAFKA_SSL_PORT"
        additional_options="--consumer.config /tmp/kafka-ssl-client.properties"
    else
        bootstrap_server="localhost:$KAFKA_PLAIN_PORT"
    fi
    
    # Check topic details
    print_status "Topic details:"
    if [ "$use_ssl" == "true" ]; then
        docker exec kafka kafka-topics --bootstrap-server "$bootstrap_server" --command-config /tmp/kafka-ssl-client.properties --describe --topic "$topic_name"
    else
        docker exec kafka kafka-topics --bootstrap-server "$bootstrap_server" --describe --topic "$topic_name"
    fi
    
    # Show last few messages (if any)
    print_status "Recent messages in topic (last 5):"
    if [ "$use_ssl" == "true" ]; then
        timeout 5 docker exec kafka kafka-console-consumer \
            --bootstrap-server "$bootstrap_server" \
            --topic "$topic_name" \
            --from-beginning \
            --max-messages 5 \
            $additional_options 2>/dev/null || print_warning "No messages found or timeout reached"
    else
        timeout 5 docker exec kafka kafka-console-consumer \
            --bootstrap-server "$bootstrap_server" \
            --topic "$topic_name" \
            --from-beginning \
            --max-messages 5 2>/dev/null || print_warning "No messages found or timeout reached"
    fi
}

# Function to show next steps
show_next_steps() {
    local topic_name="$1"
    local use_ssl="$2"
    
    echo ""
    echo "Next steps:"
    if [ "$use_ssl" == "true" ]; then
        echo "1. Consume messages: ./consume-test-messages.sh $topic_name --ssl"
    else
        echo "1. Consume messages: ./consume-test-messages.sh $topic_name"
    fi
    echo "2. Send more messages: $0 'Another message' $topic_name"
    echo "3. View topic details: docker exec kafka kafka-topics --bootstrap-server localhost:9092 --describe --topic $topic_name"
    echo ""
}

# Main execution
main() {
    # Parse command line arguments
    local message="${1:-$DEFAULT_MESSAGE}"
    local topic_name="${2:-$DEFAULT_TOPIC}"
    local use_ssl="false"
    local message_count=1
    
    # Parse options
    local args=("$@")
    for i in "${!args[@]}"; do
        case "${args[i]}" in
            --ssl)
                use_ssl="true"
                ;;
            --count)
                if [ $((i+1)) -lt ${#args[@]} ]; then
                    message_count="${args[$((i+1))]}"
                    if ! [[ "$message_count" =~ ^[0-9]+$ ]] || [ "$message_count" -lt 1 ]; then
                        print_error "Invalid count: $message_count. Must be a positive integer."
                        exit 1
                    fi
                else
                    print_error "--count requires a number"
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
    
    echo "=============================="
    echo "📨 Sending Messages to Kafka"
    echo "=============================="
    
    echo "Configuration:"
    echo "- Message: '$message'"
    echo "- Topic: $topic_name"
    echo "- SSL: $use_ssl"
    echo "- Count: $message_count"
    echo ""
    
    check_prerequisites
    
    # Create SSL client properties if needed
    if [ "$use_ssl" == "true" ]; then
        create_ssl_client_properties
    fi
    
    verify_topic_exists "$topic_name" "$use_ssl"
    send_messages "$message" "$topic_name" "$use_ssl" "$message_count"
    verify_message_delivery "$topic_name" "$use_ssl" "$message_count"
    
    print_success "Message sending completed successfully!"
    show_next_steps "$topic_name" "$use_ssl"
}

# Run main function
main "$@" 