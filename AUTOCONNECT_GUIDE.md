# 🚀 Oneiros Auto-Connect Quick Reference

## 🎯 Problem It Solves

**Before v0.2.1:**
```
✅ Application starts successfully
❓ No indication if DB connection works
⏰ First API request hangs or fails
😱 Production issue discovered too late
```

**After v0.2.1:**
```
✅ Application starts
🔍 Connection verified immediately
✅ or ❌ Clear status in startup logs
🎯 Issues discovered before traffic arrives
```

## ⚙️ Configuration

### Enable Auto-Connect (Default)

```yaml
oneiros:
  url: "ws://localhost:8000/rpc"
  username: "root"
  password: "root"
  namespace: "my_namespace"
  database: "my_database"
  auto-connect: true  # ✅ Connect on startup
```

### Disable Auto-Connect (Lazy Mode)

```yaml
oneiros:
  auto-connect: false  # ⏳ Connect on first request
```

## 📊 Monitoring

### 1. Startup Logs

Look for this in your console on startup:

✅ **Success:**
```
═══════════════════════════════════════════════════════════
              🌊 ONEIROS DATABASE CLIENT 🌊
═══════════════════════════════════════════════════════════
   URL:        ws://localhost:8000/rpc
   Namespace:  my_namespace
   Database:   my_database
   Username:   root
   Status:     ✅ CONNECTED
═══════════════════════════════════════════════════════════
```

❌ **Failure:**
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

### 2. Health Endpoint

Add Spring Boot Actuator:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health
```

Check status:
```bash
curl http://localhost:8080/actuator/health
```

Response:
```json
{
  "status": "UP",
  "components": {
    "oneirosHealthIndicator": {
      "status": "UP",
      "details": {
        "status": "Connected",
        "type": "OneirosWebsocketClient"
      }
    }
  }
}
```

### 3. Programmatic Check

```java
@Autowired
private OneirosClient client;

public boolean isDatabaseAvailable() {
    return client.isConnected();
}
```

## 🐛 Troubleshooting

### Issue 1: Application Won't Start

**Symptoms:**
```
❌ Oneiros connection failed: Connection refused
Application run failed
```

**Solutions:**

1. Check if SurrealDB is running:
   ```bash
   surreal start --user root --pass root
   ```

2. Verify configuration:
   ```yaml
   oneiros:
     url: "ws://127.0.0.1:8000/rpc"  # Check port!
     username: "root"
     password: "root"
   ```

3. Use lazy connect for development:
   ```yaml
   oneiros:
     auto-connect: false  # Application starts even if DB unavailable
   ```

### Issue 2: Works in Tests, Fails in Application

**Cause:** Configuration not loaded from `application.yml`

**Solution:**
```java
@SpringBootApplication
@ComponentScan(basePackages = {
    "your.package",
    "io.oneiros"  // ← Add this!
})
@EnableConfigurationProperties(OneirosProperties.class)
public class Application { }
```

### Issue 3: Connection Pool Shows Unhealthy

**Symptoms:**
```
Pool: 2/5 connections healthy
      40.0% health rate
```

**Solutions:**

1. Increase pool size:
   ```yaml
   oneiros:
     connection-pool:
       size: 10
   ```

2. Check SurrealDB logs for connection limit

3. Verify network stability

## 📋 Checklist for Production

- [ ] Set `auto-connect: true` (fail fast)
- [ ] Use environment variables for credentials
- [ ] Enable health endpoint
- [ ] Set up monitoring alerts on health endpoint
- [ ] Configure connection pool if high traffic
- [ ] Enable debug logging initially
- [ ] Test connection failure scenarios
- [ ] Document expected startup behavior

## 🔄 Recommended Profiles

### Development
```yaml
oneiros:
  url: "ws://localhost:8000/rpc"
  auto-connect: false  # Optional - allow dev without DB
  
logging:
  level:
    io.oneiros: DEBUG
```

### Staging/Production
```yaml
oneiros:
  url: "${SURREAL_URL}"
  username: "${SURREAL_USER}"
  password: "${SURREAL_PASS}"
  auto-connect: true  # Fail fast!
  
  connection-pool:
    enabled: true
    size: 10
  
management:
  endpoints:
    web:
      exposure:
        include: health,info
  endpoint:
    health:
      show-details: when-authorized

logging:
  level:
    io.oneiros: INFO
```

## 📞 Support

- **GitHub Issues:** https://github.com/mzxidev/oneiros-core/issues
- **Documentation:** https://github.com/mzxidev/oneiros-core/blob/main/README.md
- **Examples:** https://github.com/mzxidev/oneiros-core/tree/main/src/test/java/io/oneiros/test

---

**Version:** 0.2.1+  
**Last Updated:** 2026-02-06
