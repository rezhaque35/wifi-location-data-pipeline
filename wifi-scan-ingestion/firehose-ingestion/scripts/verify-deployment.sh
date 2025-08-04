#!/bin/bash
# wifi-scan-ingestion/firehose-ingestion/scripts/verify-deployment.sh
# Verify that all infrastructure resources are deployed and functioning

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Script directory and project paths
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
TERRAFORM_DIR="$PROJECT_ROOT/terraform"

# LocalStack endpoint
LOCALSTACK_ENDPOINT="http://localhost:4566"

# Function to print colored output
print_status() {
    local status=$1
    local message=$2
    case $status in
        "INFO")  echo -e "${BLUE}[INFO]${NC} $message" ;;
        "SUCCESS") echo -e "${GREEN}[SUCCESS]${NC} $message" ;;
        "WARNING") echo -e "${YELLOW}[WARNING]${NC} $message" ;;
        "ERROR") echo -e "${RED}[ERROR]${NC} $message" ;;
    esac
}

# Function to set AWS credentials for LocalStack
set_aws_credentials() {
    export AWS_ACCESS_KEY_ID=test
    export AWS_SECRET_ACCESS_KEY=test
    export AWS_DEFAULT_REGION=us-east-1
}

# Function to check if LocalStack is running
check_localstack() {
    print_status "INFO" "Checking LocalStack health..."
    
    if ! curl -s ${LOCALSTACK_ENDPOINT}/_localstack/health >/dev/null 2>&1; then
        print_status "ERROR" "LocalStack is not running"
        return 1
    fi
    
    local health_response
    health_response=$(curl -s ${LOCALSTACK_ENDPOINT}/_localstack/health)
    
    print_status "SUCCESS" "LocalStack is running"
    print_status "INFO" "LocalStack version: $(echo "$health_response" | jq -r '.version' 2>/dev/null || echo 'unknown')"
    return 0
}

# Function to verify S3 bucket
verify_s3_bucket() {
    print_status "INFO" "Verifying S3 bucket..."
    
    local bucket_name="ingested-wifiscan-data"
    
    # Check if bucket exists
    if aws --endpoint-url=${LOCALSTACK_ENDPOINT} s3 ls "s3://${bucket_name}" >/dev/null 2>&1; then
        print_status "SUCCESS" "S3 bucket '${bucket_name}' exists"
        
        # Check bucket versioning
        local versioning
        versioning=$(aws --endpoint-url=${LOCALSTACK_ENDPOINT} s3api get-bucket-versioning --bucket ${bucket_name} 2>/dev/null || echo "{}")
        
        if echo "$versioning" | jq -r '.Status' | grep -q "Enabled"; then
            print_status "SUCCESS" "S3 bucket versioning is enabled"
        else
            print_status "WARNING" "S3 bucket versioning status unclear"
        fi
        
        # Check bucket notification configuration
        local notification
        notification=$(aws --endpoint-url=${LOCALSTACK_ENDPOINT} s3api get-bucket-notification-configuration --bucket ${bucket_name} 2>/dev/null || echo "{}")
        
        if echo "$notification" | jq -r '.EventBridgeConfiguration' | grep -q "true"; then
            print_status "SUCCESS" "S3 EventBridge notifications are configured"
        else
            print_status "WARNING" "S3 EventBridge notifications configuration unclear"
        fi
        
        return 0
    else
        print_status "ERROR" "S3 bucket '${bucket_name}' not found"
        return 1
    fi
}

# Function to verify SQS queues
verify_sqs_queues() {
    print_status "INFO" "Verifying SQS queues..."
    
    local main_queue="wifi_scan_ingestion_event_queue"
    local dlq="wifi_scan_ingestion_event_queue-dlq"
    
    # Get queue list
    local queues
    queues=$(aws --endpoint-url=${LOCALSTACK_ENDPOINT} sqs list-queues 2>/dev/null || echo '{"QueueUrls": []}')
    
    # Check main queue
    if echo "$queues" | jq -r '.QueueUrls[]' | grep -q "${main_queue}"; then
        print_status "SUCCESS" "Main SQS queue '${main_queue}' exists"
        
        # Get queue attributes
        local queue_url
        queue_url=$(echo "$queues" | jq -r '.QueueUrls[]' | grep "${main_queue}")
        
        local attributes
        attributes=$(aws --endpoint-url=${LOCALSTACK_ENDPOINT} sqs get-queue-attributes --queue-url "${queue_url}" --attribute-names All 2>/dev/null || echo '{"Attributes": {}}')
        
        local visibility_timeout
        visibility_timeout=$(echo "$attributes" | jq -r '.Attributes.VisibilityTimeoutSeconds' 2>/dev/null || echo "unknown")
        
        local retention
        retention=$(echo "$attributes" | jq -r '.Attributes.MessageRetentionPeriod' 2>/dev/null || echo "unknown")
        
        print_status "INFO" "Queue visibility timeout: ${visibility_timeout}s"
        print_status "INFO" "Queue message retention: ${retention}s"
    else
        print_status "ERROR" "Main SQS queue '${main_queue}' not found"
        return 1
    fi
    
    # Check DLQ
    if echo "$queues" | jq -r '.QueueUrls[]' | grep -q "${dlq}"; then
        print_status "SUCCESS" "Dead letter queue '${dlq}' exists"
    else
        print_status "ERROR" "Dead letter queue '${dlq}' not found"
        return 1
    fi
    
    return 0
}

# Function to verify Kinesis Firehose
verify_firehose() {
    print_status "INFO" "Verifying Kinesis Firehose delivery stream..."
    
    local stream_name="MVS-stream"
    
    # Check if stream exists
    local streams
    streams=$(aws --endpoint-url=${LOCALSTACK_ENDPOINT} firehose list-delivery-streams 2>/dev/null || echo '{"DeliveryStreamNames": []}')
    
    if echo "$streams" | jq -r '.DeliveryStreamNames[]' | grep -q "${stream_name}"; then
        print_status "SUCCESS" "Firehose delivery stream '${stream_name}' exists"
        
        # Get stream description
        local description
        description=$(aws --endpoint-url=${LOCALSTACK_ENDPOINT} firehose describe-delivery-stream --delivery-stream-name "${stream_name}" 2>/dev/null || echo '{}')
        
        local status
        status=$(echo "$description" | jq -r '.DeliveryStreamDescription.DeliveryStreamStatus' 2>/dev/null || echo "unknown")
        
        local destination
        destination=$(echo "$description" | jq -r '.DeliveryStreamDescription.Destinations[0].ExtendedS3DestinationDescription.BucketARN' 2>/dev/null || echo "unknown")
        
        print_status "INFO" "Stream status: ${status}"
        print_status "INFO" "S3 destination: ${destination}"
        
        if [ "$status" = "ACTIVE" ]; then
            print_status "SUCCESS" "Firehose stream is active"
        else
            print_status "WARNING" "Firehose stream status is not active: ${status}"
        fi
        
        return 0
    else
        print_status "ERROR" "Firehose delivery stream '${stream_name}' not found"
        return 1
    fi
}

# Function to verify EventBridge rule
verify_eventbridge() {
    print_status "INFO" "Verifying EventBridge rule..."
    
    local rule_name="wifi-scan-s3-put-rule"
    
    # Check if rule exists
    local rules
    rules=$(aws --endpoint-url=${LOCALSTACK_ENDPOINT} events list-rules 2>/dev/null || echo '{"Rules": []}')
    
    if echo "$rules" | jq -r '.Rules[].Name' | grep -q "${rule_name}"; then
        print_status "SUCCESS" "EventBridge rule '${rule_name}' exists"
        
        # Get rule details
        local rule_detail
        rule_detail=$(echo "$rules" | jq -r ".Rules[] | select(.Name == \"${rule_name}\")")
        
        local state
        state=$(echo "$rule_detail" | jq -r '.State' 2>/dev/null || echo "unknown")
        
        local event_pattern
        event_pattern=$(echo "$rule_detail" | jq -r '.EventPattern' 2>/dev/null || echo "unknown")
        
        print_status "INFO" "Rule state: ${state}"
        
        if [ "$state" = "ENABLED" ]; then
            print_status "SUCCESS" "EventBridge rule is enabled"
        else
            print_status "WARNING" "EventBridge rule is not enabled: ${state}"
        fi
        
        # Check if event pattern includes S3 and txt filter
        if echo "$event_pattern" | grep -q "aws.s3" && echo "$event_pattern" | grep -q ".txt"; then
            print_status "SUCCESS" "EventBridge rule has correct S3 .txt event pattern"
        else
            print_status "WARNING" "EventBridge rule event pattern may be incorrect"
        fi
        
        # Check targets
        local targets
        targets=$(aws --endpoint-url=${LOCALSTACK_ENDPOINT} events list-targets-by-rule --rule "${rule_name}" 2>/dev/null || echo '{"Targets": []}')
        
        local target_count
        target_count=$(echo "$targets" | jq '.Targets | length' 2>/dev/null || echo "0")
        
        if [ "$target_count" -gt 0 ]; then
            print_status "SUCCESS" "EventBridge rule has ${target_count} target(s) configured"
        else
            print_status "WARNING" "EventBridge rule has no targets configured"
        fi
        
        return 0
    else
        print_status "ERROR" "EventBridge rule '${rule_name}' not found"
        return 1
    fi
}

# Function to verify IAM roles
verify_iam_roles() {
    print_status "INFO" "Verifying IAM roles..."
    
    local roles=("firehose-delivery-role" "s3-eventbridge-role" "eventbridge-sqs-role")
    local all_roles_exist=true
    
    for role in "${roles[@]}"; do
        if aws --endpoint-url=${LOCALSTACK_ENDPOINT} iam get-role --role-name "${role}" >/dev/null 2>&1; then
            print_status "SUCCESS" "IAM role '${role}' exists"
            
            # Check attached policies
            local policies
            policies=$(aws --endpoint-url=${LOCALSTACK_ENDPOINT} iam list-role-policies --role-name "${role}" 2>/dev/null || echo '{"PolicyNames": []}')
            
            local policy_count
            policy_count=$(echo "$policies" | jq '.PolicyNames | length' 2>/dev/null || echo "0")
            
            if [ "$policy_count" -gt 0 ]; then
                print_status "INFO" "Role '${role}' has ${policy_count} inline policy(ies)"
            else
                print_status "WARNING" "Role '${role}' has no inline policies"
            fi
        else
            print_status "ERROR" "IAM role '${role}' not found"
            all_roles_exist=false
        fi
    done
    
    if $all_roles_exist; then
        return 0
    else
        return 1
    fi
}

# Function to verify Terraform state
verify_terraform_state() {
    print_status "INFO" "Verifying Terraform state..."
    
    if [ ! -f "$TERRAFORM_DIR/terraform.tfstate" ]; then
        print_status "ERROR" "Terraform state file not found"
        return 1
    fi
    
    cd "$TERRAFORM_DIR"
    
    # Check if terraform show works
    if terraform show >/dev/null 2>&1; then
        print_status "SUCCESS" "Terraform state is valid"
        
        # Count resources
        local resource_count
        resource_count=$(terraform show -json 2>/dev/null | jq '.values.root_module.resources | length' 2>/dev/null || echo "unknown")
        
        print_status "INFO" "Terraform manages ${resource_count} resources"
        
        return 0
    else
        print_status "ERROR" "Terraform state is corrupted or invalid"
        return 1
    fi
}

# Function to generate verification report
generate_report() {
    local s3_status=$1
    local sqs_status=$2
    local firehose_status=$3
    local eventbridge_status=$4
    local iam_status=$5
    local terraform_status=$6
    
    print_status "INFO" "=== VERIFICATION REPORT ==="
    
    echo "Component Status:"
    echo "  S3 Bucket:        $([ $s3_status -eq 0 ] && echo "✅ PASS" || echo "❌ FAIL")"
    echo "  SQS Queues:       $([ $sqs_status -eq 0 ] && echo "✅ PASS" || echo "❌ FAIL")"
    echo "  Firehose Stream:  $([ $firehose_status -eq 0 ] && echo "✅ PASS" || echo "❌ FAIL")"
    echo "  EventBridge Rule: $([ $eventbridge_status -eq 0 ] && echo "✅ PASS" || echo "❌ FAIL")"
    echo "  IAM Roles:        $([ $iam_status -eq 0 ] && echo "✅ PASS" || echo "❌ FAIL")"
    echo "  Terraform State:  $([ $terraform_status -eq 0 ] && echo "✅ PASS" || echo "❌ FAIL")"
    
    local total_failed=$((6 - (s3_status == 0) - (sqs_status == 0) - (firehose_status == 0) - (eventbridge_status == 0) - (iam_status == 0) - (terraform_status == 0)))
    
    if [ $total_failed -eq 0 ]; then
        print_status "SUCCESS" "All components verified successfully! 🎉"
        print_status "INFO" "The WiFi scan ingestion pipeline is ready for testing"
    else
        print_status "ERROR" "${total_failed} component(s) failed verification"
        print_status "INFO" "Please check the errors above and redeploy if necessary"
    fi
    
    return $total_failed
}

# Main function
main() {
    print_status "INFO" "Starting infrastructure verification..."
    
    # Set AWS credentials
    set_aws_credentials
    
    # Check LocalStack
    if ! check_localstack; then
        print_status "ERROR" "Cannot verify infrastructure without LocalStack"
        exit 1
    fi
    
    # Initialize status variables
    local s3_status=1
    local sqs_status=1
    local firehose_status=1
    local eventbridge_status=1
    local iam_status=1
    local terraform_status=1
    
    # Run verifications
    verify_s3_bucket && s3_status=0 || true
    verify_sqs_queues && sqs_status=0 || true
    verify_firehose && firehose_status=0 || true
    verify_eventbridge && eventbridge_status=0 || true
    verify_iam_roles && iam_status=0 || true
    verify_terraform_state && terraform_status=0 || true
    
    # Generate report
    generate_report $s3_status $sqs_status $firehose_status $eventbridge_status $iam_status $terraform_status
    local report_result=$?
    
    if [ $report_result -eq 0 ]; then
        print_status "INFO" "Next steps:"
        print_status "INFO" "  - Run './scripts/test-pipeline.sh' to test the data flow"
        print_status "INFO" "  - Check LocalStack logs: docker logs wifi-localstack"
    fi
    
    exit $report_result
}

# Run main function
main "$@" 