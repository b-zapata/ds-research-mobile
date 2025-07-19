#!/bin/bash

# OneSecClone Server Setup and Data Verification Script
# This script sets up your EC2 server and provides tools to verify data flow

set -e

echo "🚀 OneSecClone Server Setup and Verification"
echo "=============================================="

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
DB_NAME="onesec_analytics"
DB_USER="onesec_user"
DB_PASSWORD="${DB_PASSWORD:-$(openssl rand -base64 32)}"
SERVER_PORT="${SERVER_PORT:-8080}"

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

# Function to install dependencies
install_dependencies() {
    print_status "Installing system dependencies..."

    # Update system
    sudo dnf update -y

    # Install Node.js and npm
    if ! command -v node &> /dev/null; then
        print_status "Installing Node.js..."
        sudo dnf install -y nodejs npm
    fi

    # Install PostgreSQL
    if ! command -v psql &> /dev/null; then
        print_status "Installing PostgreSQL..."
        sudo dnf install -y postgresql15-server postgresql15
        sudo postgresql-setup --initdb
        sudo systemctl start postgresql
        sudo systemctl enable postgresql
    fi

    # Install tar and other utilities
    sudo dnf install -y tar gzip curl

    print_success "Dependencies installed successfully"
}

# Function to setup database
setup_database() {
    print_status "Setting up PostgreSQL database..."

    # Create database and user
    sudo -u postgres psql << EOF
CREATE DATABASE $DB_NAME;
CREATE USER $DB_USER WITH ENCRYPTED PASSWORD '$DB_PASSWORD';
GRANT ALL PRIVILEGES ON DATABASE $DB_NAME TO $DB_USER;
ALTER USER $DB_USER CREATEDB;
\q
EOF

    # Run schema setup
    if [ -f "database_schema.sql" ]; then
        print_status "Creating database schema..."
        PGPASSWORD=$DB_PASSWORD psql -h localhost -U $DB_USER -d $DB_NAME -f database_schema.sql
        print_success "Database schema created successfully"
    else
        print_warning "database_schema.sql not found, skipping schema creation"
    fi
}

# Function to setup environment
setup_environment() {
    print_status "Setting up environment variables..."

    cat > .env << EOF
# Database Configuration
DB_HOST=localhost
DB_PORT=5432
DB_NAME=$DB_NAME
DB_USER=$DB_USER
DB_PASSWORD=$DB_PASSWORD

# Server Configuration
PORT=$SERVER_PORT
NODE_ENV=production

# Security
SESSION_SECRET=$(openssl rand -base64 32)
EOF

    print_success "Environment file created"
}

# Function to install Node.js dependencies
install_node_deps() {
    print_status "Installing Node.js dependencies..."

    if [ ! -f "package.json" ]; then
        print_status "Creating package.json..."
        npm init -y
    fi

    # Install required packages
    npm install express cors helmet morgan express-rate-limit compression pg dotenv

    print_success "Node.js dependencies installed"
}

# Function to setup systemd service
setup_service() {
    print_status "Setting up systemd service..."

    sudo tee /etc/systemd/system/onesec-server.service > /dev/null << EOF
[Unit]
Description=OneSecClone Analytics Server
After=network.target postgresql.service

[Service]
Type=simple
User=$USER
WorkingDirectory=$(pwd)
ExecStart=/usr/bin/node server.js
Restart=always
RestartSec=10
Environment=NODE_ENV=production

[Install]
WantedBy=multi-user.target
EOF

    sudo systemctl daemon-reload
    sudo systemctl enable onesec-server

    print_success "Systemd service configured"
}

# Function to setup firewall
setup_firewall() {
    print_status "Configuring firewall..."

    # Install ufw if not installed
    if ! command -v ufw &> /dev/null; then
        sudo dnf install -y ufw
    fi

    # Configure firewall rules
    sudo ufw allow ssh
    sudo ufw allow $SERVER_PORT
    sudo ufw --force enable

    print_success "Firewall configured"
}

# Function to test server
test_server() {
    print_status "Testing server functionality..."

    # Start server in background for testing
    node server.js &
    SERVER_PID=$!

    sleep 5

    # Test health endpoint
    if curl -s http://localhost:$SERVER_PORT/api/health | grep -q "success"; then
        print_success "Health endpoint working"
    else
        print_error "Health endpoint failed"
    fi

    # Kill test server
    kill $SERVER_PID 2>/dev/null || true
    wait $SERVER_PID 2>/dev/null || true
}

# Function to start monitoring
start_monitoring() {
    print_status "Starting data flow monitoring..."

    if [ -f "verify_data_flow.js" ]; then
        echo ""
        echo "📊 Data Flow Monitoring Tools Available:"
        echo "   Real-time monitoring: node verify_data_flow.js --monitor"
        echo "   Full verification:    node verify_data_flow.js"
        echo ""

        # Run initial verification
        node verify_data_flow.js
    else
        print_warning "verify_data_flow.js not found"
    fi
}

# Function to show status
show_status() {
    echo ""
    echo "🔍 Current Server Status"
    echo "========================"

    # Check if service is running
    if systemctl is-active --quiet onesec-server; then
        print_success "OneSecClone server is running"
    else
        print_warning "OneSecClone server is not running"
    fi

    # Check database connection
    if PGPASSWORD=$DB_PASSWORD psql -h localhost -U $DB_USER -d $DB_NAME -c '\q' 2>/dev/null; then
        print_success "Database connection working"
    else
        print_error "Database connection failed"
    fi

    # Show server logs
    echo ""
    echo "📋 Recent Server Logs:"
    sudo journalctl -u onesec-server --no-pager -n 10 || echo "No logs available"

    # Show network info
    echo ""
    echo "🌐 Network Information:"
    echo "   Public IP: $(curl -s ifconfig.me || echo 'Unable to determine')"
    echo "   Local IP:  $(hostname -I | awk '{print $1}')"
    echo "   Server Port: $SERVER_PORT"
    echo ""
    echo "📱 Android App Configuration:"
    echo "   Server URL: http://$(curl -s ifconfig.me):$SERVER_PORT/"
}

# Main execution
case "${1:-setup}" in
    "setup")
        echo "🔧 Running full server setup..."
        install_dependencies
        setup_database
        setup_environment
        install_node_deps
        setup_service
        setup_firewall
        test_server

        print_success "Setup completed successfully!"
        echo ""
        echo "📋 Next Steps:"
        echo "1. Start the server: sudo systemctl start onesec-server"
        echo "2. Check status: ./deploy.sh status"
        echo "3. Monitor data: node verify_data_flow.js --monitor"
        echo "4. Configure your Android app with server URL: http://$(curl -s ifconfig.me):$SERVER_PORT/"
        ;;

    "start")
        print_status "Starting OneSecClone server..."
        sudo systemctl start onesec-server
        sleep 2
        show_status
        ;;

    "stop")
        print_status "Stopping OneSecClone server..."
        sudo systemctl stop onesec-server
        print_success "Server stopped"
        ;;

    "restart")
        print_status "Restarting OneSecClone server..."
        sudo systemctl restart onesec-server
        sleep 2
        show_status
        ;;

    "status")
        show_status
        ;;

    "monitor")
        start_monitoring
        ;;

    "logs")
        echo "📋 Live Server Logs (Ctrl+C to exit):"
        sudo journalctl -u onesec-server -f
        ;;

    "help"|"-h"|"--help")
        echo "OneSecClone Server Management Script"
        echo ""
        echo "Usage: $0 [command]"
        echo ""
        echo "Commands:"
        echo "  setup     - Run full server setup (default)"
        echo "  start     - Start the server"
        echo "  stop      - Stop the server"
        echo "  restart   - Restart the server"
        echo "  status    - Show server status"
        echo "  monitor   - Start data flow monitoring"
        echo "  logs      - Show live server logs"
        echo "  help      - Show this help message"
        ;;

    *)
        print_error "Unknown command: $1"
        echo "Use '$0 help' for available commands"
        exit 1
        ;;
esac
