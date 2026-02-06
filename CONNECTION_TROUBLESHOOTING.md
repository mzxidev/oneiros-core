# 🔧 Connection Troubleshooting Guide

## Common Connection Issues and Solutions

### Issue 1: "Connection has been closed BEFORE send operation"

**Symptom:**
```
reactor.netty.channel.AbortedException: Connection has been closed BEFORE send operation
```

**Causes:**
1. WebSocket connections being closed prematurely
2. Connection pool initialized before connections are fully established
3. Queries executed before pool is ready

**Solutions:**

✅ **Ensure proper configuration:**
```yaml
oneiros:
  pool:
    enabled: true
    size: 10
    health-check-interval: 60  # Don't check too frequently during startup
```

✅ **Wait for application startup:**
- The connection pool initializes asynchronously
- Wait a few seconds after application startup before making queries
- Check logs for "✅ Connection pool initialized with X connections"

✅ **Check SurrealDB is running:**
```bash
# Test SurrealDB connection
curl http://localhost:8000/health
```

---

### Issue 2: All Connections Unhealthy After Startup

**Symptom:**
```
⚠️ Connection unhealthy, will attempt recovery
PoolStats[total=10, healthy=0, unhealthy=10, maxSize=10]
```

**Causes:**
1. Connections closed immediately after initialization
2. Health check running before connections are fully ready
3. Authentication or namespace/database selection failed

**Solutions:**

✅ **Check your configuration:**
```yaml
oneiros:
  url: "ws://127.0.0.1:8000/rpc"  # Correct WebSocket URL?
  username: "root"                 # Correct credentials?
  password: "root"
  namespace: "your_namespace"      # Does namespace exist?
  database: "your_database"        # Does database exist?
```

✅ **Verify SurrealDB setup:**
```bash
# Start SurrealDB with defined namespace/database
surreal start --user root --pass root --bind 0.0.0.0:8000

# Or create namespace/database manually
surreal sql --conn http://localhost:8000 --user root --pass root --ns your_namespace --db your_database
```

✅ **Increase health check interval:**
```yaml
oneiros:
  pool:
    health-check-interval: 120  # Give connections time to stabilize
```

---

### Issue 3: "No connections available in pool"

**Symptom:**
```
java.lang.IllegalStateException: No connections available in pool
```

**Causes:**
1. All connections failed to initialize
2. All connections became unhealthy
3. Circuit breaker opened due to repeated failures

**Solutions:**

✅ **Check circuit breaker status:**
```java
@Autowired
private CircuitBreaker circuitBreaker;

public void checkStatus() {
    log.info("Circuit Breaker State: {}", circuitBreaker.getState());
    // CLOSED = normal, OPEN = blocking calls, HALF_OPEN = testing
}
```

✅ **Wait for circuit breaker to close:**
- Circuit breaker automatically transitions: OPEN → HALF_OPEN → CLOSED
- Default wait time: 30 seconds
- Configure in application.yml:
```yaml
resilience4j:
  circuitbreaker:
    instances:
      oneiros-shield:
        wait-duration-in-open-state: 10s
```

✅ **Check pool stats:**
```java
@Autowired
private OneirosConnectionPool pool;

public void checkPoolHealth() {
    PoolStats stats = pool.getStats();
    log.info("Pool Stats: {}", stats);
    // Expect: healthy > 0
}
```

---

### Issue 4: Migration Fails with Connection Errors

**Symptom:**
```
❌ Schema migration failed
reactor.netty.channel.AbortedException: Connection has been closed BEFORE send operation
```

**Causes:**
1. Migration engine running before pool is initialized
2. Connection closed during schema generation

**Solutions:**

✅ **Disable migrations during initial debugging:**
```yaml
oneiros:
  migration:
    enabled: false  # Temporarily disable to test connection
```

✅ **Run migrations manually after startup:**
```java
@Autowired
private OneirosMigrationEngine migrationEngine;

@EventListener(ApplicationReadyEvent.class)
public void runMigrationsLater() {
    // Wait for pool to stabilize
    Mono.delay(Duration.ofSeconds(5))
        .then(migrationEngine.migrate())
        .subscribe();
}
```

---

### Issue 5: Slow Application Startup

**Symptom:**
- Application takes 15+ seconds to start
- Many timeout errors in logs

**Causes:**
1. Connection pool trying to establish too many connections
2. SurrealDB not responding quickly
3. Network latency

**Solutions:**

✅ **Reduce pool size:**
```yaml
oneiros:
  pool:
    enabled: true
    size: 3  # Start with fewer connections
```

✅ **Increase timeouts:**
```yaml
oneiros:
  connect-timeout: 30s  # Default is 15s
```

✅ **Use lazy initialization:**
```yaml
oneiros:
  auto-connect: false  # Connect on first query instead of startup
```

---

## Debugging Tips

### 1. Enable Debug Logging

```yaml
logging:
  level:
    io.oneiros: DEBUG
    reactor.netty: DEBUG
```

### 2. Monitor Connection Health

```java
@Component
public class PoolHealthMonitor {
    
    private final OneirosConnectionPool pool;
    
    @Scheduled(fixedRate = 30000)
    public void logPoolStats() {
        if (pool != null) {
            PoolStats stats = pool.getStats();
            log.info("📊 Pool Health: total={}, healthy={}, unhealthy={}", 
                     stats.total(), stats.healthy(), stats.unhealthy());
        }
    }
}
```

### 3. Test Connection Manually

```java
@RestController
public class HealthController {
    
    @Autowired
    private OneirosClient client;
    
    @GetMapping("/test-db")
    public Mono<String> testConnection() {
        return OneirosQuery.select(Object.class)
            .sql("SELECT 1 as test")
            .execute(client)
            .map(result -> "✅ Database connection OK: " + result)
            .defaultIfEmpty("⚠️ No result returned")
            .onErrorResume(e -> Mono.just("❌ Connection failed: " + e.getMessage()));
    }
}
```

---

## Best Practices

### ✅ Production Checklist

- [ ] Connection pool enabled with appropriate size (5-10 for most apps)
- [ ] Health check interval set to 60+ seconds
- [ ] Circuit breaker enabled and configured
- [ ] Encryption key stored in environment variables (not in code)
- [ ] SurrealDB namespace and database pre-created
- [ ] Proper error handling in query code
- [ ] Actuator health endpoint exposed for monitoring

### 🚦 Connection Pool Guidelines

| Application Type | Pool Enabled | Pool Size | Auto-Reconnect |
|-----------------|--------------|-----------|----------------|
| Development     | false        | 1         | false          |
| Testing         | false        | 1         | false          |
| Production (Low Load) | true   | 3-5       | true           |
| Production (High Load) | true  | 10-15     | true           |
| Microservices   | true         | 5-10      | true           |

### 📊 Monitoring Recommendations

1. **Log Connection Events:**
   - Connection established/failed
   - Health check results
   - Pool statistics every 60 seconds

2. **Set Up Alerts:**
   - Alert when `healthy` connections drop below 50%
   - Alert when circuit breaker opens
   - Alert on repeated connection failures

3. **Use Spring Boot Actuator:**
   ```yaml
   management:
     endpoints:
       web:
         exposure:
           include: health,metrics,info
     health:
       show-details: always
   ```

---

## Need More Help?

If you're still experiencing issues:

1. Check GitHub Issues: https://github.com/mzxidev/oneiros-core/issues
2. Enable DEBUG logging and share logs
3. Provide your configuration (remove sensitive data!)
4. Include SurrealDB version and deployment method

**Quick Test Command:**
```bash
# Test if SurrealDB is accessible
curl -X POST http://localhost:8000/sql \
  -H "Accept: application/json" \
  -H "NS: test" \
  -H "DB: test" \
  -u "root:root" \
  -d "SELECT 1;"
```
