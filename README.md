# OneSecClone Research App

A mobile research application for studying digital behavior and app usage patterns.

## 📱 **Project Overview**

OneSecClone is an Android research app that:

- Tracks app usage sessions using Android's UsageStatsManager
- Implements intervention mechanisms (video interruptions)
- Collects device status and usage analytics
- Sends data to a secure research server for analysis

## 🏗️ **Architecture**

- **Android App**: Kotlin-based with UsageStatsManager API
- **Server**: Node.js/Express with PostgreSQL database
- **Database**: 4-table schema with duplicate prevention
- **Deployment**: AWS EC2 production environment

## 📚 **Documentation**

**🎯 Start Here**: [`DOCUMENTATION_INDEX.md`](DOCUMENTATION_INDEX.md) - Complete guide to all documentation

### **Quick Links**:

- **Deploy to EC2**: `server/DUPLICATE_PREVENTION_DEPLOYMENT.md`
- **Server Configuration**: `SERVER_CONFIG_GUIDE.md`
- **Database Schema**: `server/DATABASE_SCHEMA.md`
- **API Reference**: `server/README.md`

## 🚀 **Current Status**

- ✅ **Server Running**: `44.247.94.119:8080`
- ✅ **Database**: PostgreSQL with duplicate prevention
- ✅ **Mobile App**: Usage tracking and intervention system
- ✅ **Data Integrity**: Three-layer duplicate prevention (Aug 2, 2025)

## 🛠️ **Development**

### **Android App**

```bash
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

### **Server**

```bash
cd server
npm install
node server.js
```

## 📞 **Support**

For deployment and configuration help, see:

1. `DOCUMENTATION_INDEX.md` - Find the right guide for your task
2. `server/README.md` - Server troubleshooting and monitoring
3. `SERVER_CONFIG_GUIDE.md` - Configuration changes and IP updates

---

**Research Project**: Digital Behavior Study  
**Last Updated**: August 2, 2025  
**Repository**: [b-zapata/ds-research-mobile](https://github.com/b-zapata/ds-research-mobile)
