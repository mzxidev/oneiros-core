package io.oneiros.pool;

/**
 * Exception thrown when rate limit is exceeded.
 *
 * @since 0.4.3
 */
public class RateLimitExceededException extends RuntimeException {

    private final int requestedTokens;
    private final int availableTokens;
    private final long retryAfterMillis;

    public RateLimitExceededException(String message) {
        super(message);
        this.requestedTokens = 1;
        this.availableTokens = 0;
        this.retryAfterMillis = 1000; // Default: 1 second
    }

    public RateLimitExceededException(String message, int requestedTokens, int availableTokens, long retryAfterMillis) {
        super(message);
        this.requestedTokens = requestedTokens;
        this.availableTokens = availableTokens;
        this.retryAfterMillis = retryAfterMillis;
    }

    public int getRequestedTokens() {
        return requestedTokens;
    }

    public int getAvailableTokens() {
        return availableTokens;
    }

    public long getRetryAfterMillis() {
        return retryAfterMillis;
    }

    @Override
    public String getMessage() {
        return String.format("%s (requested: %d, available: %d, retry after: %dms)",
                super.getMessage(), requestedTokens, availableTokens, retryAfterMillis);
    }
}
