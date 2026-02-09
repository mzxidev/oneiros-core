package io.oneiros.security;

/**
 * Exception thrown when key provider operations fail.
 *
 * @since 0.4.2
 */
public class KeyProviderException extends RuntimeException {

    private final String providerId;
    private final String operation;

    public KeyProviderException(String message) {
        super(message);
        this.providerId = null;
        this.operation = null;
    }

    public KeyProviderException(String message, Throwable cause) {
        super(message, cause);
        this.providerId = null;
        this.operation = null;
    }

    public KeyProviderException(String providerId, String operation, String message) {
        super(String.format("[%s] %s failed: %s", providerId, operation, message));
        this.providerId = providerId;
        this.operation = operation;
    }

    public KeyProviderException(String providerId, String operation, String message, Throwable cause) {
        super(String.format("[%s] %s failed: %s", providerId, operation, message), cause);
        this.providerId = providerId;
        this.operation = operation;
    }

    public String getProviderId() {
        return providerId;
    }

    public String getOperation() {
        return operation;
    }
}

