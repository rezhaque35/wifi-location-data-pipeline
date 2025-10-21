#!/bin/bash

# wifi-database/wifi-scan-collection/wifi-scan-queue-consumer/scripts/send-file-wifi-scan-messages.sh
# Script to send WiFi scan data messages from file to Kafka topics
# Reads configuration from application.yml to ensure consistency with the service
# Uses direct Kafka CLI tools (no Docker dependency)

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

# Kafka CLI tools detection
KAFKA_CONSOLE_PRODUCER=""
KAFKA_CONSOLE_CONSUMER=""

# Function to show usage
show_usage() {
    echo "Usage: $0 --file FILE [--count N] [--interval SECONDS] [--topic TOPIC] [--help]"
    echo ""
    echo "Send WiFi scan data messages from file to Kafka"
    echo ""
    echo "🔧 Configuration Source:"
    echo "  This script reads Kafka configuration from src/main/resources/application.yml"
    echo "  to ensure messages are sent to the same broker/topic the service consumes from."
    echo "  SSL settings are automatically determined from application.yml configuration."
    echo ""
    echo "Parameters:"
    echo "  --file FILE             JSON file containing WiFi scan messages (required)"
    echo "  --count N               Number of times to send the message(s) from file (default: 1)"
    echo "  --interval SECONDS      Interval between messages in seconds (default: $DEFAULT_INTERVAL)"
    echo "  --topic TOPIC          Override topic from application.yml (not recommended)"
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
    echo "🌐 Remote Server Support:"
    echo "  Set KAFKA_BOOTSTRAP_SERVERS environment variable to override:"
    echo "  KAFKA_BOOTSTRAP_SERVERS=remote-server:9093 $0 --file message.json"
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

# Function to parse YAML using awk (handles nested structure)
parse_yaml_value() {
    local yaml_file="$1"
    local key_path="$2"
    local default_value="$3"
    
    # Use awk to parse nested YAML structure
    local value=$(awk -v key="$key_path" '
    BEGIN { 
        indent = -1
        found = 0
    }
    # Skip comments and empty lines
    /^[[:space:]]*#/ { next }
    /^[[:space:]]*$/ { next }
    # Match the key
    $0 ~ "^[[:space:]]*" key ":" {
        # Extract value after colon
        sub(/^[[:space:]]*[^:]+:[[:space:]]*/, "")
        # Remove trailing spaces and comments
        sub(/[[:space:]]*#.*$/, "")
        sub(/[[:space:]]+$/, "")
        if (length($0) > 0) {
            print $0
            found = 1
            exit
        }
    }
    END {
        if (!found && length(default_val) > 0) {
            print default_val
        }
    }
    ' default_val="$default_value" "$yaml_file")
    
    if [ -n "$value" ] && [ "$value" != "null" ]; then
        echo "$value"
    else
        echo "$default_value"
    fi
}

# Function to load configuration from application.yml
load_configuration() {
    local app_yml="$1"
    
    print_status "Loading configuration from: $app_yml"
    echo ""
    
    # Load Kafka configuration
    print_status "Reading Kafka configuration..."
    
    # Extract bootstrap-servers (skip commented lines)
    KAFKA_BOOTSTRAP_SERVERS=$(awk '
        /^kafka:$/ { in_kafka=1; next }
        in_kafka && /^[^ ]/ { in_kafka=0 }
        in_kafka && /^  bootstrap-servers:/ && !/^[[:space:]]*#/ {
            sub(/^[[:space:]]*bootstrap-servers:[[:space:]]*/, "")
            sub(/[[:space:]]*#.*$/, "")
            print
            exit
        }
    ' "$app_yml")
    
    # Extract topic name
    KAFKA_TOPIC=$(awk '
        /^kafka:$/ { in_kafka=1 }
        in_kafka && /^  topic:$/ { in_topic=1; next }
        in_topic && /^    name:/ {
            sub(/^[[:space:]]*name:[[:space:]]*/, "")
            sub(/[[:space:]]*#.*$/, "")
            print
            exit
        }
        in_topic && /^  [^ ]/ { in_topic=0 }
    ' "$app_yml")
    
    # Load SSL configuration from kafka.ssl section
    print_status "Reading SSL configuration..."
    KAFKA_SSL_ENABLED=$(awk '
        /^kafka:$/ { in_kafka=1 }
        in_kafka && /^  ssl:$/ { in_ssl=1; next }
        in_ssl && /^    enabled:/ {
            sub(/^[[:space:]]*enabled:[[:space:]]*/, "")
            sub(/[[:space:]]*#.*$/, "")
            print
            exit
        }
        in_ssl && /^  [^ ]/ { in_ssl=0 }
    ' "$app_yml")
    
    # Extract keystore location (skip commented lines)
    KAFKA_SSL_KEYSTORE_LOCATION=$(awk '
        /^kafka:$/ { in_kafka=1 }
        in_kafka && /^  ssl:$/ { in_ssl=1 }
        in_ssl && /^    keystore:$/ { in_keystore=1; next }
        in_keystore && /^      location:/ && !/^[[:space:]]*#/ {
            sub(/^[[:space:]]*location:[[:space:]]*/, "")
            sub(/[[:space:]]*#.*$/, "")
            print
            exit
        }
        in_keystore && /^    [^ ]/ { in_keystore=0 }
    ' "$app_yml")
    
    # Extract keystore password (skip commented lines)
    KAFKA_SSL_KEYSTORE_PASSWORD=$(awk '
        /^kafka:$/ { in_kafka=1 }
        in_kafka && /^  ssl:$/ { in_ssl=1 }
        in_ssl && /^    keystore:$/ { in_keystore=1; next }
        in_keystore && /^      password:/ && !/^[[:space:]]*#/ {
            sub(/^[[:space:]]*password:[[:space:]]*/, "")
            sub(/[[:space:]]*#.*$/, "")
            print
            exit
        }
        in_keystore && /^    [^ ]/ { in_keystore=0 }
    ' "$app_yml")
    
    # Extract truststore location
    KAFKA_SSL_TRUSTSTORE_LOCATION=$(awk '
        /^kafka:$/ { in_kafka=1 }
        in_kafka && /^  ssl:$/ { in_ssl=1 }
        in_ssl && /^    truststore:$/ { in_truststore=1; next }
        in_truststore && /^      location:/ && !/^[[:space:]]*#/ {
            sub(/^[[:space:]]*location:[[:space:]]*/, "")
            sub(/[[:space:]]*#.*$/, "")
            print
            exit
        }
        in_truststore && /^    [^ ]/ { in_truststore=0 }
    ' "$app_yml")
    
    # Extract truststore password
    KAFKA_SSL_TRUSTSTORE_PASSWORD=$(awk '
        /^kafka:$/ { in_kafka=1 }
        in_kafka && /^  ssl:$/ { in_ssl=1 }
        in_ssl && /^    truststore:$/ { in_truststore=1; next }
        in_truststore && /^      password:/ && !/^[[:space:]]*#/ {
            sub(/^[[:space:]]*password:[[:space:]]*/, "")
            sub(/[[:space:]]*#.*$/, "")
            print
            exit
        }
        in_truststore && /^    [^ ]/ { in_truststore=0 }
    ' "$app_yml")
    
    # Set defaults if values are empty
    KAFKA_BOOTSTRAP_SERVERS=${KAFKA_BOOTSTRAP_SERVERS:-"localhost:9092"}
    KAFKA_TOPIC=${KAFKA_TOPIC:-"wifi-scan-data"}
    KAFKA_SSL_ENABLED=${KAFKA_SSL_ENABLED:-"false"}
    
    # Resolve relative paths to absolute paths
    # If paths don't start with /, they are relative to project root
    if [ -n "$KAFKA_SSL_KEYSTORE_LOCATION" ] && [[ ! "$KAFKA_SSL_KEYSTORE_LOCATION" = /* ]]; then
        local script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
        local project_root="$(cd "$script_dir/../.." && pwd)"
        KAFKA_SSL_KEYSTORE_LOCATION="$project_root/$KAFKA_SSL_KEYSTORE_LOCATION"
    fi
    
    if [ -n "$KAFKA_SSL_TRUSTSTORE_LOCATION" ] && [[ ! "$KAFKA_SSL_TRUSTSTORE_LOCATION" = /* ]]; then
        local script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
        local project_root="$(cd "$script_dir/../.." && pwd)"
        KAFKA_SSL_TRUSTSTORE_LOCATION="$project_root/$KAFKA_SSL_TRUSTSTORE_LOCATION"
    fi
    
    # Determine ports based on SSL configuration
    if [ "$KAFKA_SSL_ENABLED" == "true" ]; then
        KAFKA_SSL_PORT="9093"
        KAFKA_PLAIN_PORT="9092"
    else
        KAFKA_SSL_PORT="9092"
        KAFKA_PLAIN_PORT="9092"
    fi
    
    # Allow environment variable override for remote servers
    if [ -n "$KAFKA_BOOTSTRAP_SERVERS_ENV" ]; then
        KAFKA_BOOTSTRAP_SERVERS="$KAFKA_BOOTSTRAP_SERVERS_ENV"
        print_status "Using remote Kafka server: $KAFKA_BOOTSTRAP_SERVERS"
    fi
    
    echo ""
    print_success "Configuration loaded successfully!"
    echo ""
    print_status "📋 Configuration Summary:"
    echo "  ┌─────────────────────────────────────────────────────────────┐"
    echo "  │ Kafka Configuration                                        │"
    echo "  ├─────────────────────────────────────────────────────────────┤"
    echo "  │ Bootstrap Servers: $KAFKA_BOOTSTRAP_SERVERS"
    printf "  │ %-20s: %-30s │\n" "Topic" "$KAFKA_TOPIC"
    printf "  │ %-20s: %-30s │\n" "SSL Enabled" "$KAFKA_SSL_ENABLED"
    printf "  │ %-20s: %-30s │\n" "SSL Port" "$KAFKA_SSL_PORT"
    printf "  │ %-20s: %-30s │\n" "Plain Port" "$KAFKA_PLAIN_PORT"
    echo "  └─────────────────────────────────────────────────────────────┘"
    
    if [ "$KAFKA_SSL_ENABLED" == "true" ]; then
        echo ""
        print_status "🔐 SSL Configuration Details:"
        echo "  ┌─────────────────────────────────────────────────────────────┐"
        printf "  │ %-20s: %-30s │\n" "Keystore Location" "$KAFKA_SSL_KEYSTORE_LOCATION"
        printf "  │ %-20s: %-30s │\n" "Keystore Password" "$KAFKA_SSL_KEYSTORE_PASSWORD"
        printf "  │ %-20s: %-30s │\n" "Truststore Location" "$KAFKA_SSL_TRUSTSTORE_LOCATION"
        printf "  │ %-20s: %-30s │\n" "Truststore Password" "$KAFKA_SSL_TRUSTSTORE_PASSWORD"
        echo "  └─────────────────────────────────────────────────────────────┘"
        
        # Validate SSL certificate files exist
        echo ""
        print_status "🔍 Validating SSL certificate files..."
        if [ -f "$KAFKA_SSL_KEYSTORE_LOCATION" ]; then
            print_success "✓ Keystore file exists: $KAFKA_SSL_KEYSTORE_LOCATION"
        else
            print_error "✗ Keystore file not found: $KAFKA_SSL_KEYSTORE_LOCATION"
        fi
        
        if [ -f "$KAFKA_SSL_TRUSTSTORE_LOCATION" ]; then
            print_success "✓ Truststore file exists: $KAFKA_SSL_TRUSTSTORE_LOCATION"
        else
            print_error "✗ Truststore file not found: $KAFKA_SSL_TRUSTSTORE_LOCATION"
        fi
    else
        echo ""
        print_status "🔓 Using plaintext connection (SSL disabled)"
    fi
    
    echo ""
}

# Function to check if Kafka CLI tools are installed
check_kafka_cli() {
    if command -v kafka-console-producer &> /dev/null; then
        KAFKA_CONSOLE_PRODUCER="kafka-console-producer"
        KAFKA_CONSOLE_CONSUMER="kafka-console-consumer"
        print_success "Kafka CLI tools found in PATH"
        return 0
    fi
    
    # Check common installation paths
    local common_paths=(
        "/usr/local/bin/kafka-console-producer"
        "/opt/kafka/bin/kafka-console-producer"
        "/usr/bin/kafka-console-producer"
        "$HOME/kafka/bin/kafka-console-producer"
    )
    
    for path in "${common_paths[@]}"; do
        if [ -f "$path" ]; then
            KAFKA_CONSOLE_PRODUCER="$path"
            KAFKA_CONSOLE_CONSUMER="${path%producer}consumer"
            print_success "Kafka CLI tools found at: $path"
            return 0
        fi
    done
    
    return 1
}

# Function to install Kafka CLI tools
install_kafka_cli() {
    print_status "Kafka CLI tools not found. Installing..."
    
    local os=$(uname -s)
    local arch=$(uname -m)
    
    case "$os" in
        "Darwin")
            if command -v brew &> /dev/null; then
                print_status "Installing Kafka via Homebrew..."
                brew install kafka
                if check_kafka_cli; then
                    print_success "Kafka CLI tools installed successfully via Homebrew"
                    return 0
                fi
            fi
            ;;
        "Linux")
            if command -v apt-get &> /dev/null; then
                print_status "Installing Kafka via apt..."
                sudo apt-get update
                sudo apt-get install -y kafka
                if check_kafka_cli; then
                    print_success "Kafka CLI tools installed successfully via apt"
                    return 0
                fi
            elif command -v yum &> /dev/null; then
                print_status "Installing Kafka via yum..."
                sudo yum install -y kafka
                if check_kafka_cli; then
                    print_success "Kafka CLI tools installed successfully via yum"
                    return 0
                fi
            fi
            ;;
    esac
    
    # Fallback: Download and extract Kafka
    print_status "Downloading Kafka binary distribution..."
    local kafka_version="2.8.1"
    local kafka_url="https://downloads.apache.org/kafka/${kafka_version}/kafka_2.13-${kafka_version}.tgz"
    local temp_dir="/tmp/kafka-install"
    
    mkdir -p "$temp_dir"
    cd "$temp_dir"
    
    if command -v curl &> /dev/null; then
        curl -L "$kafka_url" -o "kafka.tgz"
    elif command -v wget &> /dev/null; then
        wget "$kafka_url" -O "kafka.tgz"
    else
        print_error "Neither curl nor wget found. Please install Kafka manually."
        return 1
    fi
    
    tar -xzf kafka.tgz
    local kafka_dir=$(ls -d kafka_* | head -1)
    
    # Add to PATH for this session
    export PATH="$temp_dir/$kafka_dir/bin:$PATH"
    
    if check_kafka_cli; then
        print_success "Kafka CLI tools installed successfully from binary distribution"
        print_warning "Kafka is installed in $temp_dir/$kafka_dir"
        print_warning "Add this to your PATH: export PATH=\"$temp_dir/$kafka_dir/bin:\$PATH\""
        return 0
    else
        print_error "Failed to install Kafka CLI tools"
        return 1
    fi
}

# Function to ensure Kafka CLI tools are available
ensure_kafka_cli() {
    if check_kafka_cli; then
        return 0
    fi
    
    print_warning "Kafka CLI tools not found. Attempting to install..."
    
    if install_kafka_cli; then
        return 0
    else
        print_error "Failed to install Kafka CLI tools automatically."
        echo ""
        echo "Please install Kafka CLI tools manually:"
        echo "  macOS: brew install kafka"
        echo "  Ubuntu/Debian: sudo apt-get install kafka"
        echo "  CentOS/RHEL: sudo yum install kafka"
        echo "  Or download from: https://kafka.apache.org/downloads"
        echo ""
        exit 1
    fi
}

# Function to create SSL client properties file
create_ssl_client_properties() {
    if [ "$KAFKA_SSL_ENABLED" != "true" ]; then
        return 0
    fi
    
    # Redirect all output to stderr except the final file path
    {
        print_status "Creating SSL client properties..."
        
        local client_props_file="/tmp/kafka-ssl-client.properties"
        
        # Validate SSL certificate files exist
        if [ ! -f "$KAFKA_SSL_KEYSTORE_LOCATION" ]; then
            print_error "SSL keystore not found: $KAFKA_SSL_KEYSTORE_LOCATION"
            return 1
        fi
        
        if [ ! -f "$KAFKA_SSL_TRUSTSTORE_LOCATION" ]; then
            print_error "SSL truststore not found: $KAFKA_SSL_TRUSTSTORE_LOCATION"
            return 1
        fi
        
        cat > "$client_props_file" << EOF
security.protocol=SSL
ssl.truststore.location=$KAFKA_SSL_TRUSTSTORE_LOCATION
ssl.truststore.password=$KAFKA_SSL_TRUSTSTORE_PASSWORD
ssl.truststore.type=PKCS12
ssl.keystore.location=$KAFKA_SSL_KEYSTORE_LOCATION
ssl.keystore.password=$KAFKA_SSL_KEYSTORE_PASSWORD
ssl.keystore.type=PKCS12
ssl.key.password=$KAFKA_SSL_KEYSTORE_PASSWORD
EOF
        
        print_success "SSL client properties created"
    } >&2
    
    # Only the file path goes to stdout
    echo "/tmp/kafka-ssl-client.properties"
}

# Function to check prerequisites
check_prerequisites() {
    print_status "Checking prerequisites..."
    
    # Check if jq is available for JSON validation
    if ! command -v jq &> /dev/null; then
        print_error "jq is required for JSON validation. Please install it:"
        echo "  macOS: brew install jq"
        echo "  Ubuntu/Debian: sudo apt-get install jq"
        echo "  CentOS/RHEL: sudo yum install jq"
        exit 1
    fi
    
    # Ensure Kafka CLI tools are available
    ensure_kafka_cli
    
    print_success "Prerequisites verified!"
}

# Function to detect file format and parse messages
parse_message_file() {
    local file_path="$1"
    
    if [ ! -f "$file_path" ]; then
        print_error "File not found: $file_path"
        return 1
    fi
    
    # Try to detect file format by checking the file structure
    local first_char=$(head -1 "$file_path" | tr -d ' \t\n\r' | cut -c1)
    local line_count=$(wc -l < "$file_path" | tr -d ' ')
    
    # Check if it's a JSON array (first char is [)
    if [ "$first_char" == "[" ]; then
        echo "array"
    # Check if file has multiple lines starting with { (JSONL format)
    elif [ "$line_count" -gt 1 ] && [ "$first_char" == "{" ]; then
        # Verify second line also starts with { to confirm JSONL
        local second_char=$(sed -n '2p' "$file_path" | tr -d ' \t\n\r' | cut -c1)
        if [ "$second_char" == "{" ]; then
            echo "jsonl"
        else
            echo "single"
        fi
    # Single JSON object
    elif [ "$first_char" == "{" ]; then
        echo "single"
    else
        # Default to single JSON object
        echo "single"
    fi
}

# Function to count messages in file
count_messages_in_file() {
    local file_path="$1"
    local format_type="$2"
    
    case "$format_type" in
        "single")
            echo "1"
            ;;
        "array")
            jq length "$file_path"
            ;;
        "jsonl")
            wc -l < "$file_path" | tr -d ' '
            ;;
        *)
            echo "0"
            ;;
    esac
}

# Function to send messages
send_messages() {
    local file_path="$1"
    local topic_name="$2"
    local use_ssl="$3"
    local repeat_count="$4"
    local interval="$5"
    
    print_status "Parsing message file: $file_path"
    
    local format_type=$(parse_message_file "$file_path")
    local messages_in_file=$(count_messages_in_file "$file_path" "$format_type")
    local total_messages=$((messages_in_file * repeat_count))
    
    print_status "File format: $format_type"
    print_status "Messages in file: $messages_in_file"
    print_status "Total messages to send: $total_messages"
    
    local bootstrap_server="$KAFKA_BOOTSTRAP_SERVERS"
    local additional_options=""
    
    if [ "$use_ssl" == "true" ]; then
        local ssl_props_file=$(create_ssl_client_properties)
        if [ $? -ne 0 ]; then
            return 1
        fi
        additional_options="--producer.config $ssl_props_file"
        print_status "Using SSL connection"
    else
        print_status "Using plaintext connection"
    fi
    
    local message_number=0
    
    case "$format_type" in
        "single")
            local message=$(jq -c . "$file_path")
            
            for repeat in $(seq 1 "$repeat_count"); do
                message_number=$((message_number + 1))
                print_status "Sending message $message_number/$total_messages (iteration $repeat/$repeat_count)..."
                print_status "Message preview: $(echo "$message" | jq -r '.client // .requestId // "N/A"' 2>/dev/null)"
                
                echo "$message" | $KAFKA_CONSOLE_PRODUCER \
                    --bootstrap-server "$bootstrap_server" \
                    --topic "$topic_name" \
                    $additional_options
                
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
                    
                    echo "$message" | $KAFKA_CONSOLE_PRODUCER \
                        --bootstrap-server "$bootstrap_server" \
                        --topic "$topic_name" \
                        $additional_options
                    
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
                    
                    echo "$message" | $KAFKA_CONSOLE_PRODUCER \
                        --bootstrap-server "$bootstrap_server" \
                        --topic "$topic_name" \
                        $additional_options
                    
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
    
    print_success "All messages sent successfully!"
}

# Function to verify message delivery (optional)
verify_message_delivery() {
    local topic_name="$1"
    local use_ssl="$2"
    
    print_status "Verifying message delivery..."
    
    local bootstrap_server="$KAFKA_BOOTSTRAP_SERVERS"
    local additional_options=""
    
    if [ "$use_ssl" == "true" ]; then
        local ssl_props_file=$(create_ssl_client_properties)
        if [ $? -ne 0 ]; then
            return 1
        fi
        additional_options="--consumer.config $ssl_props_file"
    fi
    
    # Consume one message to verify delivery
    timeout 10s $KAFKA_CONSOLE_CONSUMER \
        --bootstrap-server "$bootstrap_server" \
        --topic "$topic_name" \
        --from-beginning \
        --max-messages 1 \
        $additional_options &>/dev/null
    
    if [ $? -eq 0 ]; then
        print_success "Message delivery verified!"
    else
        print_warning "Could not verify message delivery (timeout or no messages)"
    fi
}

# Main execution
main() {
    echo "======================================"
    echo "WiFi Scan Message Sender"
    echo "======================================"
    
    # Parse command line arguments
    local file_path=""
    local message_count=1
    local interval=$DEFAULT_INTERVAL
    local topic_override=""
    
    while [[ $# -gt 0 ]]; do
        case $1 in
            --file)
                file_path="$2"
                shift 2
                ;;
            --count)
                message_count="$2"
                shift 2
                ;;
            --interval)
                interval="$2"
                shift 2
                ;;
            --topic)
                topic_override="$2"
                shift 2
                ;;
            --help)
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
    
    # Validate required parameters
    if [ -z "$file_path" ]; then
        print_error "File path is required"
        show_usage
        exit 1
    fi
    
    # Load configuration
    local app_yml=$(find_application_yml)
    load_configuration "$app_yml"
    
    # Override topic if specified
    if [ -n "$topic_override" ]; then
        KAFKA_TOPIC="$topic_override"
        print_warning "Topic overridden to: $KAFKA_TOPIC"
    fi
    
    # Use SSL configuration from application.yml
    local use_ssl="$KAFKA_SSL_ENABLED"
    
    # Check prerequisites
    check_prerequisites
    
    # Send messages
    send_messages "$file_path" "$KAFKA_TOPIC" "$use_ssl" "$message_count" "$interval"
    
    # Optional verification
    verify_message_delivery "$KAFKA_TOPIC" "$use_ssl"
    
    echo "======================================"
    print_success "Script completed successfully!"
    echo "======================================"
}

# Run main function
main "$@"