# 🚀 Oneiros v0.2.1 - Auto-Connect & Health Monitoring

## 📋 Summary

This release adds automatic connection establishment on startup and comprehensive health monitoring capabilities to prevent connection issues in production environments.

## ✨ New Features

### 1. Auto-Connect on Startup ✅

**Problem Solved:** Previously, connections were established lazily on first request, leading to:
- Silent failures if configuration was wrong
- Unexpected delays on first API call
- Difficult debugging in production

**Solution:** New `auto-connect` property (default: `true`) establishes connection immediately on application startup.

```yaml
oneiros:
  auto-connect: true  # Connect immediately (recommended)
```

**Benefits:**
- ✅ Fail fast on startup if configuration is wrong
- ✅ Connection ready immediately for first request
- ✅ Clear logging of connection status
- ✅ Easier debugging and troubleshooting

### 2. Spring Boot Actuator Integration 🏥

New health indicator provides real-time connection status:

**Endpoint:**
```bash
curl http://localhost:8080/actuator/health
```

**Response:**
```json
{
  "status": "UP",
  "components": {
    "oneirosHealthIndicator": {
      "status": "UP",
      "details": {
        "status": "Connected",
        "type": "OneirosWebsocketClient",
        "pool": {
          "total": 5,
          "healthy": 5,
          "unhealthy": 0,
          "maxSize": 5,
          "healthPercentage": 100.0
        }
      }
    }
  }
}
```

### 3. Startup Status Logger 📊

Beautiful colored console output on application startup:

```
═══════════════════════════════════════════════════════════
              🌊 ONEIROS DATABASE CLIENT 🌊
═══════════════════════════════════════════════════════════
   URL:        ws://localhost:8000/rpc
   Namespace:  my_namespace
   Database:   my_database
   Username:   root
   Status:     ✅ CONNECTED
   Pool:       5/5 connections healthy
               100.0% health rate
═══════════════════════════════════════════════════════════
```

If connection fails:
```
═══════════════════════════════════════════════════════════
              🌊 ONEIROS DATABASE CLIENT 🌊
═══════════════════════════════════════════════════════════
   URL:        ws://localhost:8000/rpc
   Namespace:  my_namespace
   Database:   my_database
   Username:   root
   Status:     ❌ DISCONNECTED
═══════════════════════════════════════════════════════════

⚠️ Oneiros is NOT connected to SurrealDB!
   Please check your configuration:
   - Is SurrealDB running at ws://localhost:8000/rpc?
   - Are credentials correct (user: root)?
   - Does namespace 'my_namespace' and database 'my_database' exist?
```

### 4. Enhanced Connection Pool Stats 📈

New `getStats()` method on `OneirosConnectionPool`:

```java
OneirosConnectionPool.PoolStats stats = pool.getStats();
System.out.println("Healthy connections: " + stats.healthy());
System.out.println("Health rate: " + stats.healthPercentage() + "%");
```

## 🔧 API Changes

### New Methods

```java
// OneirosClient interface
public interface OneirosClient {
    boolean isConnected();  // ✨ NEW
    // ...existing methods...
}
```

### New Configuration Properties

```yaml
oneiros:
  auto-connect: true  # ✨ NEW - Connect on startup (default: true)
```

## 📦 Dependencies

Added Spring Boot Actuator for health monitoring:

```gradle
dependencies {
    api 'org.springframework.boot:spring-boot-starter-actuator:4.1.0-M1'
}
```

## 🔄 Migration Guide

### From v0.2.0 to v0.2.1

No breaking changes! Just update your dependency:

```gradle
dependencies {
    implementation 'com.github.mzxidev:oneiros-core:v0.2.1'
}
```

**Optional:** Enable health endpoint in production:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info
  endpoint:
    health:
      show-details: always
```

## 🐛 Bug Fixes

- Fixed: Connection not established in Spring Boot applications using library as local JAR
- Fixed: Silent connection failures not visible until first query
- Fixed: Missing connection status visibility in logs
- Improved: Error messages now more descriptive and actionable

## 📚 Documentation

- ✅ Updated README.md with auto-connect configuration
- ✅ Added comprehensive troubleshooting section
- ✅ Added health monitoring examples
- ✅ Added connection mode comparison

## 🎯 Recommended Configuration

For production applications:

```yaml
spring:
  application:
    name: my-app
  main:
    web-application-type: reactive

oneiros:
  url: "ws://surrealdb:8000/rpc"
  namespace: "production"
  database: "main"
  username: "${SURREAL_USER}"
  password: "${SURREAL_PASS}"
  auto-connect: true  # Fail fast if DB unavailable
  
  connection-pool:
    enabled: true
    size: 10
    health-check-interval-seconds: 30
    reconnect-delay-seconds: 5
  
  security:
    enabled: true
    key: "${ENCRYPTION_KEY}"

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
  endpoint:
    health:
      show-details: when-authorized

logging:
  level:
    io.oneiros: INFO
```

## 🚀 What's Next?

Upcoming in v0.3.0:
- 🔄 Automatic schema migration
- 📊 Graph relation API improvements
- 🎯 Query performance metrics
- 🔐 JWT authentication support
- 📝 Query result caching improvements

---

**Full Changelog:** https://github.com/mzxidev/oneiros-core/compare/v0.2.0...v0.2.1

**Contributors:** @mzxidev

**Questions?** Open an issue on GitHub!
