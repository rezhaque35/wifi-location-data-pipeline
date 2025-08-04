#!/bin/bash

# Set color variables
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${YELLOW}Building the application with AWS profile...${NC}"
./mvnw clean package -DskipTests -Paws

echo -e "${YELLOW}Building Docker image...${NC}"
docker build -t wifi-positioning-service:latest .

# Example Docker tag and push to ECR (uncomment and customize as needed)
# AWS_ACCOUNT=$(aws sts get-caller-identity --query Account --output text)
# AWS_REGION=us-east-1
# echo -e "${YELLOW}Tagging and pushing to ECR...${NC}"
# aws ecr get-login-password --region $AWS_REGION | docker login --username AWS --password-stdin $AWS_ACCOUNT.dkr.ecr.$AWS_REGION.amazonaws.com
# docker tag wifi-positioning-service:latest $AWS_ACCOUNT.dkr.ecr.$AWS_REGION.amazonaws.com/wifi-positioning-service:latest
# docker push $AWS_ACCOUNT.dkr.ecr.$AWS_REGION.amazonaws.com/wifi-positioning-service:latest

echo -e "${YELLOW}Creating Kubernetes deployment files...${NC}"
mkdir -p k8s

cat > k8s/deployment.yaml << EOF
apiVersion: apps/v1
kind: Deployment
metadata:
  name: wifi-positioning-service
  labels:
    app: wifi-positioning-service
spec:
  replicas: 2
  selector:
    matchLabels:
      app: wifi-positioning-service
  template:
    metadata:
      labels:
        app: wifi-positioning-service
    spec:
      containers:
      - name: wifi-positioning-service
        image: ${AWS_ACCOUNT}.dkr.ecr.${AWS_REGION}.amazonaws.com/wifi-positioning-service:latest
        ports:
        - containerPort: 8080
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "aws"
        - name: AWS_REGION
          valueFrom:
            configMapKeyRef:
              name: aws-config
              key: region
        resources:
          limits:
            cpu: "1"
            memory: "1Gi"
          requests:
            cpu: "500m"
            memory: "512Mi"
EOF

cat > k8s/service.yaml << EOF
apiVersion: v1
kind: Service
metadata:
  name: wifi-positioning-service
spec:
  selector:
    app: wifi-positioning-service
  ports:
  - port: 80
    targetPort: 8080
  type: ClusterIP
EOF

cat > k8s/configmap.yaml << EOF
apiVersion: v1
kind: ConfigMap
metadata:
  name: aws-config
data:
  region: ${AWS_REGION:-us-east-1}
EOF

echo -e "${GREEN}Deployment files created in k8s directory${NC}"
echo -e "${YELLOW}To deploy to EKS, run:${NC}"
echo -e "kubectl apply -f k8s/"
echo -e "${YELLOW}Note: Make sure your EKS cluster has the proper IAM roles configured for DynamoDB access${NC}" 