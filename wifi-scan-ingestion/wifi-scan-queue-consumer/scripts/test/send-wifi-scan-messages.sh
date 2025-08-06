#!/bin/bash

# wifi-database/wifi-scan-collection/wifi-scan-queue-consumer/scripts/send-wifi-scan-messages.sh
# Script to send randomly generated WiFi scan data messages to Kafka topics

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
DEFAULT_TOPIC="wifi-scan-data"
DEFAULT_COUNT=10
DEFAULT_INTERVAL=2
KAFKA_SSL_PORT=9093
KAFKA_PLAIN_PORT=9092

# WiFi frequency bands
FREQ_2_4GHz=(2412 2417 2422 2427 2432 2437 2442 2447 2452 2457 2462 2467 2472)
FREQ_5GHz=(5180 5200 5220 5240 5260 5280 5300 5320 5500 5520 5540 5560 5580 5600 5620 5640 5660 5680 5700 5720 5745 5765 5785 5805 5825)

# Sample SSIDs for realistic data
SSIDS=("HomeNetwork" "OfficeWiFi" "CoffeeShop" "Library_Guest" "Building_A" "SecureAP" "PublicWiFi" "GuestNetwork" "Conference_Room" "Lab_Network" "TestAP" "Mobile_Hotspot")

# Sample vendors
VENDORS=("Cisco" "TP-Link" "Netgear" "Aruba" "Ubiquiti" "Linksys" "D-Link" "ASUS")

# Function to show usage
show_usage() {
    echo "Usage: $0 [--count N] [--interval SECONDS] [--topic TOPIC] [--ssl] [--help]"
    echo ""
    echo "Send randomly generated WiFi scan data messages to Kafka"
    echo ""
    echo "Parameters:"
    echo "  --count N               Number of messages to send (default: $DEFAULT_COUNT)"
    echo "  --interval SECONDS      Interval between messages in seconds (default: $DEFAULT_INTERVAL)"
    echo "  --topic TOPIC          Target topic (default: '$DEFAULT_TOPIC')"
    echo "  --ssl                  Use SSL connection (default: plaintext)"
    echo "  --help                 Show this help message"
    echo ""
    echo "Examples:"
    echo "  $0                                          # Send 10 messages every 2 seconds"
    echo "  $0 --count 5                               # Send 5 messages every 2 seconds"
    echo "  $0 --count 20 --interval 1                 # Send 20 messages every 1 second"
    echo "  $0 --count 5 --topic my-topic --ssl        # Send to custom topic using SSL"
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
    
    # Check if jq is available for JSON generation
    if ! command -v jq &> /dev/null; then
        print_error "jq is required for JSON generation. Please install it: brew install jq"
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

# Function to generate random MAC address
generate_mac_address() {
    printf "%02x:%02x:%02x:%02x:%02x:%02x" $((RANDOM%256)) $((RANDOM%256)) $((RANDOM%256)) $((RANDOM%256)) $((RANDOM%256)) $((RANDOM%256))
}

# Function to generate random WiFi scan result
generate_wifi_scan_result() {
    local mac_address=$(generate_mac_address)
    local signal_strength=$(echo "scale=1; -30 - $RANDOM % 70" | bc)  # Range: -30 to -100 dBm
    
    # Randomly choose frequency band
    if [ $((RANDOM % 2)) -eq 0 ]; then
        # 2.4 GHz band
        local frequency=${FREQ_2_4GHz[$RANDOM % ${#FREQ_2_4GHz[@]}]}
    else
        # 5 GHz band
        local frequency=${FREQ_5GHz[$RANDOM % ${#FREQ_5GHz[@]}]}
    fi
    
    local ssid=${SSIDS[$RANDOM % ${#SSIDS[@]}]}
    local link_speed=$((54 + RANDOM % 1000))  # Range: 54-1054 Mbps
    local channel_width_options=(20 40 80 160)
    local channel_width=${channel_width_options[$RANDOM % ${#channel_width_options[@]}]}
    
    cat << EOF
{
    "macAddress": "$mac_address",
    "signalStrength": $signal_strength,
    "frequency": $frequency,
    "ssid": "$ssid",
    "linkSpeed": $link_speed,
    "channelWidth": $channel_width
}
EOF
}

# Function to generate WiFi positioning request message
generate_wifi_positioning_request() {
    local scan_count=$((1 + RANDOM % 5))  # 1-5 scan results per message
    local client="wifi-scan-generator"
    local request_id="req-$(date +%s)-$RANDOM"
    local application="wifi-scan-test-suite"
    local calculation_detail=$([ $((RANDOM % 2)) -eq 0 ] && echo "true" || echo "false")
    
    # Generate scan results array
    local scan_results=""
    for i in $(seq 1 $scan_count); do
        local scan_result=$(generate_wifi_scan_result)
        if [ $i -eq 1 ]; then
            scan_results="$scan_result"
        else
            scan_results="$scan_results,$scan_result"
        fi
    done
    
    cat << EOF
{
    "wifiScanResults": [$scan_results],
    "client": "$client",
    "requestId": "$request_id",
    "application": "$application",
    "calculationDetail": $calculation_detail
}
EOF
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

# Function to send WiFi scan messages
send_wifi_scan_messages() {
    local topic_name="$1"
    local use_ssl="$2"
    local message_count="$3"
    local interval="$4"
    
    print_status "Sending $message_count WiFi scan messages to topic '$topic_name' with ${interval}s interval..."
    
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
        print_status "Generating WiFi scan message $i/$message_count..."
        
        # Generate WiFi positioning request
        local wifi_message=$(generate_wifi_positioning_request)
        local compact_message=$(echo "$wifi_message" | jq -c .)
        
        print_status "Sending message $i/$message_count"
        print_status "Sample data: $(echo "$compact_message" | jq -r '.wifiScanResults | length') scan results, Client: $(echo "$compact_message" | jq -r '.client'), RequestID: $(echo "$compact_message" | jq -r '.requestId')"
        
        # Send the message
        if [ "$use_ssl" == "true" ]; then
            echo "$compact_message" | docker exec -i kafka kafka-console-producer \
                --bootstrap-server "$bootstrap_server" \
                --topic "$topic_name" \
                $additional_options
        else
            echo "$compact_message" | docker exec -i kafka kafka-console-producer \
                --bootstrap-server "$bootstrap_server" \
                --topic "$topic_name"
        fi
        
        if [ $? -eq 0 ]; then
            print_success "Message $i sent successfully!"
        else
            print_error "Failed to send message $i"
            return 1
        fi
        
        # Wait for interval if not the last message
        if [ "$i" -lt "$message_count" ]; then
            print_status "Waiting ${interval} seconds before next message..."
            sleep "$interval"
        fi
    done
}

# Function to show summary and next steps
show_summary() {
    local topic_name="$1"
    local use_ssl="$2"
    local message_count="$3"
    local interval="$4"
    
    echo ""
    print_success "WiFi scan message generation completed!"
    echo "Summary:"
    echo "- Topic: $topic_name"
    echo "- Messages sent: $message_count"
    echo "- Interval: ${interval}s"
    echo "- SSL: $use_ssl"
    echo ""
    echo "Next steps:"
    if [ "$use_ssl" == "true" ]; then
        echo "1. Consume messages: ./consume-test-messages.sh $topic_name --ssl"
    else
        echo "1. Consume messages: ./consume-test-messages.sh $topic_name"
    fi
    echo "2. Start your Spring Boot application to process the messages"
    echo "3. Monitor application logs for message processing"
    echo ""
}

# Main execution
main() {
    # Default values
    local topic_name="$DEFAULT_TOPIC"
    local use_ssl="false"
    local message_count="$DEFAULT_COUNT"
    local interval="$DEFAULT_INTERVAL"
    
    # Parse command line arguments
    while [[ $# -gt 0 ]]; do
        case $1 in
            --count)
                message_count="$2"
                if ! [[ "$message_count" =~ ^[0-9]+$ ]] || [ "$message_count" -lt 1 ]; then
                    print_error "Invalid count: $message_count. Must be a positive integer."
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
                if [ -z "$topic_name" ]; then
                    print_error "Topic name cannot be empty."
                    exit 1
                fi
                shift 2
                ;;
            --ssl)
                use_ssl="true"
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
    
    echo "=========================================="
    echo "📡 WiFi Scan Data Message Generator"
    echo "=========================================="
    
    echo "Configuration:"
    echo "- Topic: $topic_name"
    echo "- Message count: $message_count"
    echo "- Interval: ${interval}s"
    echo "- SSL: $use_ssl"
    echo ""
    
    check_prerequisites
    
    # Create SSL client properties if needed
    if [ "$use_ssl" == "true" ]; then
        create_ssl_client_properties
    fi
    
    verify_topic_exists "$topic_name" "$use_ssl"
    send_wifi_scan_messages "$topic_name" "$use_ssl" "$message_count" "$interval"
    show_summary "$topic_name" "$use_ssl" "$message_count" "$interval"
}

# Run main function
main "$@" 