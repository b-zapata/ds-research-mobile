# EC2 Deployment Guide for OneSecClone

This guide covers multiple ways to deploy your OneSecClone server to EC2, from manual SSH deployment to automated CI/CD.

## Prerequisites

1. **EC2 Instance**: Ubuntu 20.04+ LTS (recommended)
2. **Security Group**: Allow inbound traffic on port 8080 (or your chosen port) and SSH (port 22)
3. **Key Pair**: Your EC2 instance key pair (.pem file)

## Method 1: Manual SSH Deployment (Recommended for Development)

### Step 1: Prepare Your Local Files

First, create a deployment package with all necessary files:

```bash
# From your project root (C:\Users\bzapa\AndroidStudioProjects\OneSecClone)
cd server
tar -czf onesec-server.tar.gz server.js database_schema.sql package.json deploy.sh verify_data_flow.js
```

### Step 2: Connect to EC2 via SSH

#### Using Windows Command Prompt:
```cmd
ssh -i "path\to\your-key.pem" ubuntu@your-ec2-public-ip
```

#### Using WSL or Git Bash:
```bash
ssh -i "/path/to/your-key.pem" ubuntu@your-ec2-public-ip
```

#### Using PuTTY (Windows):
1. Convert .pem to .ppk using PuTTYgen
2. Use PuTTY with the .ppk key

### Step 3: Upload and Deploy

#### Option A: SCP Upload (from local machine)
```bash
scp -i "your-key.pem" onesec-server.tar.gz ubuntu@your-ec2-ip:~/
```

#### Option B: Direct Git Clone (on EC2)
```bash
# On your EC2 instance
git clone https://github.com/your-username/your-repo.git
cd your-repo/server
```

### Step 4: Run Deployment Script
```bash
# On your EC2 instance
tar -xzf onesec-server.tar.gz  # if using SCP
chmod +x deploy.sh
./deploy.sh setup
```

## Method 2: Automated Deployment Script (Windows)

Create a local Windows batch script to automate the process:

```batch
@echo off
echo Deploying OneSecClone to EC2...

set EC2_IP=your-ec2-public-ip
set KEY_PATH=path\to\your-key.pem
set PROJECT_PATH=C:\Users\bzapa\AndroidStudioProjects\OneSecClone\server

cd /d %PROJECT_PATH%

echo Creating deployment package...
tar -czf onesec-server.tar.gz server.js database_schema.sql package.json deploy.sh verify_data_flow.js

echo Uploading to EC2...
scp -i "%KEY_PATH%" onesec-server.tar.gz ubuntu@%EC2_IP%:~/

echo Deploying on EC2...
ssh -i "%KEY_PATH%" ubuntu@%EC2_IP% "tar -xzf onesec-server.tar.gz && chmod +x deploy.sh && ./deploy.sh setup"

echo Deployment complete!
pause
```

## Method 3: Using AWS CLI and CodeDeploy

### Setup AWS CLI
```bash
# Install AWS CLI
pip install awscli

# Configure credentials
aws configure
```

### Create CodeDeploy Application
```bash
aws deploy create-application --application-name OneSecClone --compute-platform Server
```

## Method 4: Docker Deployment

### Create Dockerfile
```dockerfile
FROM node:18-alpine

WORKDIR /app

COPY package*.json ./
RUN npm install --production

COPY . .

EXPOSE 8080

CMD ["node", "server.js"]
```

### Deploy with Docker
```bash
# Build and push to ECR or Docker Hub
docker build -t onesec-server .
docker tag onesec-server:latest your-registry/onesec-server:latest
docker push your-registry/onesec-server:latest

# On EC2
docker pull your-registry/onesec-server:latest
docker run -d -p 8080:8080 --env-file .env onesec-server:latest
```

## Quick SSH Commands Reference

### Basic Connection
```bash
ssh -i "your-key.pem" ubuntu@your-ec2-ip
```

### File Transfer
```bash
# Upload single file
scp -i "your-key.pem" localfile.txt ubuntu@your-ec2-ip:~/

# Upload directory
scp -i "your-key.pem" -r local-directory/ ubuntu@your-ec2-ip:~/

# Download from EC2
scp -i "your-key.pem" ubuntu@your-ec2-ip:~/remote-file.txt ./
```

### Remote Commands
```bash
# Run single command
ssh -i "your-key.pem" ubuntu@your-ec2-ip "sudo systemctl status onesec-server"

# Run multiple commands
ssh -i "your-key.pem" ubuntu@your-ec2-ip "cd ~/server && ./deploy.sh status"
```

## Troubleshooting SSH

### Permission Issues
```bash
# Fix key permissions (Linux/WSL)
chmod 400 your-key.pem

# Windows (in PowerShell as Admin)
icacls "your-key.pem" /inheritance:r /grant:r "%username%:R"
```

### Connection Issues
1. **Security Group**: Ensure port 22 is open for your IP
2. **Key Pair**: Verify you're using the correct .pem file
3. **Instance State**: Ensure EC2 instance is running
4. **Public IP**: Use the current public IP (changes on restart)

## Post-Deployment Verification

### Check Server Status
```bash
ssh -i "your-key.pem" ubuntu@your-ec2-ip "./deploy.sh status"
```

### Monitor Real-time Data
```bash
ssh -i "your-key.pem" ubuntu@your-ec2-ip "cd ~/server && node verify_data_flow.js --monitor"
```

### View Logs
```bash
ssh -i "your-key.pem" ubuntu@your-ec2-ip "./deploy.sh logs"
```

## Security Best Practices

1. **Use IAM Roles**: Instead of storing AWS credentials on EC2
2. **Update Security Groups**: Only allow necessary IPs/ports
3. **Regular Updates**: Keep system and dependencies updated
4. **Environment Variables**: Never commit sensitive data to git
5. **HTTPS**: Use SSL certificates in production

## Next Steps After Deployment

1. **Update Android App**: Configure server URL to point to your EC2 instance
2. **Test Data Flow**: Use the DataFlowTestActivity to verify connectivity
3. **Monitor Performance**: Set up CloudWatch for monitoring
4. **Backup Database**: Configure automated backups
5. **Domain Setup**: Consider using a domain name instead of IP address
