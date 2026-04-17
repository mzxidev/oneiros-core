package io.oneiros.core;

import io.oneiros.client.OneirosClient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OneirosKernelTest {

    @Test
    void testKernelAndEssenceInitialization() {
        // Create mock configs for two different databases
        OneirosConfig config1 = OneirosConfig.builder()
            .url("ws://localhost:8000/rpc")
            .database("db1")
            .username("root")
            .password("root")
            .autoConnect(false) // Don't try to connect in unit test
            .build();
            
        OneirosConfig config2 = OneirosConfig.builder()
            .url("ws://localhost:8000/rpc")
            .database("db2")
            .username("root")
            .password("root")
            .autoConnect(false)
            .build();

        SurrealEssence surreal1 = new SurrealEssence(config1);
        SurrealEssence surreal2 = new SurrealEssence(config2);
        
        MultiDatabaseManager dbManager = new MultiDatabaseManager();
        dbManager.addDatabase("primary", surreal1);
        dbManager.addDatabase("secondary", surreal2);

        // Build Kernel
        OneirosKernel kernel = OneirosKernel.builder()
            .addEssence(MultiDatabaseManager.class, dbManager)
            .addEssence(SurrealEssence.class, surreal1) // Note: surreal1 is registered as the "main" SurrealEssence
            .build();

        // Verify registration
        assertNotNull(kernel.use(MultiDatabaseManager.class));
        assertNotNull(kernel.use(SurrealEssence.class));

        // Verify Multi-DB routing
        MultiDatabaseManager manager = kernel.use(MultiDatabaseManager.class);
        assertEquals("db.surreal.db1", manager.db("primary").getId());
        assertEquals("db.surreal.db2", manager.db("secondary").getId());
        
        kernel.shutdown();
    }
}
