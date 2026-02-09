package io.oneiros.pool;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for RateLimiter.
 */
class RateLimiterTest {

    @Test
    @DisplayName("Should allow requests within limit")
    void shouldAllowRequestsWithinLimit() {
        RateLimiter limiter = new RateLimiter(10, Duration.ofSeconds(1));

        // Should allow 10 requests
        for (int i = 0; i < 10; i++) {
            assertTrue(limiter.tryAcquire(), "Request " + i + " should succeed");
        }

        // 11th request should be throttled
        assertFalse(limiter.tryAcquire(), "11th request should be throttled");
    }

    @Test
    @DisplayName("Should refill tokens over time")
    void shouldRefillTokensOverTime() throws InterruptedException {
        RateLimiter limiter = new RateLimiter(5, Duration.ofMillis(100));

        // Exhaust tokens
        for (int i = 0; i < 5; i++) {
            assertTrue(limiter.tryAcquire());
        }
        assertFalse(limiter.tryAcquire());

        // Wait for refill
        Thread.sleep(150);

        // Should have tokens again
        assertTrue(limiter.tryAcquire(), "Tokens should be refilled after interval");
    }

    @Test
    @DisplayName("Should acquire multiple tokens")
    void shouldAcquireMultipleTokens() {
        RateLimiter limiter = new RateLimiter(10, Duration.ofSeconds(1));

        assertTrue(limiter.tryAcquire(5));
        assertEquals(5, limiter.getAvailableTokens());

        assertTrue(limiter.tryAcquire(3));
        assertEquals(2, limiter.getAvailableTokens());

        assertFalse(limiter.tryAcquire(3), "Should not acquire more tokens than available");
    }

    @Test
    @DisplayName("Should release tokens")
    void shouldReleaseTokens() {
        RateLimiter limiter = new RateLimiter(5, Duration.ofSeconds(1));

        limiter.tryAcquire(5);
        assertEquals(0, limiter.getAvailableTokens());

        limiter.release();
        assertEquals(1, limiter.getAvailableTokens());

        limiter.release();
        limiter.release();
        assertEquals(3, limiter.getAvailableTokens());
    }

    @Test
    @DisplayName("Should not exceed max tokens on release")
    void shouldNotExceedMaxTokensOnRelease() {
        RateLimiter limiter = new RateLimiter(5, Duration.ofSeconds(1));

        // Release more tokens than consumed
        for (int i = 0; i < 10; i++) {
            limiter.release();
        }

        // Should cap at max
        assertEquals(5, limiter.getAvailableTokens());
    }

    @Test
    @DisplayName("Should reset to full capacity")
    void shouldResetToFullCapacity() {
        RateLimiter limiter = new RateLimiter(10, Duration.ofSeconds(1));

        limiter.tryAcquire(10);
        assertEquals(0, limiter.getAvailableTokens());

        limiter.reset();
        assertEquals(10, limiter.getAvailableTokens());
    }

    @Test
    @DisplayName("Should calculate usage percentage")
    void shouldCalculateUsagePercentage() {
        RateLimiter limiter = new RateLimiter(100, Duration.ofSeconds(1));

        assertEquals(0.0, limiter.getUsagePercentage(), 0.01);

        limiter.tryAcquire(50);
        assertEquals(50.0, limiter.getUsagePercentage(), 0.01);

        limiter.tryAcquire(25);
        assertEquals(75.0, limiter.getUsagePercentage(), 0.01);
    }

    @Test
    @DisplayName("Should handle concurrent requests")
    void shouldHandleConcurrentRequests() throws InterruptedException {
        RateLimiter limiter = new RateLimiter(100, Duration.ofSeconds(1));
        int threadCount = 10;
        int requestsPerThread = 15;

        Thread[] threads = new Thread[threadCount];
        int[] successCounts = new int[threadCount];

        for (int i = 0; i < threadCount; i++) {
            final int threadIndex = i;
            threads[i] = new Thread(() -> {
                for (int j = 0; j < requestsPerThread; j++) {
                    if (limiter.tryAcquire()) {
                        successCounts[threadIndex]++;
                    }
                }
            });
        }

        for (Thread thread : threads) {
            thread.start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        int totalSuccess = 0;
        for (int count : successCounts) {
            totalSuccess += count;
        }

        // Should allow exactly 100 requests
        assertEquals(100, totalSuccess);
    }

    @Test
    @DisplayName("Should throw on invalid configuration")
    void shouldThrowOnInvalidConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> {
            new RateLimiter(0, Duration.ofSeconds(1));
        });

        assertThrows(IllegalArgumentException.class, () -> {
            new RateLimiter(-1, Duration.ofSeconds(1));
        });

        assertThrows(IllegalArgumentException.class, () -> {
            new RateLimiter(10, Duration.ZERO);
        });

        assertThrows(IllegalArgumentException.class, () -> {
            new RateLimiter(10, Duration.ofSeconds(-1));
        });
    }
}
