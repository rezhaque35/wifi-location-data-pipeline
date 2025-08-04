# WiFi Scan Ingestion Pipeline - Quick Setup Guide

This guide will help you set up the complete WiFi scan ingestion pipeline infrastructure on a new machine.

## 🚀 One-Command Setup

For a complete setup from scratch:

```bash
./scripts/setup.sh
```

This single command will:
1. ✅ Install all dependencies (Docker, Terraform, AWS CLI, jq)
2. ✅ Start LocalStack with required AWS services
3. ✅ Deploy infrastructure using Terraform
4. ✅ Verify all components are working

## 📋 Prerequisites

- **macOS**: macOS 10.14+ (Docker Desktop will be installed)
- **Linux**: Ubuntu 18.04+ (Docker Engine will be installed)
- **Memory**: 4GB+ RAM for LocalStack
- **Storage**: 2GB+ free space
- **Permissions**: Administrator/sudo access for installations

## 🔧 Manual Setup (if needed)

If you prefer to run steps individually:

### Step 1: Install Dependencies
```bash
./scripts/install-dependencies.sh
```

### Step 2: Start LocalStack
```bash
./scripts/start-localstack.sh
```

### Step 3: Deploy Infrastructure
```bash
./scripts/deploy-infrastructure.sh
```

### Step 4: Verify Deployment
```bash
./scripts/verify-deployment.sh
```

## ✅ Verification

After setup, you should see:
- ✅ S3 bucket: `ingested-wifiscan-data`
- ✅ SQS queues: main queue + dead letter queue
- ✅ Kinesis Firehose: `wifi-scan-ingestion-stream`
- ✅ EventBridge rule: `wifi-scan-s3-put-rule`
- ✅ IAM roles: 3 roles with proper policies

## 🧹 Cleanup

To clean up everything:
```bash
./scripts/cleanup.sh
```

## 📚 Need Help?

- **Full documentation**: See `scripts/README.md`
- **Troubleshooting**: Check LocalStack logs with `docker logs wifi-localstack`
- **Infrastructure status**: Run `./scripts/verify-deployment.sh`

## 🎯 What's Next?

After successful setup:
1. **Phase 3 Testing**: Test the data flow pipeline
2. **Integration**: Connect with WiFi scan queue consumer
3. **Monitoring**: Set up pipeline monitoring and alerting

---

**Ready to start?** Just run: `./scripts/setup.sh` 🚀 