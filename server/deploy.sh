#!/bin/bash
# EC2 Server Deployment Script for Amazon Linux 2023
# Run this script on your EC2 instance after uploading the server folder

echo "Starting OneSecClone Analytics Server deployment..."

# Update system
echo "Updating system packages..."
sudo dnf update -y

# Install Node.js and npm
echo "Installing Node.js..."
curl -fsSL https://rpm.nodesource.com/setup_18.x | sudo bash -
sudo dnf install -y nodejs

# Install PostgreSQL
echo "Installing PostgreSQL..."
sudo dnf install -y postgresql15-server postgresql15-contrib
sudo postgresql-setup --initdb
sudo systemctl enable postgresql
sudo systemctl start postgresql

# Install PM2 (process manager)
echo "Installing PM2..."
sudo npm install -g pm2

# Navigate to the server directory
cd ~/server

# Copy environment file
cp .env.example .env

# Install dependencies
echo "Installing Node.js dependencies..."
npm install

# Set up the database
echo "Setting up database..."
sudo -u postgres psql << EOF
CREATE DATABASE onesec_analytics;
CREATE USER onesec_user WITH PASSWORD 'your_secure_password';
GRANT ALL PRIVILEGES ON DATABASE onesec_analytics TO onesec_user;
\q
EOF

# Run database schema
echo "Creating database tables..."
sudo -u postgres psql -d onesec_analytics -f database_schema.sql

# Start the server with PM2
echo "Starting server with PM2..."
pm2 start server.js --name "onesec-analytics"
pm2 save
pm2 startup

# Configure Nginx (optional)
echo "Server setup complete!"
echo "Don't forget to:"
echo "1. Edit .env file with your database password (currently set to 'your_secure_password')"
echo "2. Configure your security group to allow port 8080"
echo "3. Test the server: curl http://localhost:8080/api/health"
echo "4. Check server status: pm2 status"
