package io.oneiros.security;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Jackson serializer for LocalDateTime that outputs SurrealDB-compatible datetime literals.
 *
 * <p>SurrealDB expects datetime values in the format: {@code d"2024-01-01T00:00:00Z"}
 *
 * <p>Usage:
 * <pre>
 * {@code
 * @JsonSerialize(using = SurrealLocalDateTimeSerializer.class)
 * private LocalDateTime updatedAt;
 * }
 * </pre>
 *
 * @see LocalDateTime
 * @see java.time.Instant
 */
public class SurrealLocalDateTimeSerializer extends JsonSerializer<LocalDateTime> {

    private static final DateTimeFormatter FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
            .withZone(ZoneOffset.UTC);

    @Override
    public void serialize(LocalDateTime value, JsonGenerator gen, SerializerProvider serializers)
            throws IOException {
        if (value == null) {
            gen.writeNull();
        } else {
            // Convert to UTC and format for SurrealDB
            String formatted = value.atZone(ZoneOffset.UTC).format(FORMATTER);
            gen.writeRawValue("d\"" + formatted + "\"");
        }
    }
}

