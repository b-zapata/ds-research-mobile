# AWS EC2 Server Setup Guide for OneSecClone Analytics

## 1. EC2 Instance Configuration

### Instance Requirements:
- **Instance Type**: t3.micro or t3.small (for basic usage)
- **OS**: Amazon Linux 2023 (or Ubuntu 22.04 LTS)
- **Storage**: 20GB+ SSD
- **Security Group**: Configure ports 22 (SSH), 80 (HTTP), 443 (HTTPS), 8080 (API)

### Security Group Rules:
```
Type: SSH, Port: 22, Source: Your IP
Type: HTTP, Port: 80, Source: 0.0.0.0/0
Type: HTTPS, Port: 443, Source: 0.0.0.0/0
Type: Custom TCP, Port: 8080, Source: 0.0.0.0/0
```

## 2. Server Installation Commands

Connect to your EC2 instance via SSH and run these commands:

### For Amazon Linux 2023:
```bash
# Update system
sudo dnf update -y

# Install Node.js and npm
curl -fsSL https://rpm.nodesource.com/setup_18.x | sudo bash -
sudo dnf install -y nodejs

# Install PostgreSQL
sudo dnf install -y postgresql15-server postgresql15-contrib
sudo postgresql-setup --initdb
sudo systemctl enable postgresql
sudo systemctl start postgresql

# Install Nginx (for reverse proxy)
sudo dnf install -y nginx
sudo systemctl enable nginx
sudo systemctl start nginx

# Install PM2 (process manager)
sudo npm install -g pm2

# Install certbot for SSL certificates
sudo dnf install -y certbot python3-certbot-nginx

# Install git (if not already installed)
sudo dnf install -y git
```

### For Ubuntu 22.04 (alternative):
```bash
# Update system
sudo apt update && sudo apt upgrade -y

# Install Node.js and npm
curl -fsSL https://deb.nodesource.com/setup_18.x | sudo -E bash -
sudo apt-get install -y nodejs

# Install PostgreSQL
sudo apt install postgresql postgresql-contrib -y

# Install Nginx (for reverse proxy)
sudo apt install nginx -y

# Install PM2 (process manager)
sudo npm install -g pm2

# Install certbot for SSL certificates
sudo apt install certbot python3-certbot-nginx -y
```

## 3. Database Setup

```bash
# Switch to postgres user
sudo -u postgres psql

# Create database and user
CREATE DATABASE onesec_analytics;
CREATE USER onesec_user WITH PASSWORD 'your_secure_password';
GRANT ALL PRIVILEGES ON DATABASE onesec_analytics TO onesec_user;
\q
```

## 4. Required Information

### What you need to provide:
1. **EC2 Public IP**: Replace `YOUR_EC2_IP_OR_DOMAIN` in NetworkClient.kt
2. **Domain Name (optional)**: If you have a custom domain
3. **Database Password**: Choose a secure password for PostgreSQL

### Example URL formats:
- With IP: `https://54.123.456.789:8080/`
- With domain: `https://yourdomain.com/`

## 5. Next Steps

1. **Get your EC2 instance**: Note down the public IP address
2. **Update NetworkClient.kt**: Replace the placeholder URL with your actual IP/domain
3. **Deploy the server**: I'll create the server code for you
4. **Configure SSL**: Set up HTTPS certificates for secure communication

Would you like me to:
- Create the complete Node.js server code?
- Help you get your EC2 public IP?
- Set up the database schema?
