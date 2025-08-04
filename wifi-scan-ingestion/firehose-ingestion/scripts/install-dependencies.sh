#!/bin/bash
# wifi-scan-ingestion/firehose-ingestion/scripts/install-dependencies.sh
# Install required dependencies for the WiFi scan ingestion pipeline

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

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

# Function to check if command exists
command_exists() {
    command -v "$1" >/dev/null 2>&1
}

# Function to get OS type
get_os() {
    case "$(uname -s)" in
        Darwin*) echo "macos" ;;
        Linux*)  echo "linux" ;;
        CYGWIN*) echo "windows" ;;
        MINGW*)  echo "windows" ;;
        *) echo "unknown" ;;
    esac
}

# Function to install Docker on macOS
install_docker_macos() {
    if ! command_exists docker; then
        print_status "INFO" "Installing Docker Desktop for macOS..."
        print_status "WARNING" "Please download and install Docker Desktop from: https://www.docker.com/products/docker-desktop"
        print_status "WARNING" "After installation, please restart this script"
        return 1
    else
        print_status "SUCCESS" "Docker is already installed"
        docker --version
    fi
}

# Function to install Docker on Linux
install_docker_linux() {
    if ! command_exists docker; then
        print_status "INFO" "Installing Docker on Linux..."
        
        # Update package index
        sudo apt-get update
        
        # Install required packages
        sudo apt-get install -y \
            ca-certificates \
            curl \
            gnupg \
            lsb-release
        
        # Add Docker's official GPG key
        sudo mkdir -p /etc/apt/keyrings
        curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
        
        # Set up the repository
        echo \
          "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
          $(lsb_release -cs) stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
        
        # Install Docker Engine
        sudo apt-get update
        sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin
        
        # Add user to docker group
        sudo usermod -aG docker $USER
        
        print_status "WARNING" "Please log out and log back in for Docker group changes to take effect"
    else
        print_status "SUCCESS" "Docker is already installed"
        docker --version
    fi
}

# Function to install Terraform
install_terraform() {
    if ! command_exists terraform; then
        print_status "INFO" "Installing Terraform..."
        
        local os=$(get_os)
        local terraform_version="1.9.0"
        
        case $os in
            "macos")
                if command_exists brew; then
                    brew install terraform
                else
                    print_status "INFO" "Installing Homebrew first..."
                    /bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
                    brew install terraform
                fi
                ;;
            "linux")
                # Download and install Terraform binary
                wget -O- https://apt.releases.hashicorp.com/gpg | \
                    gpg --dearmor | \
                    sudo tee /usr/share/keyrings/hashicorp-archive-keyring.gpg
                
                echo "deb [signed-by=/usr/share/keyrings/hashicorp-archive-keyring.gpg] \
                    https://apt.releases.hashicorp.com $(lsb_release -cs) main" | \
                    sudo tee /etc/apt/sources.list.d/hashicorp.list
                
                sudo apt update
                sudo apt-get install terraform
                ;;
            *)
                print_status "ERROR" "Unsupported OS for automatic Terraform installation"
                print_status "INFO" "Please install Terraform manually from: https://www.terraform.io/downloads"
                return 1
                ;;
        esac
    else
        print_status "SUCCESS" "Terraform is already installed"
        terraform --version
    fi
}

# Function to install AWS CLI
install_aws_cli() {
    if ! command_exists aws; then
        print_status "INFO" "Installing AWS CLI..."
        
        local os=$(get_os)
        
        case $os in
            "macos")
                if command_exists brew; then
                    brew install awscli
                else
                    # Download and install AWS CLI v2
                    curl "https://awscli.amazonaws.com/AWSCLIV2.pkg" -o "AWSCLIV2.pkg"
                    sudo installer -pkg AWSCLIV2.pkg -target /
                    rm AWSCLIV2.pkg
                fi
                ;;
            "linux")
                curl "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o "awscliv2.zip"
                unzip awscliv2.zip
                sudo ./aws/install
                rm -rf awscliv2.zip aws/
                ;;
            *)
                print_status "ERROR" "Unsupported OS for automatic AWS CLI installation"
                print_status "INFO" "Please install AWS CLI manually from: https://aws.amazon.com/cli/"
                return 1
                ;;
        esac
    else
        print_status "SUCCESS" "AWS CLI is already installed"
        aws --version
    fi
}

# Function to install jq
install_jq() {
    if ! command_exists jq; then
        print_status "INFO" "Installing jq..."
        
        local os=$(get_os)
        
        case $os in
            "macos")
                if command_exists brew; then
                    brew install jq
                else
                    print_status "ERROR" "Homebrew not found. Please install jq manually"
                    return 1
                fi
                ;;
            "linux")
                sudo apt-get update
                sudo apt-get install -y jq
                ;;
            *)
                print_status "ERROR" "Unsupported OS for automatic jq installation"
                return 1
                ;;
        esac
    else
        print_status "SUCCESS" "jq is already installed"
        jq --version
    fi
}

# Function to install curl
install_curl() {
    if ! command_exists curl; then
        print_status "INFO" "Installing curl..."
        
        local os=$(get_os)
        
        case $os in
            "linux")
                sudo apt-get update
                sudo apt-get install -y curl
                ;;
            *)
                print_status "WARNING" "curl not found, but it should be pre-installed on most systems"
                ;;
        esac
    else
        print_status "SUCCESS" "curl is already installed"
        curl --version | head -1
    fi
}

# Main installation process
main() {
    print_status "INFO" "Starting dependency installation..."
    print_status "INFO" "Detected OS: $(get_os)"
    
    local os=$(get_os)
    
    # Install curl first (needed for other installations)
    install_curl
    
    # Install Docker
    case $os in
        "macos")
            install_docker_macos
            ;;
        "linux")
            install_docker_linux
            ;;
        *)
            print_status "ERROR" "Unsupported OS: $os"
            exit 1
            ;;
    esac
    
    # Check if Docker is running
    if ! docker info >/dev/null 2>&1; then
        print_status "WARNING" "Docker is installed but not running. Please start Docker Desktop/service"
        print_status "INFO" "On macOS: Open Docker Desktop application"
        print_status "INFO" "On Linux: sudo systemctl start docker"
    else
        print_status "SUCCESS" "Docker is running"
    fi
    
    # Install other dependencies
    install_terraform
    install_aws_cli
    install_jq
    
    print_status "SUCCESS" "All dependencies installed successfully!"
    
    # Display versions
    print_status "INFO" "Installed versions:"
    echo "  Docker: $(docker --version 2>/dev/null || echo 'Not available')"
    echo "  Terraform: $(terraform --version 2>/dev/null | head -1 || echo 'Not available')"
    echo "  AWS CLI: $(aws --version 2>/dev/null || echo 'Not available')"
    echo "  jq: $(jq --version 2>/dev/null || echo 'Not available')"
    echo "  curl: $(curl --version 2>/dev/null | head -1 || echo 'Not available')"
}

# Run main function
main "$@" 