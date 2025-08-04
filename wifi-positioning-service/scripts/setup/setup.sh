#!/bin/bash

echo "Setting up DynamoDB Local environment..."

# Step 1: Install Homebrew if not installed
if ! command -v brew &> /dev/null; then
    echo "Installing Homebrew..."
    /bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
else
    echo "Homebrew already installed"
fi

# Step 2: Install AWS CLI
if ! command -v aws &> /dev/null; then
    echo "Installing AWS CLI..."
    brew install awscli
else
    echo "AWS CLI already installed"
fi

# Step 3: Install Docker if not installed
if ! command -v docker &> /dev/null; then
    echo "Installing Docker..."
    brew install --cask docker
    echo "Please open Docker Desktop and complete the installation"
    echo "Press any key once Docker is running..."
    read -n 1
else
    echo "Docker already installed"
fi

# Step 4: Create AWS credentials directory and config
echo "Setting up AWS credentials for DynamoDB Local..."
mkdir -p ~/.aws

# Create credentials file
cat > ~/.aws/credentials << EOL
[dynamodb-local]
aws_access_key_id = dummy
aws_secret_access_key = dummy
EOL

# Create config file
cat > ~/.aws/config << EOL
[profile dynamodb-local]
region = us-east-1
output = json
EOL

# Step 5: Pull and run DynamoDB Local
echo "Starting DynamoDB Local..."
docker pull amazon/dynamodb-local
docker run -d -p 8000:8000 amazon/dynamodb-local

# Step 6: Wait for DynamoDB to start
echo "Waiting for DynamoDB Local to start..."
sleep 5

# Step 7: Create the wifi_access_points table
echo "Creating wifi_access_points table..."
./create-and-load.sh

# Step 8: Verify table creation
echo "Verifying table creation..."
aws dynamodb list-tables --endpoint-url http://localhost:8000 --profile dynamodb-local
./verify-dynamodb-data.sh

echo "Setup complete! DynamoDB Local is running on port 8000"
echo "You can now run the load-test-data.sh script" 