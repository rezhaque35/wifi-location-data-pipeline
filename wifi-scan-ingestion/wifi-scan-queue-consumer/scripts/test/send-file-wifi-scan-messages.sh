#!/bin/bash

# wifi-database/wifi-scan-collection/wifi-scan-queue-consumer/scripts/send-file-wifi-scan-messages.sh
# Script to send WiFi scan data messages from file to Kafka topics
# Reads configuration from application.yml to ensure consistency with the service

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

# Default configuration
DEFAULT_INTERVAL=2

# Configuration variables (will be populated from application.yml)
KAFKA_BOOTSTRAP_SERVERS=""
KAFKA_TOPIC=""
KAFKA_SSL_ENABLED=""
KAFKA_SSL_KEYSTORE_LOCATION=""
KAFKA_SSL_KEYSTORE_PASSWORD=""
KAFKA_SSL_TRUSTSTORE_LOCATION=""
KAFKA_SSL_TRUSTSTORE_PASSWORD=""
KAFKA_SSL_PORT=""
KAFKA_PLAIN_PORT=""

# Function to show usage
show_usage() {
    echo "Usage: $0 --file FILE [--count N] [--interval SECONDS] [--topic TOPIC] [--ssl|--no-ssl] [--help]"
    echo ""
    echo "Send WiFi scan data messages from file to Kafka"
    echo ""
    echo "🔧 Configuration Source:"
    echo "  This script reads Kafka configuration from src/main/resources/application.yml"
    echo "  to ensure messages are sent to the same broker/topic the service consumes from."
    echo ""
    echo "Parameters:"
    echo "  --file FILE             JSON file containing WiFi scan messages (required)"
    echo "  --count N               Number of times to send the message(s) from file (default: 1)"
    echo "  --interval SECONDS      Interval between messages in seconds (default: $DEFAULT_INTERVAL)"
    echo "  --topic TOPIC          Override topic from application.yml (not recommended)"
    echo "  --ssl                  Force SSL connection (overrides application.yml)"
    echo "  --no-ssl               Force plaintext connection (overrides application.yml)"
    echo "  --help                 Show this help message"
    echo ""
    echo "📝 File Format:"
    echo "  - Single JSON object: Sends one message (repeated --count times)"
    echo "  - JSON array: Sends each element as a separate message (array repeated --count times)"
    echo "  - JSONL (newline-delimited JSON): Sends each line as a separate message (repeated --count times)"
    echo ""
    echo "Examples:"
    echo "  $0 --file message.json                     # Send single message once"
    echo "  $0 --file message.json --count 5           # Send same message 5 times"
    echo "  $0 --file messages.json --count 3          # Send array 3 times (each element sent 3 times)"
    echo "  $0 --file debug-case.json --topic test     # Send to custom topic"
    echo "  $0 --file production-issue.jsonl --count 10 # Replay log file 10 times"
    echo ""
}

# Function to find application.yml file
find_application_yml() {
    local script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
    local project_root="$(cd "$script_dir/../.." && pwd)"
    local app_yml="$project_root/src/main/resources/application.yml"
    
    if [ -f "$app_yml" ]; then
        echo "$app_yml"
        return 0
    else
        print_error "application.yml not found at: $app_yml"
        return 1
    fi
}

# Function to parse YAML using grep and sed (simple parser for our needs)
parse_yaml_value() {
    local yaml_file="$1"
    local key_path="$2"
    local default_value="$3"
    
    # Simple YAML parser for single-level and two-level keys
    local value=""
    
    case "$key_path" in
        "kafka.bootstrap-servers")
            value=$(grep -A0 "^kafka:" "$yaml_file" -A 20 | grep "bootstrap-servers:" | sed 's/.*bootstrap-servers: *//' | tr -d ' ')
            ;;
        "kafka.topic.name")
            value=$(grep -A0 "^kafka:" "$yaml_file" -A 20 | grep "name:" | head -1 | sed 's/.*name: *//' | tr -d ' ')
            ;;
        "kafka.ssl.enabled")
            value=$(grep -A0 "^kafka:" "$yaml_file" -A 30 | grep "enabled:" | head -1 | sed 's/.*enabled: *//' | tr -d ' ')
            ;;
        "kafka.ssl.keystore.location")
            value=$(grep -A0 "keystore:" "$yaml_file" -A 5 | grep "location:" | sed 's/.*location: *//' | sed 's/\${[^:]*://' | sed 's/}//' | tr -d ' ')
            ;;
        "kafka.ssl.keystore.password")
            value=$(grep -A0 "keystore:" "$yaml_file" -A 5 | grep "password:" | head -1 | sed 's/.*password: *//' | sed 's/\${[^:]*://' | sed 's/}//' | tr -d ' ')
            ;;
        "kafka.ssl.truststore.location")
            value=$(grep -A0 "truststore:" "$yaml_file" -A 5 | grep "location:" | sed 's/.*location: *//' | sed 's/\${[^:]*://' | sed 's/}//' | tr -d ' ')
            ;;
        "kafka.ssl.truststore.password")
            value=$(grep -A0 "truststore:" "$yaml_file" -A 5 | grep "password:" | head -1 | sed 's/.*password: *//' | sed 's/\${[^:]*://' | sed 's/}//' | tr -d ' ')
            ;;
    esac
    
    # Return value or default
    if [ -z "$value" ]; then
        echo "$default_value"
    else
        echo "$value"
    fi
}

# Function to load configuration from application.yml
load_config_from_yml() {
    print_status "Loading configuration from application.yml..."
    
    local app_yml=$(find_application_yml)
    if [ -z "$app_yml" ]; then
        print_error "Could not find application.yml. Using default configuration."
        return 1
    fi
    
    print_success "Found application.yml at: $app_yml"
    
    # Parse Kafka configuration
    KAFKA_BOOTSTRAP_SERVERS=$(parse_yaml_value "$app_yml" "kafka.bootstrap-servers" "localhost:9093")
    KAFKA_TOPIC=$(parse_yaml_value "$app_yml" "kafka.topic.name" "wifi-scan-data")
    KAFKA_SSL_ENABLED=$(parse_yaml_value "$app_yml" "kafka.ssl.enabled" "true")
    KAFKA_SSL_KEYSTORE_LOCATION=$(parse_yaml_value "$app_yml" "kafka.ssl.keystore.location" "scripts/kafka/secrets/kafka.keystore.p12")
    KAFKA_SSL_KEYSTORE_PASSWORD=$(parse_yaml_value "$app_yml" "kafka.ssl.keystore.password" "kafka123")
    KAFKA_SSL_TRUSTSTORE_LOCATION=$(parse_yaml_value "$app_yml" "kafka.ssl.truststore.location" "scripts/kafka/secrets/kafka.truststore.p12")
    KAFKA_SSL_TRUSTSTORE_PASSWORD=$(parse_yaml_value "$app_yml" "kafka.ssl.truststore.password" "kafka123")
    
    # Extract port from bootstrap servers
    if [[ "$KAFKA_BOOTSTRAP_SERVERS" =~ :([0-9]+)$ ]]; then
        local port="${BASH_REMATCH[1]}"
        if [ "$port" = "9093" ]; then
            KAFKA_SSL_PORT=9093
            KAFKA_PLAIN_PORT=9092
        else
            KAFKA_SSL_PORT="$port"
            KAFKA_PLAIN_PORT="$port"
        fi
    else
        KAFKA_SSL_PORT=9093
        KAFKA_PLAIN_PORT=9092
    fi
    
    print_success "Configuration loaded from application.yml:"
    print_status "  Bootstrap Servers: $KAFKA_BOOTSTRAP_SERVERS"
    print_status "  Topic: $KAFKA_TOPIC"
    print_status "  SSL Enabled: $KAFKA_SSL_ENABLED"
    print_status "  SSL Port: $KAFKA_SSL_PORT"
    
    return 0
}

# Function to check prerequisites
check_prerequisites() {
    print_status "Checking prerequisites..."
    
    # Check if Kafka containers are running
    if ! docker ps | grep -q "kafka"; then
        print_error "Kafka container is not running. Please run ./start-local-kafka.sh first."
        exit 1
    fi
    
    # Check if jq is available for JSON validation
    if ! command -v jq &> /dev/null; then
        print_error "jq is required for JSON validation. Please install it: brew install jq"
        exit 1
    fi
    
    print_success "Prerequisites verified!"
}

# Function to create SSL client properties from application.yml configuration
create_ssl_client_properties() {
    print_status "Creating SSL client properties from application.yml configuration..."
    
    # Map local paths to container paths
    local container_keystore="/etc/kafka/secrets/kafka.keystore.p12"
    local container_truststore="/etc/kafka/secrets/kafka.truststore.p12"
    
    CLIENT_PROPS_FILE="/tmp/kafka-ssl-client.properties"
    cat > "$CLIENT_PROPS_FILE" << EOF
security.protocol=SSL
ssl.truststore.location=$container_truststore
ssl.truststore.password=$KAFKA_SSL_TRUSTSTORE_PASSWORD
ssl.truststore.type=PKCS12
ssl.keystore.location=$container_keystore
ssl.keystore.password=$KAFKA_SSL_KEYSTORE_PASSWORD
ssl.keystore.type=PKCS12
ssl.key.password=$KAFKA_SSL_KEYSTORE_PASSWORD
EOF
    
    # Copy client properties to container
    docker cp "$CLIENT_PROPS_FILE" kafka:/tmp/kafka-ssl-client.properties
    
    print_success "SSL client properties created with configuration from application.yml"
    
    # Cleanup local file
    rm -f "$CLIENT_PROPS_FILE"
}

# Function to detect file format and parse messages
parse_message_file() {
    local file_path="$1"
    
    if [ ! -f "$file_path" ]; then
        print_error "File not found: $file_path"
        return 1
    fi
    
    print_status "Parsing message file: $file_path"
    
    # Try to detect file format
    local first_char=$(head -c 1 "$file_path")
    
    if [ "$first_char" = "[" ]; then
        # JSON array format
        print_status "Detected JSON array format"
        local message_count=$(jq '. | length' "$file_path" 2>/dev/null)
        if [ -z "$message_count" ] || [ "$message_count" = "null" ]; then
            print_error "Invalid JSON array format in file"
            return 1
        fi
        echo "array:$message_count"
        return 0
    elif [ "$first_char" = "{" ]; then
        # Check if it's a single JSON object or JSONL
        local line_count=$(grep -c "^{" "$file_path")
        if [ "$line_count" -eq 1 ]; then
            print_status "Detected single JSON object format"
            # Validate JSON
            if ! jq empty "$file_path" 2>/dev/null; then
                print_error "Invalid JSON format in file"
                return 1
            fi
            echo "single:1"
            return 0
        else
            print_status "Detected JSONL (newline-delimited JSON) format"
            echo "jsonl:$line_count"
            return 0
        fi
    else
        print_error "Unknown file format. Expected JSON object, JSON array, or JSONL format."
        return 1
    fi
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

# Function to send messages from file
send_messages_from_file() {
    local file_path="$1"
    local topic_name="$2"
    local use_ssl="$3"
    local interval="$4"
    local repeat_count="$5"
    
    local format_info=$(parse_message_file "$file_path" 2>&1)
    if [ $? -ne 0 ]; then
        return 1
    fi
    
    local format_type=$(echo "$format_info" | tail -1 | cut -d: -f1)
    local messages_in_file=$(echo "$format_info" | tail -1 | cut -d: -f2)
    local total_messages=$((messages_in_file * repeat_count))
    
    print_status "Sending $messages_in_file message(s) from file, repeated $repeat_count time(s) = $total_messages total messages"
    print_status "Sending to topic '$topic_name' with ${interval}s interval..."
    
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
    
    # Send messages based on format type
    local message_number=0
    
    case "$format_type" in
        "single")
            local message=$(jq -c . "$file_path")
            
            for repeat in $(seq 1 "$repeat_count"); do
                message_number=$((message_number + 1))
                print_status "Sending message $message_number/$total_messages (iteration $repeat/$repeat_count)..."
                print_status "Message preview: $(echo "$message" | jq -r '.client // .requestId // "N/A"' 2>/dev/null)"
                
                if [ "$use_ssl" == "true" ]; then
                    echo "$message" | docker exec -i kafka kafka-console-producer \
                        --bootstrap-server "$bootstrap_server" \
                        --topic "$topic_name" \
                        $additional_options
                else
                    echo "$message" | docker exec -i kafka kafka-console-producer \
                        --bootstrap-server "$bootstrap_server" \
                        --topic "$topic_name"
                fi
                
                if [ $? -eq 0 ]; then
                    print_success "Message $message_number sent successfully!"
                else
                    print_error "Failed to send message $message_number"
                    return 1
                fi
                
                # Wait for interval if not the last message
                if [ "$message_number" -lt "$total_messages" ]; then
                    sleep "$interval"
                fi
            done
            ;;
            
        "array")
            for repeat in $(seq 1 "$repeat_count"); do
                for i in $(seq 0 $((messages_in_file - 1))); do
                    message_number=$((message_number + 1))
                    print_status "Sending message $message_number/$total_messages (iteration $repeat/$repeat_count, element $((i+1))/$messages_in_file)..."
                    local message=$(jq -c ".[$i]" "$file_path")
                    print_status "Message preview: $(echo "$message" | jq -r '.client // .requestId // "N/A"' 2>/dev/null)"
                    
                    if [ "$use_ssl" == "true" ]; then
                        echo "$message" | docker exec -i kafka kafka-console-producer \
                            --bootstrap-server "$bootstrap_server" \
                            --topic "$topic_name" \
                            $additional_options
                    else
                        echo "$message" | docker exec -i kafka kafka-console-producer \
                            --bootstrap-server "$bootstrap_server" \
                            --topic "$topic_name"
                    fi
                    
                    if [ $? -eq 0 ]; then
                        print_success "Message $message_number sent successfully!"
                    else
                        print_error "Failed to send message $message_number"
                        return 1
                    fi
                    
                    # Wait for interval if not the last message
                    if [ "$message_number" -lt "$total_messages" ]; then
                        sleep "$interval"
                    fi
                done
            done
            ;;
            
        "jsonl")
            for repeat in $(seq 1 "$repeat_count"); do
                local line_num=0
                while IFS= read -r line; do
                    line_num=$((line_num + 1))
                    message_number=$((message_number + 1))
                    print_status "Sending message $message_number/$total_messages (iteration $repeat/$repeat_count, line $line_num/$messages_in_file)..."
                    
                    # Validate and compact the JSON line
                    local message=$(echo "$line" | jq -c . 2>/dev/null)
                    if [ -z "$message" ]; then
                        print_warning "Skipping invalid JSON on line $line_num"
                        continue
                    fi
                    
                    print_status "Message preview: $(echo "$message" | jq -r '.client // .requestId // "N/A"' 2>/dev/null)"
                    
                    if [ "$use_ssl" == "true" ]; then
                        echo "$message" | docker exec -i kafka kafka-console-producer \
                            --bootstrap-server "$bootstrap_server" \
                            --topic "$topic_name" \
                            $additional_options
                    else
                        echo "$message" | docker exec -i kafka kafka-console-producer \
                            --bootstrap-server "$bootstrap_server" \
                            --topic "$topic_name"
                    fi
                    
                    if [ $? -eq 0 ]; then
                        print_success "Message $message_number sent successfully!"
                    else
                        print_error "Failed to send message $message_number"
                        return 1
                    fi
                    
                    # Wait for interval if not the last message
                    if [ "$message_number" -lt "$total_messages" ]; then
                        sleep "$interval"
                    fi
                done < "$file_path"
            done
            ;;
    esac
    
    return 0
}

# Function to show summary and next steps
show_summary() {
    local file_path="$1"
    local topic_name="$2"
    local use_ssl="$3"
    local total_messages="$4"
    local repeat_count="$5"
    
    echo ""
    print_success "WiFi scan message sending completed!"
    echo "Summary:"
    echo "- Source File: $file_path"
    echo "- Topic: $topic_name"
    echo "- Messages sent: $total_messages (repeated $repeat_count time(s))"
    echo "- SSL: $use_ssl"
    echo ""
    echo "Next steps:"
    echo "1. Check service metrics: curl http://localhost:8080/frisco-location-wifi-scan-vmb-consumer/api/metrics/kafka"
    echo "2. Monitor application logs for message processing"
    if [ "$use_ssl" == "true" ]; then
        echo "3. Consume messages: ../setup/consume-test-messages.sh $topic_name --ssl"
    else
        echo "3. Consume messages: ../setup/consume-test-messages.sh $topic_name"
    fi
    echo ""
}

# Main execution
main() {
    echo "=========================================="
    echo "📄 WiFi Scan Message File Sender"
    echo "=========================================="
    echo ""
    
    # Load configuration from application.yml first
    load_config_from_yml
    
    # Default values from loaded configuration
    local file_path=""
    local topic_name="$KAFKA_TOPIC"
    local use_ssl="$KAFKA_SSL_ENABLED"
    local interval="$DEFAULT_INTERVAL"
    local repeat_count=1
    local config_override=false
    
    # Parse command line arguments (can override application.yml values)
    while [[ $# -gt 0 ]]; do
        case $1 in
            --file)
                file_path="$2"
                if [ -z "$file_path" ]; then
                    print_error "File path cannot be empty."
                    exit 1
                fi
                shift 2
                ;;
            --count)
                repeat_count="$2"
                if ! [[ "$repeat_count" =~ ^[0-9]+$ ]] || [ "$repeat_count" -lt 1 ]; then
                    print_error "Invalid count: $repeat_count. Must be a positive integer."
                    exit 1
                fi
                shift 2
                ;;
            --interval)
                interval="$2"
                if ! [[ "$interval" =~ ^[0-9]+(\.[0-9]+)?$ ]] || (( $(echo "$interval <= 0" | bc -l) )); then
                    print_error "Invalid interval: $interval. Must be a positive number."
                    exit 1
                fi
                shift 2
                ;;
            --topic)
                topic_name="$2"
                config_override=true
                print_warning "Overriding topic from application.yml: $KAFKA_TOPIC -> $topic_name"
                if [ -z "$topic_name" ]; then
                    print_error "Topic name cannot be empty."
                    exit 1
                fi
                shift 2
                ;;
            --ssl)
                use_ssl="true"
                config_override=true
                print_warning "Overriding SSL setting from application.yml: $KAFKA_SSL_ENABLED -> true"
                shift
                ;;
            --no-ssl)
                use_ssl="false"
                config_override=true
                print_warning "Overriding SSL setting from application.yml: $KAFKA_SSL_ENABLED -> false"
                shift
                ;;
            --help|-h)
                show_usage
                exit 0
                ;;
            *)
                print_error "Unknown option: $1"
                show_usage
                exit 1
                ;;
        esac
    done
    
    # Validate required arguments
    if [ -z "$file_path" ]; then
        print_error "Missing required argument: --file"
        echo ""
        show_usage
        exit 1
    fi
    
    # Check if file exists
    if [ ! -f "$file_path" ]; then
        print_error "File not found: $file_path"
        exit 1
    fi
    
    echo ""
    print_success "Configuration Summary:"
    echo "┌─────────────────────────────────────────┐"
    echo "│ Source: application.yml                 │"
    echo "├─────────────────────────────────────────┤"
    echo "│ Bootstrap Servers: $KAFKA_BOOTSTRAP_SERVERS"
    echo "│ Topic: $topic_name"
    echo "│ Message File: $file_path"
    echo "│ Repeat Count: $repeat_count"
    echo "│ Interval: ${interval}s"
    echo "│ SSL Enabled: $use_ssl"
    if [ "$config_override" = true ]; then
        echo "│ ⚠️  Command-line overrides applied"
    fi
    echo "└─────────────────────────────────────────┘"
    echo ""
    
    check_prerequisites
    
    # Create SSL client properties if SSL is enabled
    if [ "$use_ssl" == "true" ]; then
        create_ssl_client_properties
    fi
    
    verify_topic_exists "$topic_name" "$use_ssl"
    
    send_messages_from_file "$file_path" "$topic_name" "$use_ssl" "$interval" "$repeat_count"
    
    if [ $? -eq 0 ]; then
        # Get message count for summary after successful send
        local format_info=$(parse_message_file "$file_path" 2>/dev/null)
        local messages_in_file=$(echo "$format_info" | tail -1 | cut -d: -f2)
        local total_messages=$((messages_in_file * repeat_count))
        show_summary "$file_path" "$topic_name" "$use_ssl" "$total_messages" "$repeat_count"
    else
        print_error "Failed to send messages from file"
        exit 1
    fi
}

# Run main function
main "$@"

