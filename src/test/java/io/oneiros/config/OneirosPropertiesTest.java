package io.oneiros.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class to verify Oneiros configuration properties are correctly bound.
 */
@SpringBootTest(classes = {OneirosProperties.class})
@EnableConfigurationProperties(OneirosProperties.class)
@TestPropertySource(properties = {
    "oneiros.url=ws://localhost:8000/rpc",
    "oneiros.namespace=test",
    "oneiros.database=test",
    "oneiros.username=root",
    "oneiros.password=root",
    "oneiros.auto-connect=true",
    "oneiros.security.enabled=true",
    "oneiros.security.key=test-key-must-be-16-chars",
    "oneiros.cache.enabled=true",
    "oneiros.cache.ttl-seconds=120",
    "oneiros.cache.max-size=5000",
    "oneiros.migration.enabled=true",
    "oneiros.migration.base-package=io.oneiros.test",
    "oneiros.migration.dry-run=true",
    "oneiros.pool.enabled=true",
    "oneiros.pool.size=10",
    "oneiros.pool.min-idle=3",
    "oneiros.pool.max-wait-seconds=60",
    "oneiros.pool.health-check-interval=45",
    "oneiros.pool.auto-reconnect=false"
})
public class OneirosPropertiesTest {

    @Autowired
    private OneirosProperties properties;

    @Test
    public void testBasicProperties() {
        assertEquals("ws://localhost:8000/rpc", properties.getUrl());
        assertEquals("test", properties.getNamespace());
        assertEquals("test", properties.getDatabase());
        assertEquals("root", properties.getUsername());
        assertEquals("root", properties.getPassword());
        assertTrue(properties.isAutoConnect());
    }

    @Test
    public void testSecurityProperties() {
        assertTrue(properties.getSecurity().isEnabled());
        assertEquals("test-key-must-be-16-chars", properties.getSecurity().getKey());
    }

    @Test
    public void testCacheProperties() {
        assertTrue(properties.getCache().isEnabled());
        assertEquals(120, properties.getCache().getTtlSeconds());
        assertEquals(5000, properties.getCache().getMaxSize());
    }

    @Test
    public void testMigrationProperties() {
        assertTrue(properties.getMigration().isEnabled());
        assertEquals("io.oneiros.test", properties.getMigration().getBasePackage());
        assertTrue(properties.getMigration().isDryRun());
    }

    @Test
    public void testPoolProperties() {
        assertTrue(properties.getPool().isEnabled());
        assertEquals(10, properties.getPool().getSize());
        assertEquals(3, properties.getPool().getMinIdle());
        assertEquals(60, properties.getPool().getMaxWaitSeconds());
        assertEquals(45, properties.getPool().getHealthCheckInterval());
        assertFalse(properties.getPool().isAutoReconnect());
    }
}
