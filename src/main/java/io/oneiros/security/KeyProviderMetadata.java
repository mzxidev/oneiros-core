package io.oneiros.security;

import java.time.Instant;
import java.util.Map;

/**
 * Metadata about a key provider for auditing and monitoring.
 *
 * @since 0.4.2
 */
public record KeyProviderMetadata(
        /**
         * Type of key provider (e.g., "IN_MEMORY", "AWS_KMS", "AZURE_KEY_VAULT", "HSM")
         */
        String providerType,

        /**
         * Provider implementation class name
         */
        String providerClass,

        /**
         * Region or location (for cloud providers)
         */
        String region,

        /**
         * When the current key was created/activated
         */
        Instant keyCreatedAt,

        /**
         * When the key will expire (if applicable)
         */
        Instant keyExpiresAt,

        /**
         * Additional provider-specific metadata
         */
        Map<String, String> additionalInfo
) {
    /**
     * Creates metadata for an in-memory key provider.
     */
    public static KeyProviderMetadata inMemory() {
        return new KeyProviderMetadata(
                "IN_MEMORY",
                InMemoryKeyProvider.class.getName(),
                "local",
                Instant.now(),
                null,
                Map.of(
                        "storage", "JVM_HEAP",
                        "security_note", "Key stored in application memory - suitable for development and standard applications"
                )
        );
    }

    /**
     * Creates metadata for an environment variable key provider.
     */
    public static KeyProviderMetadata environment(String envVarName) {
        return new KeyProviderMetadata(
                "ENVIRONMENT",
                EnvironmentKeyProvider.class.getName(),
                "local",
                Instant.now(),
                null,
                Map.of(
                        "storage", "ENVIRONMENT_VARIABLE",
                        "env_var", envVarName,
                        "security_note", "Key loaded from environment variable"
                )
        );
    }

    /**
     * Builder for creating custom metadata.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String providerType = "CUSTOM";
        private String providerClass;
        private String region;
        private Instant keyCreatedAt;
        private Instant keyExpiresAt;
        private Map<String, String> additionalInfo = Map.of();

        public Builder providerType(String providerType) {
            this.providerType = providerType;
            return this;
        }

        public Builder providerClass(Class<?> clazz) {
            this.providerClass = clazz.getName();
            return this;
        }

        public Builder region(String region) {
            this.region = region;
            return this;
        }

        public Builder keyCreatedAt(Instant keyCreatedAt) {
            this.keyCreatedAt = keyCreatedAt;
            return this;
        }

        public Builder keyExpiresAt(Instant keyExpiresAt) {
            this.keyExpiresAt = keyExpiresAt;
            return this;
        }

        public Builder additionalInfo(Map<String, String> additionalInfo) {
            this.additionalInfo = additionalInfo;
            return this;
        }

        public KeyProviderMetadata build() {
            return new KeyProviderMetadata(
                    providerType,
                    providerClass,
                    region,
                    keyCreatedAt,
                    keyExpiresAt,
                    additionalInfo
            );
        }
    }
}

