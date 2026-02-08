package io.oneiros.migration.serializer;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;
import java.time.Instant;

/**
 * Custom Jackson serializer for converting Java Instant to SurrealDB datetime format.
 *
 * <p>SurrealDB accepts datetime values as numeric timestamps (seconds since Unix epoch).
 * This serializer converts Java Instant to a decimal number representing seconds.nanoseconds.
 *
 * <p>Example: <code>1707414168.208</code> represents 2024-02-08T17:42:48.208Z
 */
public class InstantToSurrealDatetimeSerializer extends JsonSerializer<Instant> {

    @Override
    public void serialize(Instant value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (value == null) {
            gen.writeNull();
        } else {
            // Convert to decimal seconds (SurrealDB format)
            double timestamp = value.getEpochSecond() + (value.getNano() / 1_000_000_000.0);
            gen.writeNumber(timestamp);
        }
    }
}

