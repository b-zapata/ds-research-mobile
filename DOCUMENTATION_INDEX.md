# OneSecClone Documentation Index

This document provides an overview of all documentation in this project and where to find what you need.

## 🎯 **Quick Start Guides**

### For Server Deployment

- **📘 New Duplicate Prevention Deployment**: `server/DUPLICATE_PREVENTION_DEPLOYMENT.md`
- **📗 Standard EC2 Deployment**: `server/EC2_DEPLOYMENT_GUIDE.md`
- **📙 Server Configuration**: `SERVER_CONFIG_GUIDE.md`

### For Mobile App

- **📱 IP Configuration**: `SERVER_CONFIG_GUIDE.md` (Section: "Changing Server at Runtime")

## 📚 **Complete Documentation Structure**

### **Root Directory**

| File                            | Purpose                                              | When to Use                                                 |
| ------------------------------- | ---------------------------------------------------- | ----------------------------------------------------------- |
| `SERVER_CONFIG_GUIDE.md`        | How to configure app to connect to different servers | When changing server IPs or setting up environments         |
| `SERVER_IP_UPDATE_GUIDE.md`     | Legacy IP update instructions                        | **DEPRECATED** - Use SERVER_CONFIG_GUIDE.md instead         |
| `server_setup_guide.md`         | General server setup                                 | **DEPRECATED** - Use server/EC2_DEPLOYMENT_GUIDE.md instead |
| `DUPLICATE_PREVENTION_READY.md` | Summary of duplicate prevention implementation       | Reference for what was implemented                          |

### **Server Directory** (`server/`)

| File                                 | Purpose                                         | When to Use                                                       |
| ------------------------------------ | ----------------------------------------------- | ----------------------------------------------------------------- |
| `README.md`                          | Complete server documentation and API reference | Understanding server features and monitoring                      |
| `EC2_DEPLOYMENT_GUIDE.md`            | Standard server deployment to EC2               | Deploying basic server without duplicate prevention               |
| `DUPLICATE_PREVENTION_DEPLOYMENT.md` | Deploy duplicate prevention system to EC2       | **PRIMARY** - Deploying enhanced server with duplicate protection |
| `DATABASE_SCHEMA.md`                 | Complete database schema documentation          | Understanding database structure and constraints                  |
| `ssh_instructions.md`                | SSH connection instructions                     | Basic EC2 connection troubleshooting                              |

### **Scripts Directory** (`scripts/`)

| File                   | Purpose                              | When to Use                        |
| ---------------------- | ------------------------------------ | ---------------------------------- |
| `README.md`            | Script documentation and usage guide | Understanding available automation |
| `update_server_ip.ps1` | Automated IP updates (PowerShell)    | Changing server IP on Windows      |
| `update_server_ip.sh`  | Automated IP updates (Bash)          | Changing server IP on Linux/macOS  |

### **Migrations Directory** (`server/migrations/`)

| File                             | Purpose                              | When to Use                                   |
| -------------------------------- | ------------------------------------ | --------------------------------------------- |
| `README.md`                      | Database migration instructions      | Applying schema updates to existing databases |
| `001_add_unique_constraints.sql` | Add duplicate prevention constraints | Database migration step 1                     |
| `002_add_client_ids.sql`         | Add UUID columns                     | Database migration step 2                     |

## 🚀 **Common Scenarios - Where to Go**

### **"I need to deploy the server to EC2"**

1. **With duplicate prevention** (recommended): → `server/DUPLICATE_PREVENTION_DEPLOYMENT.md`
2. **Basic deployment**: → `server/EC2_DEPLOYMENT_GUIDE.md`

### **"I need to change the server IP address"**

→ `scripts/README.md` (for automated updates)  
→ `SERVER_CONFIG_GUIDE.md` (for manual process)

### **"I need to understand the database schema"**

→ `server/DATABASE_SCHEMA.md`

### **"I need to update an existing database"**

→ `server/migrations/README.md`

### **"I need to monitor the server or understand the API"**

→ `server/README.md`

### **"I can't connect to EC2 via SSH"**

→ `server/ssh_instructions.md`

## 🧹 **Files to Ignore/Remove**

### **Deprecated Files** (can be safely removed):

- `SERVER_IP_UPDATE_GUIDE.md` - Replaced by SERVER_CONFIG_GUIDE.md
- `server_setup_guide.md` - Replaced by server/EC2_DEPLOYMENT_GUIDE.md

### **Reference Files** (keep for historical reference):

- `DUPLICATE_PREVENTION_READY.md` - Summary of implementation

## 📖 **Recommended Reading Order**

### **For New Setup**:

1. `server/README.md` - Understand the server
2. `server/DATABASE_SCHEMA.md` - Understand the data
3. `server/DUPLICATE_PREVENTION_DEPLOYMENT.md` - Deploy to EC2

### **For Ongoing Maintenance**:

1. `scripts/README.md` - Automated server configuration changes
2. `SERVER_CONFIG_GUIDE.md` - Manual server configuration changes
3. `server/migrations/README.md` - Database updates
4. `server/README.md` - Monitoring and troubleshooting

---

**Last Updated**: August 2, 2025  
**Status**: All documentation current and consolidated
