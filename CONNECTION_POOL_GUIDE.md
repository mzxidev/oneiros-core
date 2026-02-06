# 🏊 Connection Pool Configuration Guide

## Overview

Oneiros provides a **connection pooling system** that manages multiple WebSocket connections to SurrealDB for improved performance, load balancing, and resilience.

---

## 📋 Configuration

### Enable Connection Pool

Add to your `application.yml`:

```yaml
oneiros:
  # Enable connection pool (default: false)
  pool:
    enabled: true
    size: 10                    # Number of connections in pool (default: 5)
    auto-reconnect: true        # Auto-reconnect on failure (default: true)
    health-check-interval: 30   # Health check interval in seconds (default: 30)
```

### Standard Configuration (Single Connection)

```yaml
oneiros:
  url: ws://localhost:8000/rpc
  namespace: test
  database: test
  username: root
  password: root
  auto-connect: true  # Auto-connect on startup (recommended)
  
  # Pool is disabled by default
  pool:
    enabled: false
```

---

## 🚀 When to Use Connection Pool

### ✅ Use Connection Pool When:

1. **High Traffic Applications**
   - E-commerce platforms
   - Real-time dashboards
   - Social media apps

2. **Concurrent Users**
   - Multiple simultaneous requests
   - WebSocket heavy operations
   - Live query subscriptions

3. **Production Environments**
   - Load balancing needed
   - Resilience required
   - High availability systems

### ❌ Skip Connection Pool When:

1. **Development/Testing**
   - Local development
   - Testing single features
   - Debugging queries

2. **Low Traffic Applications**
   - Personal projects
   - Internal tools
   - Prototypes

3. **Resource Constraints**
   - Limited server resources
   - Small applications
   - Edge devices

---

## 📊 Pool Statistics

### Check Pool Health

Inject `OneirosConnectionPool` and call `getStats()`:

```java
@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final OneirosConnectionPool pool;

    @GetMapping("/pool")
    public PoolStats getPoolStats() {
        return pool.getStats();
    }
}
```

### Response Example

```json
{
  "total": 10,
  "healthy": 9,
  "unhealthy": 1,
  "maxSize": 10,
  "healthPercentage": 90.0
}
```

---

## ⚙️ Advanced Configuration

### Custom Pool Size

```yaml
oneiros:
  pool:
    enabled: true
    size: 20  # For high-traffic applications
```

**Recommendations:**
- **Small Apps:** 3-5 connections
- **Medium Apps:** 5-10 connections
- **Large Apps:** 10-20 connections
- **Enterprise:** 20-50 connections

### Health Check Interval

```yaml
oneiros:
  pool:
    health-check-interval: 60  # Check every 60 seconds
```

**Recommendations:**
- **Critical Systems:** 15-30 seconds
- **Standard Apps:** 30-60 seconds
- **Low Priority:** 60-120 seconds

---

## 🔧 Load Balancing

The pool uses **round-robin load balancing** to distribute queries across connections:

```
Request 1 → Connection #1
Request 2 → Connection #2
Request 3 → Connection #3
Request 4 → Connection #1  (loops back)
```

### Benefits:

✅ **Even distribution** of load  
✅ **No single point of failure**  
✅ **Automatic failover** to healthy connections  
✅ **Zero configuration** required  

---

## 🛡️ Resilience Features

### Circuit Breaker Integration

The pool integrates with **Resilience4j Circuit Breaker**:

```yaml
resilience4j:
  circuitbreaker:
    configs:
      default:
        failure-rate-threshold: 50
        wait-duration-in-open-state: 5s
```

### Automatic Recovery

If a connection fails:
1. ❌ Marked as **unhealthy**
2. 🔄 **Auto-reconnect** attempted
3. ✅ Restored to pool when recovered
4. 🆕 Replaced if recovery fails

---

## 🧪 Testing Configuration

### Test Connection Pool Initialization

```java
@SpringBootTest
class ConnectionPoolTest {

    @Autowired
    private OneirosConnectionPool pool;

    @Test
    void poolShouldInitialize() {
        // Check pool is connected
        assertTrue(pool.isConnected());
        
        // Verify pool size
        PoolStats stats = pool.getStats();
        assertEquals(10, stats.total());
        assertTrue(stats.healthy() > 0);
    }
}
```

---

## 📈 Performance Monitoring

### Log Output

When pool is enabled, you'll see:

```
🏊 Initializing Oneiros Connection Pool
   📊 Pool size: 10
   🔄 Auto-reconnect: true
   ❤️ Health check interval: 30s
✅ Connection #1/10 added to pool
✅ Connection #2/10 added to pool
...
🏊 Connection pool initialized with 10/10 connections (100% success rate)
```

### Health Check Logs

Every 30 seconds (configurable):

```
🏥 Performing health check on 10 connections
✅ All connections healthy
```

Or if issues detected:

```
⚠️ Connection unhealthy, will attempt recovery
🔄 Attempting to reconnect unhealthy connection
✅ Connection recovered
```

---

## 🚨 Troubleshooting

### Error: "No connections available in pool"

**Cause:** Pool initialization failed or all connections unhealthy.

**Solutions:**

1. **Check SurrealDB is running:**
   ```bash
   surreal start --user root --pass root
   ```

2. **Verify connection settings:**
   ```yaml
   oneiros:
     url: ws://localhost:8000/rpc  # Correct URL?
     username: root                 # Valid credentials?
     password: root
   ```

3. **Enable debug logging:**
   ```yaml
   logging:
     level:
       io.oneiros: DEBUG
   ```

4. **Reduce pool size temporarily:**
   ```yaml
   oneiros:
     pool:
       size: 3  # Try with fewer connections
   ```

### Error: Pool initializes with fewer connections

**Example:**
```
🏊 Connection pool initialized with 7/10 connections (70% success rate)
⚠️ Pool initialized with fewer connections than requested.
```

**Cause:** Some connections failed to establish.

**Impact:** Pool still works with available connections.

**Solutions:**
- Check SurrealDB server capacity
- Reduce `pool.size` to match server capabilities
- Check network stability

---

## 💡 Best Practices

### 1. Always Enable in Production

```yaml
# ✅ GOOD
spring:
  profiles:
    active: prod

oneiros:
  pool:
    enabled: true
    size: 10
```

### 2. Start Small, Scale Up

```yaml
# Start with:
oneiros:
  pool:
    size: 5

# Monitor and increase if needed:
oneiros:
  pool:
    size: 15
```

### 3. Monitor Pool Health

```java
@Scheduled(fixedRate = 60000) // Every minute
public void logPoolHealth() {
    PoolStats stats = pool.getStats();
    log.info("Pool Health: {}/{} healthy ({}%)",
        stats.healthy(), stats.total(), stats.healthPercentage());
}
```

### 4. Handle Connection Failures Gracefully

```java
public Flux<Product> findAll() {
    return OneirosQuery.select(Product.class)
        .execute(client)
        .onErrorResume(error -> {
            log.error("Query failed: {}", error.getMessage());
            return Flux.empty(); // Return empty result instead of crashing
        });
}
```

---

## 📚 Examples

### Complete Production Configuration

```yaml
# application-prod.yml
oneiros:
  # Connection details
  url: ws://surrealdb.prod.example.com:8000/rpc
  namespace: production
  database: main
  username: ${SURREAL_USER}
  password: ${SURREAL_PASS}
  auto-connect: true

  # Connection pool
  pool:
    enabled: true
    size: 15
    auto-reconnect: true
    health-check-interval: 30

  # Security
  encryption:
    enabled: true
    key: ${ENCRYPTION_KEY}

# Resilience
resilience4j:
  circuitbreaker:
    configs:
      default:
        failure-rate-threshold: 50
        wait-duration-in-open-state: 10s
        sliding-window-size: 20

# Logging
logging:
  level:
    io.oneiros: INFO
```

---

## 🎯 Summary

| Feature | Single Connection | Connection Pool |
|---------|------------------|-----------------|
| **Setup Complexity** | ✅ Simple | ⚙️ Moderate |
| **Performance** | 🐢 Good | 🚀 Excellent |
| **Scalability** | 📊 Limited | 📈 High |
| **Resilience** | ⚠️ Basic | 🛡️ Advanced |
| **Load Balancing** | ❌ No | ✅ Yes |
| **Auto-Recovery** | 🔄 Basic | 🔧 Advanced |
| **Best For** | Dev/Test | Production |

---

## 🔗 Related Documentation

- [README.md](README.md) - Main documentation
- [QUICK_START.md](QUICK_START.md) - Getting started guide
- [ADVANCED_FEATURES.md](ADVANCED_FEATURES.md) - Advanced features
- [REALTIME_FEATURES.md](REALTIME_FEATURES.md) - Live queries and real-time updates
