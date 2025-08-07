#!/bin/bash

# wifi-database/wifi-scan-collection/wifi-scan-queue-consumer/scripts/generate-ssl-certs.sh
# SSL certificate generation script for local Kafka development

set -e  # Exit on any error

echo "🔐 Generating SSL certificates for local Kafka..."

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
KEYSTORE_PASSWORD="kafka123"
TRUSTSTORE_PASSWORD="kafka123"
KEY_PASSWORD="kafka123"
VALIDITY_DAYS=365
KEYSTORE_FILE="kafka.keystore.p12"
TRUSTSTORE_FILE="kafka.truststore.p12"
CA_CERT="ca-cert"
CA_KEY="ca-key"

# Directory paths - Fixed to create kafka/secrets in scripts directory
SECRETS_DIR="kafka/secrets"
TEST_RESOURCES_SECRETS_DIR="../src/test/resources/secrets"

# Function to create directories
create_directories() {
    print_status "Creating certificate directories..."
    mkdir -p "${SECRETS_DIR}"
    mkdir -p "${TEST_RESOURCES_SECRETS_DIR}"
    print_success "Directories created!"
}

# Function to cleanup existing certificates
cleanup_existing_certificates() {
    print_status "Cleaning up existing certificates..."
    
    if [ -d "${SECRETS_DIR}" ]; then
        rm -f "${SECRETS_DIR}"/*.p12
        rm -f "${SECRETS_DIR}"/ca-cert
        rm -f "${SECRETS_DIR}"/ca-key
        rm -f "${SECRETS_DIR}"/*_creds
        rm -f "${SECRETS_DIR}"/cert-*
        rm -f "${SECRETS_DIR}"/*.srl
        print_success "Existing certificates cleaned up!"
    else
        print_status "No existing certificates found."
    fi
    
    # Also cleanup test resources directory
    if [ -d "${TEST_RESOURCES_SECRETS_DIR}" ]; then
        rm -f "${TEST_RESOURCES_SECRETS_DIR}"/*.p12
        print_success "Test resources certificates cleaned up!"
    fi
}

# Function to generate CA certificate
generate_ca_certificate() {
    print_status "Generating Certificate Authority (CA)..."
    
    cd "${SECRETS_DIR}"
    
    # Generate CA private key
    openssl req -new -x509 -keyout "${CA_KEY}" -out "${CA_CERT}" -days ${VALIDITY_DAYS} \
        -subj "/C=US/ST=CA/L=San Francisco/O=Local Development/OU=Kafka/CN=localhost" \
        -passin pass:${KEY_PASSWORD} -passout pass:${KEY_PASSWORD}
    
    print_success "CA certificate generated!"
    cd - > /dev/null
}

# Function to generate Kafka keystore
generate_kafka_keystore() {
    print_status "Generating Kafka keystore..."
    
    cd "${SECRETS_DIR}"
    
    # Generate Kafka keystore and key pair (PKCS12 format)
    keytool -genkey -noprompt \
        -alias kafka \
        -dname "CN=localhost,OU=Kafka,O=Local Development,L=San Francisco,S=CA,C=US" \
        -keystore "${KEYSTORE_FILE}" \
        -storetype PKCS12 \
        -keyalg RSA \
        -storepass "${KEYSTORE_PASSWORD}" \
        -keypass "${KEY_PASSWORD}" \
        -validity ${VALIDITY_DAYS}
    
    print_success "Kafka keystore generated!"
    cd - > /dev/null
}

# Function to generate certificate signing request
generate_csr() {
    print_status "Generating Certificate Signing Request (CSR)..."
    
    cd "${SECRETS_DIR}"
    
    # Generate certificate signing request
    keytool -certreq -noprompt \
        -alias kafka \
        -keystore "${KEYSTORE_FILE}" \
        -storetype PKCS12 \
        -file cert-file \
        -storepass "${KEYSTORE_PASSWORD}" \
        -keypass "${KEY_PASSWORD}"
    
    print_success "CSR generated!"
    cd - > /dev/null
}

# Function to sign the certificate
sign_certificate() {
    print_status "Signing certificate with CA..."
    
    cd "${SECRETS_DIR}"
    
    # Sign the certificate
    openssl x509 -req -CA "${CA_CERT}" -CAkey "${CA_KEY}" \
        -in cert-file -out cert-signed \
        -days ${VALIDITY_DAYS} -CAcreateserial \
        -passin pass:${KEY_PASSWORD}
    
    print_success "Certificate signed!"
    cd - > /dev/null
}

# Function to import certificates into keystore
import_certificates_to_keystore() {
    print_status "Importing certificates into keystore..."
    
    cd "${SECRETS_DIR}"
    
    # Import CA certificate into keystore
    keytool -import -noprompt \
        -keystore "${KEYSTORE_FILE}" \
        -storetype PKCS12 \
        -alias CARoot \
        -file "${CA_CERT}" \
        -storepass "${KEYSTORE_PASSWORD}"
    
    # Import signed certificate into keystore
    keytool -import -noprompt \
        -keystore "${KEYSTORE_FILE}" \
        -storetype PKCS12 \
        -alias kafka \
        -file cert-signed \
        -storepass "${KEYSTORE_PASSWORD}"
    
    print_success "Certificates imported into keystore!"
    cd - > /dev/null
}

# Function to create truststore
create_truststore() {
    print_status "Creating truststore..."
    
    cd "${SECRETS_DIR}"
    
    # Create truststore and import CA certificate (PKCS12 format)
    keytool -import -noprompt \
        -keystore "${TRUSTSTORE_FILE}" \
        -storetype PKCS12 \
        -alias CARoot \
        -file "${CA_CERT}" \
        -storepass "${TRUSTSTORE_PASSWORD}"
    
    print_success "Truststore created!"
    cd - > /dev/null
}

# Function to create credential files for Kafka
create_credential_files() {
    print_status "Creating credential files..."
    
    cd "${SECRETS_DIR}"
    
    # Create credential files
    echo "${KEYSTORE_PASSWORD}" > keystore_creds
    echo "${KEY_PASSWORD}" > key_creds
    echo "${TRUSTSTORE_PASSWORD}" > truststore_creds
    
    print_success "Credential files created!"
    cd - > /dev/null
}

# Function to copy keystore and truststore to test resources
copy_to_test_resources() {
    print_status "Copying keystore and truststore to test resources..."
    
    # Copy keystore and truststore files to test resources
    cp "${SECRETS_DIR}/${KEYSTORE_FILE}" "${TEST_RESOURCES_SECRETS_DIR}/"
    cp "${SECRETS_DIR}/${TRUSTSTORE_FILE}" "${TEST_RESOURCES_SECRETS_DIR}/"
    
    print_success "Keystore and truststore copied to test resources!"
    print_status "Test resources location: ${TEST_RESOURCES_SECRETS_DIR}"
}

# Function to validate generated certificates
validate_certificates() {
    print_status "Validating generated certificates..."
    
    cd "${SECRETS_DIR}"
    
    # List keystore contents
    print_status "Keystore contents:"
    keytool -list -v -keystore "${KEYSTORE_FILE}" -storetype PKCS12 -storepass "${KEYSTORE_PASSWORD}" | head -20
    
    # List truststore contents
    print_status "Truststore contents:"
    keytool -list -v -keystore "${TRUSTSTORE_FILE}" -storetype PKCS12 -storepass "${TRUSTSTORE_PASSWORD}" | head -10
    
    print_success "Certificate validation completed!"
    cd - > /dev/null
}

# Function to cleanup temporary files
cleanup_temp_files() {
    print_status "Cleaning up temporary files..."
    
    cd "${SECRETS_DIR}"
    
    # Remove temporary files
    rm -f cert-file cert-signed "${CA_CERT}.srl"
    
    print_success "Temporary files cleaned up!"
    cd - > /dev/null
}

# Main execution
main() {
    echo "========================================"
    echo "🔐 SSL Certificate Generation for Kafka"
    echo "========================================"
    
    create_directories
    cleanup_existing_certificates
    generate_ca_certificate
    generate_kafka_keystore
    generate_csr
    sign_certificate
    import_certificates_to_keystore
    create_truststore
    create_credential_files
    copy_to_test_resources
    validate_certificates
    cleanup_temp_files
    
    print_success "SSL certificate generation completed successfully!"
    echo ""
    echo "Generated files:"
    echo "- ${SECRETS_DIR}/${KEYSTORE_FILE}"
    echo "- ${SECRETS_DIR}/${TRUSTSTORE_FILE}"
    echo ""
    echo "Test resources files:"
    echo "- ${TEST_RESOURCES_SECRETS_DIR}/${KEYSTORE_FILE}"
    echo "- ${TEST_RESOURCES_SECRETS_DIR}/${TRUSTSTORE_FILE}"
    echo ""
    echo "Certificate locations for application:"
    echo "- Keystore: ${SECRETS_DIR}/${KEYSTORE_FILE}"
    echo "- Truststore: ${SECRETS_DIR}/${TRUSTSTORE_FILE}"
    echo ""
    echo "Passwords (for local development only):"
    echo "- Keystore password: ${KEYSTORE_PASSWORD}"
    echo "- Truststore password: ${TRUSTSTORE_PASSWORD}"
    echo "- Key password: ${KEY_PASSWORD}"
    echo ""
    print_success "✅ Keystore and truststore files have been automatically copied to test resources!"
    print_success "✅ Unit tests should now work on other machines without manual file copying!"
    echo ""
}

# Run main function
main "$@" 