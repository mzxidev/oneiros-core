package io.oneiros.security;

/**
 * Thrown when encryption of an entity fails critically.
 *
 * <p>This exception is intentionally unchecked (extends {@link RuntimeException})
 * so it propagates through the call stack without forcing callers to declare it.
 * It is thrown instead of silently returning unencrypted data, which would be a
 * security leak.
 *
 * @see OneirosSecurityHandler
 */
public class EncryptionFailedException extends RuntimeException {

    /**
     * Creates a new {@code EncryptionFailedException} with a detail message.
     *
     * @param message the detail message
     */
    public EncryptionFailedException(String message) {
        super(message);
    }

    /**
     * Creates a new {@code EncryptionFailedException} with a detail message and cause.
     *
     * @param message the detail message
     * @param cause   the underlying cause
     */
    public EncryptionFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
