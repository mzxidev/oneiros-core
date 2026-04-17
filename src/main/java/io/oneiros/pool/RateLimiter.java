package io.oneiros.pool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Token bucket rate limiter for controlling request throughput.
 *
 * <p>
 * Implements the token bucket algorithm:
 * <ul>
 * <li>Bucket has a maximum capacity of tokens</li>
 * <li>Tokens are added at a fixed rate</li>
 * <li>Each request consumes one token</li>
 * <li>If no tokens available, request is throttled</li>
 * </ul>
 *
 * <h3>Use Cases</h3>
 * <ul>
 * <li>Prevent connection pool overload</li>
 * <li>Protect against DDoS attacks</li>
 * <li>Fair resource allocation</li>
 * <li>Cost control (cloud pricing)</li>
 * </ul>
 *
 * <h3>Example</h3>
 * 
 * <pre>{@code
 * // Allow 100 requests per second
 * RateLimiter limiter = new RateLimiter(100, Duration.ofSeconds(1));
 *
 * if (limiter.tryAcquire()) {
 *     // Execute request
 * } else {
 *     // Throttled - retry later or reject
 *     throw new RateLimitExceededException();
 * }
 * }</pre>
 *
 * @since 0.4.3
 */
public class RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RateLimiter.class);

    private final int maxTokens;
    private final long refillIntervalNanos;
    private final AtomicInteger availableTokens;
    private final AtomicLong lastRefillTime;

    /**
     * Creates a rate limiter with specified capacity and refill rate.
     *
     * @param maxTokens      maximum number of tokens in the bucket
     * @param refillInterval interval at which tokens are refilled
     */
    public RateLimiter(int maxTokens, Duration refillInterval) {
        if (maxTokens <= 0) {
            throw new IllegalArgumentException("maxTokens must be positive");
        }
        if (refillInterval.isZero() || refillInterval.isNegative()) {
            throw new IllegalArgumentException("refillInterval must be positive");
        }

        this.maxTokens = maxTokens;
        this.refillIntervalNanos = refillInterval.toNanos();
        this.availableTokens = new AtomicInteger(maxTokens);
        this.lastRefillTime = new AtomicLong(System.nanoTime());

        log.debug("🚦 RateLimiter initialized: {} tokens per {}", maxTokens, refillInterval);
    }

    /**
     * Attempts to acquire a token.
     *
     * <p>
     * Returns immediately without blocking. If no tokens are available,
     * returns false and the caller should handle rate limiting (retry, reject,
     * etc.).
     *
     * @return true if token was acquired, false if rate limit exceeded
     */
    public boolean tryAcquire() {
        refillTokens();

        // SECURITY FIX: Iterative CAS loop instead of recursive call
        // Prevents StackOverflowError under extreme contention
        while (true) {
            int current = availableTokens.get();
            if (current <= 0) {
                log.debug("🔴 Rate limit exceeded (0 tokens available)");
                return false;
            }
            if (availableTokens.compareAndSet(current, current - 1)) {
                log.trace("🟢 Token acquired ({} remaining)", current - 1);
                return true;
            }
            // CAS failed due to contention, retry immediately (iterative, not recursive)
        }
    }

    /**
     * Attempts to acquire multiple tokens atomically.
     *
     * @param tokens number of tokens to acquire
     * @return true if all tokens were acquired, false otherwise
     */
    public boolean tryAcquire(int tokens) {
        if (tokens <= 0) {
            throw new IllegalArgumentException("tokens must be positive");
        }
        if (tokens > maxTokens) {
            return false; // Can never succeed
        }

        refillTokens();

        // SECURITY FIX: Iterative CAS loop instead of recursive call
        while (true) {
            int current = availableTokens.get();
            if (current < tokens) {
                log.debug("🔴 Rate limit exceeded (requested: {}, available: {})", tokens, current);
                return false;
            }
            if (availableTokens.compareAndSet(current, current - tokens)) {
                log.trace("🟢 {} tokens acquired ({} remaining)", tokens, current - tokens);
                return true;
            }
            // CAS failed due to contention, retry immediately
        }
    }

    /**
     * Returns a token to the bucket (e.g., after failed operation).
     *
     * <p>
     * Useful for compensating when an operation fails before consuming resources.
     */
    public void release() {
        int current = availableTokens.get();
        if (current < maxTokens) {
            availableTokens.compareAndSet(current, Math.min(current + 1, maxTokens));
            log.trace("🟡 Token released ({} available)", availableTokens.get());
        }
    }

    /**
     * Refills tokens based on elapsed time since last refill.
     *
     * <p>
     * Uses nano-precision timing for accurate token distribution.
     */
    private void refillTokens() {
        long now = System.nanoTime();
        long lastRefill = lastRefillTime.get();
        long elapsed = now - lastRefill;

        if (elapsed >= refillIntervalNanos) {
            // Calculate how many tokens to add
            long intervals = elapsed / refillIntervalNanos;
            int tokensToAdd = (int) Math.min(intervals, maxTokens);

            if (tokensToAdd > 0) {
                int current = availableTokens.get();
                int newValue = Math.min(current + tokensToAdd, maxTokens);

                if (availableTokens.compareAndSet(current, newValue)) {
                    lastRefillTime.compareAndSet(lastRefill, now);
                    log.trace("🔄 Refilled {} tokens (total: {})", tokensToAdd, newValue);
                }
            }
        }
    }

    /**
     * Returns current number of available tokens.
     */
    public int getAvailableTokens() {
        refillTokens();
        return availableTokens.get();
    }

    /**
     * Returns maximum token capacity.
     */
    public int getMaxTokens() {
        return maxTokens;
    }

    /**
     * Resets the rate limiter to full capacity.
     */
    public void reset() {
        availableTokens.set(maxTokens);
        lastRefillTime.set(System.nanoTime());
        log.debug("🔄 RateLimiter reset to full capacity");
    }

    /**
     * Returns current usage as percentage (0-100).
     */
    public double getUsagePercentage() {
        return 100.0 * (maxTokens - getAvailableTokens()) / maxTokens;
    }
}
