#!/bin/bash

# wifi-database/wifi-scan-collection/wifi-scan-queue-consumer/scripts/validate-service-health.sh
# Script to validate service health by sending WiFi scan messages and monitoring health indicators

set -e  # Exit on any error

# Define colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
MAGENTA='\033[0;35m'
NC='\033[0m' # No Color

# Function to print colored output
print_header() {
    echo -e "${MAGENTA}=====================================>${NC}"
    echo -e "${MAGENTA}$1${NC}"
    echo -e "${MAGENTA}=====================================>${NC}"
}

print_step() {
    echo -e "${BLUE}[STEP]${NC} $1"
}

print_info() {
    echo -e "${CYAN}[INFO]${NC} $1"
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
DEFAULT_COUNT=10
DEFAULT_INTERVAL=2
DEFAULT_TOPIC="wifi-scan-data"
DEFAULT_SERVICE_URL="http://localhost:8080"
DEFAULT_VALIDATION_TIMEOUT=60
DEFAULT_HEALTH_CHECK_INTERVAL=5

# Function to show usage
show_usage() {
    echo "Usage: $0 [options]"
    echo ""
    echo "Validate service health by sending WiFi scan messages and monitoring health indicators"
    echo ""
    echo "Options:"
    echo "  --count N                    Number of messages to send (default: $DEFAULT_COUNT)"
    echo "  --interval SECONDS           Interval between messages (default: $DEFAULT_INTERVAL)"
    echo "  --topic TOPIC               Target Kafka topic (default: $DEFAULT_TOPIC)"
    echo "  --service-url URL           Service base URL (default: $DEFAULT_SERVICE_URL)"
    echo "  --timeout SECONDS           Validation timeout (default: $DEFAULT_VALIDATION_TIMEOUT)"
    echo "  --health-interval SECONDS   Health check interval (default: $DEFAULT_HEALTH_CHECK_INTERVAL)"
    echo "  --ssl                       Use SSL for Kafka (default: false)"
    echo "  --verbose                   Verbose output"
    echo "  --help                      Show this help message"
    echo ""
    echo "Examples:"
    echo "  $0                                                    # Basic validation with defaults"
    echo "  $0 --count 20 --interval 1                          # Send 20 messages every 1 second"
    echo "  $0 --count 5 --timeout 30 --verbose                 # Quick test with verbose output"
    echo "  $0 --ssl --service-url http://localhost:8081        # SSL testing with custom service URL"
    echo ""
}

# Global variables
SEND_SCRIPT_PATH=""

# Function to check prerequisites
check_prerequisites() {
    print_step "Checking prerequisites..."
    
    # Check if Kafka is running
    if ! docker ps | grep -q "kafka"; then
        print_error "Kafka container is not running. Please run ./start-local-kafka.sh first."
        exit 1
    fi
    
    # Check if send-wifi-scan-messages.sh exists and is executable
    if [ -x "./send-wifi-scan-messages.sh" ]; then
        SEND_SCRIPT_PATH="./send-wifi-scan-messages.sh"
    elif [ -x "./scripts/send-wifi-scan-messages.sh" ]; then
        SEND_SCRIPT_PATH="./scripts/send-wifi-scan-messages.sh"
    else
        print_error "send-wifi-scan-messages.sh not found or not executable."
        print_info "Make sure the script exists and run: chmod +x scripts/send-wifi-scan-messages.sh"
        exit 1
    fi
    
    # Check if curl is available
    if ! command -v curl &> /dev/null; then
        print_error "curl is required for health checks. Please install it."
        exit 1
    fi
    
    # Check if jq is available
    if ! command -v jq &> /dev/null; then
        print_error "jq is required for JSON processing. Please install it: brew install jq"
        exit 1
    fi
    
    print_success "Prerequisites verified!"
}

# Function to check service availability
check_service_availability() {
    local service_url="$1"
    local max_attempts=10
    local attempt=1
    
    print_step "Checking service availability at $service_url..."
    
    while [ $attempt -le $max_attempts ]; do
        if curl -s -f "$service_url/frisco-location-wifi-scan-vmb-consumer/health" > /dev/null 2>&1; then
            print_success "Service is available!"
            return 0
        fi
        
        print_info "Attempt $attempt/$max_attempts: Service not ready, waiting 3 seconds..."
        sleep 3
        ((attempt++))
    done
    
    print_error "Service is not available after $max_attempts attempts."
    print_info "Please ensure your Spring Boot application is running."
    print_info "You can start it with: cd ../ && mvn spring-boot:run"
    exit 1
}

# Function to get actual message count from Kafka broker
get_kafka_topic_message_count() {
    local topic="$1"
    local use_ssl="$2"
    local verbose="$3"
    
    local bootstrap_server
    local additional_options=""
    
    if [ "$use_ssl" == "true" ]; then
        bootstrap_server="localhost:9093"
        # Create temporary SSL config for kafka commands
        local ssl_config="/tmp/kafka-ssl-client.properties"
        cat > "$ssl_config" << EOF
security.protocol=SSL
ssl.truststore.location=/etc/kafka/secrets/kafka.truststore.jks
ssl.truststore.password=kafka123
ssl.keystore.location=/etc/kafka/secrets/kafka.keystore.jks
ssl.keystore.password=kafka123
ssl.key.password=kafka123
EOF
        docker cp "$ssl_config" kafka:/tmp/kafka-ssl-client.properties >/dev/null 2>&1
        additional_options="--command-config /tmp/kafka-ssl-client.properties"
        rm -f "$ssl_config"
    else
        bootstrap_server="localhost:9092"
    fi
    
    # Get topic partitions and their end offsets
    local total_messages=0
    local partition_info
    
    if [ "$verbose" == "true" ]; then
        print_info "Getting message count for topic '$topic' from Kafka broker..." >&2
    fi
    
    # Use kafka-run-class to get topic end offsets
    partition_info=$(docker exec kafka kafka-run-class kafka.tools.GetOffsetShell \
        --broker-list "$bootstrap_server" \
        --topic "$topic" \
        --time -1 \
        $additional_options 2>/dev/null || echo "")
    
    if [ -z "$partition_info" ]; then
        if [ "$verbose" == "true" ]; then
            print_warning "Could not get partition info for topic '$topic', returning 0" >&2
        fi
        echo "0"
        return
    fi
    
    # Parse partition offsets and sum them up
    while IFS=':' read -r topic_partition partition offset; do
        if [ -n "$offset" ] && [ "$offset" != "0" ]; then
            total_messages=$((total_messages + offset))
        fi
    done <<< "$partition_info"
    
    if [ "$verbose" == "true" ]; then
        print_info "Total messages in topic '$topic': $total_messages" >&2
    fi
    
    echo "$total_messages"
}

# Function to get current message count from service metrics
get_service_message_count() {
    local service_url="$1"
    local verbose="$2"
    
    local response
    response=$(curl -s "$service_url/frisco-location-wifi-scan-vmb-consumer/api/metrics/kafka" 2>/dev/null || echo '{"totalMessagesProcessed":0}')
    
    local count
    count=$(echo "$response" | jq -r '.totalMessagesProcessed // 0' 2>/dev/null || echo "0")
    
    if [ "$verbose" == "true" ]; then
        print_info "Service processed message count: $count" >&2
    fi
    
    echo "$count"
}

# Function to get health status
get_health_status() {
    local service_url="$1"
    local endpoint="$2"
    local verbose="$3"
    
    local response
    response=$(curl -s "$service_url/frisco-location-wifi-scan-vmb-consumer/health$endpoint" 2>/dev/null || echo '{"status":"DOWN"}')
    
    local status
    status=$(echo "$response" | jq -r '.status // "DOWN"' 2>/dev/null || echo "DOWN")
    
    if [ "$verbose" == "true" ]; then
        print_info "Health status ($endpoint): $status" >&2
        if [ "$status" != "UP" ]; then
            echo "$response" | jq '.' 2>/dev/null || echo "$response"
        fi
    fi
    
    echo "$status"
}

# Function to get detailed metrics
get_detailed_metrics() {
    local service_url="$1"
    local verbose="$2"
    
    if [ "$verbose" == "true" ]; then
        print_info "Fetching detailed metrics..."
        
        local response
        response=$(curl -s "$service_url/frisco-location-wifi-scan-vmb-consumer/api/metrics/kafka" 2>/dev/null || echo '{}')
        
        echo "$response" | jq '.' 2>/dev/null || echo "Failed to parse metrics response"
    fi
}

# Function to send WiFi scan messages
send_wifi_messages() {
    local count="$1"
    local interval="$2"
    local topic="$3"
    local use_ssl="$4"
    local verbose="$5"
    
    print_step "Sending $count WiFi scan messages with ${interval}s interval..."
    
    local ssl_flag=""
    if [ "$use_ssl" == "true" ]; then
        ssl_flag="--ssl"
    fi
    
    local verbose_flag=""
    if [ "$verbose" == "true" ]; then
        verbose_flag="--verbose"
    fi
    
    # Send messages in background to continue with monitoring
    if [ "$verbose" == "true" ]; then
        $SEND_SCRIPT_PATH --count "$count" --interval "$interval" --topic "$topic" $ssl_flag &
    else
        $SEND_SCRIPT_PATH --count "$count" --interval "$interval" --topic "$topic" $ssl_flag > /dev/null 2>&1 &
    fi
    
    local send_pid=$!
    print_info "WiFi message sender started (PID: $send_pid)"
    echo "$send_pid"
}

# Function to monitor service health
monitor_service_health() {
    local service_url="$1"
    local initial_count="$2"
    local expected_count="$3"
    local timeout="$4"
    local check_interval="$5"
    local verbose="$6"
    local results_file="$7"
    
    print_step "Monitoring service health for $timeout seconds..."
    
    local start_time
    start_time=$(date +%s)
    local end_time
    end_time=$((start_time + timeout))
    
    local last_count="$initial_count"
    local health_checks=0
    local successful_checks=0
    
    while [ $(date +%s) -lt $end_time ]; do
        ((health_checks++))
        
        # Check overall health
        local overall_health
        overall_health=$(get_health_status "$service_url" "" "$verbose")
        
        # Check readiness
        local readiness_health
        readiness_health=$(get_health_status "$service_url" "/readiness" "$verbose")
        
        # Check liveness
        local liveness_health
        liveness_health=$(get_health_status "$service_url" "/liveness" "$verbose")
        
        # Get current message count
        local current_count
        current_count=$(get_service_message_count "$service_url" "$verbose")
        
        # Check if message count increased
        local count_increased="false"
        if [ "$current_count" -gt "$last_count" ]; then
            count_increased="true"
            last_count="$current_count"
        fi
        
        # Print status
        if [ "$verbose" == "true" ]; then
            print_info "Health Check #$health_checks:"
            print_info "  Overall: $overall_health | Readiness: $readiness_health | Liveness: $liveness_health"
            print_info "  Messages: $current_count/$expected_count | Count increased: $count_increased"
        else
            printf "\r[INFO] Check #%d: Health=%s|%s|%s Messages=%d/%d " \
                "$health_checks" "$overall_health" "$readiness_health" "$liveness_health" \
                "$current_count" "$expected_count"
        fi
        
        # Count successful health checks
        if [ "$overall_health" == "UP" ] && [ "$readiness_health" == "UP" ] && [ "$liveness_health" == "UP" ]; then
            ((successful_checks++))
        fi
        
        # Check if we've reached expected message count
        if [ "$current_count" -ge "$expected_count" ]; then
            print_success "\nTarget message count reached: $current_count/$expected_count"
            break
        fi
        
        sleep "$check_interval"
    done
    
    if [ "$verbose" != "true" ]; then
        echo ""  # New line after progress indicator
    fi
    
    # Final status
    local final_count
    final_count=$(get_service_message_count "$service_url" false)
    
    echo "Monitoring Summary:"
    echo "  Total health checks: $health_checks"
    echo "  Successful health checks: $successful_checks"
    echo "  Initial message count: $initial_count"
    echo "  Final message count: $final_count"
    echo "  Messages processed: $((final_count - initial_count))"
    echo "  Expected messages: $expected_count"
    
    # Store results for caller
    echo "total_checks=$health_checks" > "$results_file"
    echo "successful_checks=$successful_checks" >> "$results_file"
    echo "final_count=$final_count" >> "$results_file"
    echo "messages_processed=$((final_count - initial_count))" >> "$results_file"
    
    # Return success if we processed expected messages and had successful health checks
    if [ "$final_count" -ge "$expected_count" ] && [ "$successful_checks" -gt 0 ]; then
        return 0
    else
        return 1
    fi
}

# Function to generate validation report
generate_report() {
    local service_url="$1"
    local initial_service_count="$2"
    local final_service_count="$3"
    local expected_count="$4"
    local health_checks="$5"
    local successful_checks="$6"
    local initial_kafka_count="$7"
    local final_kafka_count="$8"
    
    print_header "VALIDATION REPORT"
    
    echo "Service URL: $service_url"
    echo ""
    echo "Kafka Topic Messages:"
    echo "  Initial Kafka count: ${initial_kafka_count:-N/A}"
    echo "  Final Kafka count: ${final_kafka_count:-N/A}"
    echo "  Messages added to topic: $((final_kafka_count - initial_kafka_count))"
    echo ""
    echo "Service Message Processing:"
    echo "  Initial service count: $initial_service_count"
    echo "  Final service count: $final_service_count"
    echo "  Messages processed by service: $((final_service_count - initial_service_count))"
    echo "  Expected new messages: $expected_count"
    
    local message_success="FAIL"
    if [ "$final_service_count" -ge "$((initial_service_count + expected_count))" ]; then
        message_success="PASS"
    fi
    
    echo "  Status: $message_success"
    
    echo ""
    echo "Health Monitoring:"
    echo "  Total checks: $health_checks"
    echo "  Successful checks: $successful_checks"
    
    local health_success="FAIL"
    if [ "$successful_checks" -gt 0 ]; then
        health_success="PASS"
    fi
    
    echo "  Status: $health_success"
    
    echo ""
    echo "Overall Validation:"
    if [ "$message_success" == "PASS" ] && [ "$health_success" == "PASS" ]; then
        print_success "✅ VALIDATION PASSED"
        echo "The service is correctly consuming messages and health indicators are working."
        return 0
    else
        print_error "❌ VALIDATION FAILED"
        if [ "$message_success" == "FAIL" ]; then
            echo "- Message consumption issue detected"
        fi
        if [ "$health_success" == "FAIL" ]; then
            echo "- Health indicator issue detected"
        fi
        return 1
    fi
}

# Function to cleanup background processes
cleanup() {
    if [ ! -z "$SEND_PID" ]; then
        if kill -0 "$SEND_PID" 2>/dev/null; then
            print_info "Stopping WiFi message sender (PID: $SEND_PID)..."
            kill "$SEND_PID" 2>/dev/null || true
        fi
    fi
}

# Set up signal handlers
trap cleanup EXIT INT TERM

# Main execution
main() {
    # Default values
    local count="$DEFAULT_COUNT"
    local interval="$DEFAULT_INTERVAL"
    local topic="$DEFAULT_TOPIC"
    local service_url="$DEFAULT_SERVICE_URL"
    local timeout="$DEFAULT_VALIDATION_TIMEOUT"
    local health_interval="$DEFAULT_HEALTH_CHECK_INTERVAL"
    local use_ssl="false"
    local verbose="false"
    
    # Parse command line arguments
    while [[ $# -gt 0 ]]; do
        case $1 in
            --count)
                count="$2"
                if ! [[ "$count" =~ ^[0-9]+$ ]] || [ "$count" -lt 1 ]; then
                    print_error "Invalid count: $count. Must be a positive integer."
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
                topic="$2"
                if [ -z "$topic" ]; then
                    print_error "Topic name cannot be empty."
                    exit 1
                fi
                shift 2
                ;;
            --service-url)
                service_url="$2"
                if [ -z "$service_url" ]; then
                    print_error "Service URL cannot be empty."
                    exit 1
                fi
                shift 2
                ;;
            --timeout)
                timeout="$2"
                if ! [[ "$timeout" =~ ^[0-9]+$ ]] || [ "$timeout" -lt 1 ]; then
                    print_error "Invalid timeout: $timeout. Must be a positive integer."
                    exit 1
                fi
                shift 2
                ;;
            --health-interval)
                health_interval="$2"
                if ! [[ "$health_interval" =~ ^[0-9]+$ ]] || [ "$health_interval" -lt 1 ]; then
                    print_error "Invalid health interval: $health_interval. Must be a positive integer."
                    exit 1
                fi
                shift 2
                ;;
            --ssl)
                use_ssl="true"
                shift
                ;;
            --verbose)
                verbose="true"
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
    
    print_header "SERVICE HEALTH VALIDATION"
    
    echo "Configuration:"
    echo "  Message count: $count"
    echo "  Message interval: ${interval}s"
    echo "  Topic: $topic"
    echo "  Service URL: $service_url"
    echo "  Validation timeout: ${timeout}s"
    echo "  Health check interval: ${health_interval}s"
    echo "  SSL: $use_ssl"
    echo "  Verbose: $verbose"
    echo ""
    
    # Check prerequisites
    check_prerequisites
    
    # Check service availability
    check_service_availability "$service_url"
    
    # Get initial message count from Kafka broker (total messages in topic)
    local initial_kafka_count
    initial_kafka_count=$(get_kafka_topic_message_count "$topic" "$use_ssl" "$verbose")
    print_info "Initial Kafka topic message count: $initial_kafka_count"
    
    # Get initial service processed count (from service metrics)
    local initial_service_count
    initial_service_count=$(get_service_message_count "$service_url" "$verbose")
    print_info "Initial service processed count: $initial_service_count"
    
    # Get initial detailed metrics if verbose
    get_detailed_metrics "$service_url" "$verbose"
    
    # Send WiFi messages
    SEND_PID=$(send_wifi_messages "$count" "$interval" "$topic" "$use_ssl" "$verbose")
    
    # Monitor service health - we expect the service to process the new messages
    local expected_service_count=$((initial_service_count + count))
    
    # Create temporary files to store health check results
    local temp_results="/tmp/health_results_$$"
    echo "total_checks=0" > "$temp_results"
    echo "successful_checks=0" >> "$temp_results"
    
    if monitor_service_health "$service_url" "$initial_service_count" "$expected_service_count" "$timeout" "$health_interval" "$verbose" "$temp_results"; then
        local final_service_count
        final_service_count=$(get_service_message_count "$service_url" false)
        
        local final_kafka_count
        final_kafka_count=$(get_kafka_topic_message_count "$topic" "$use_ssl" false)
        
        # Wait a bit for sender to complete
        sleep 2
        
        print_info "=== FINAL VALIDATION SUMMARY ==="
        print_info "Kafka Topic Messages:"
        print_info "  Initial: $initial_kafka_count"
        print_info "  Final: $final_kafka_count"
        print_info "  Added: $((final_kafka_count - initial_kafka_count))"
        print_info "Service Processed Messages:"
        print_info "  Initial: $initial_service_count"
        print_info "  Final: $final_service_count"
        print_info "  Processed: $((final_service_count - initial_service_count))"
        print_info "Expected new messages: $count"
        
        # Read health check results
        source "$temp_results" 2>/dev/null || { total_checks=0; successful_checks=0; }
        
        # Generate final report with actual health check counts
        generate_report "$service_url" "$initial_service_count" "$final_service_count" "$count" "$total_checks" "$successful_checks" "$initial_kafka_count" "$final_kafka_count"
        exit_code=$?
        
        if [ "$verbose" == "true" ]; then
            get_detailed_metrics "$service_url" "$verbose"
        fi
        
        # Cleanup temp file
        rm -f "$temp_results"
        
        exit $exit_code
    else
        print_error "Health monitoring detected issues"
        
        local final_service_count
        final_service_count=$(get_service_message_count "$service_url" false)
        local final_kafka_count
        final_kafka_count=$(get_kafka_topic_message_count "$topic" "$use_ssl" false)
        
        print_info "=== FINAL VALIDATION SUMMARY (WITH ISSUES) ==="
        print_info "Kafka Topic Messages:"
        print_info "  Initial: $initial_kafka_count"
        print_info "  Final: $final_kafka_count"
        print_info "  Added: $((final_kafka_count - initial_kafka_count))"
        print_info "Service Processed Messages:"
        print_info "  Initial: $initial_service_count"
        print_info "  Final: $final_service_count"
        print_info "  Processed: $((final_service_count - initial_service_count))"
        
        # Read health check results
        source "$temp_results" 2>/dev/null || { total_checks=0; successful_checks=0; }
        
        generate_report "$service_url" "$initial_service_count" "$final_service_count" "$count" "$total_checks" "$successful_checks" "$initial_kafka_count" "$final_kafka_count"
        
        # Cleanup temp file
        rm -f "$temp_results"
        
        exit 1
    fi
}

# Run main function
main "$@" 