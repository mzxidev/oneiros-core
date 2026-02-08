package io.oneiros.migration;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.oneiros.migration.serializer.InstantToSurrealDatetimeSerializer;

import java.time.Instant;

/**
 * Represents an entry in the {@code oneiros_schema_history} table.
 *
 * <p>This table tracks all applied migrations to ensure they are only executed once.
 *
 * <p><b>Table Schema:</b>
 * <pre>
 * DEFINE TABLE oneiros_schema_history SCHEMAFULL;
 * DEFINE FIELD version ON oneiros_schema_history TYPE int;
 * DEFINE FIELD description ON oneiros_schema_history TYPE string;
 * DEFINE FIELD installed_on ON oneiros_schema_history TYPE datetime;
 * DEFINE FIELD execution_time_ms ON oneiros_schema_history TYPE int;
 * DEFINE FIELD success ON oneiros_schema_history TYPE bool;
 * DEFINE INDEX idx_version ON oneiros_schema_history FIELDS version UNIQUE;
 * </pre>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SchemaHistoryEntry {

    @JsonIgnore
    private String id;

    private int version;

    private String description;

    @JsonProperty("installed_on")
    @JsonSerialize(using = InstantToSurrealDatetimeSerializer.class)
    private Instant installedOn;

    @JsonProperty("execution_time_ms")
    private long executionTimeMs;

    private boolean success;

    @JsonProperty("error_message")
    private String errorMessage;

    public SchemaHistoryEntry() {
    }

    public SchemaHistoryEntry(int version, String description) {
        this.version = version;
        this.description = description;
        this.installedOn = Instant.now();
        this.success = false;
    }

    // Getters and Setters

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Instant getInstalledOn() {
        return installedOn;
    }

    public void setInstalledOn(Instant installedOn) {
        this.installedOn = installedOn;
    }

    public long getExecutionTimeMs() {
        return executionTimeMs;
    }

    public void setExecutionTimeMs(long executionTimeMs) {
        this.executionTimeMs = executionTimeMs;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    @Override
    public String toString() {
        return "SchemaHistoryEntry{" +
                "version=" + version +
                ", description='" + description + '\'' +
                ", installedOn=" + installedOn +
                ", success=" + success +
                ", executionTimeMs=" + executionTimeMs +
                '}';
    }
}

