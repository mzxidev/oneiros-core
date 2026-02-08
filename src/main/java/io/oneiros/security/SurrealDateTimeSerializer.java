package io.oneiros.security;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Jackson serializer for Instant that outputs SurrealDB-compatible datetime literals.
 *
 * <p>SurrealDB expects datetime values in the format: {@code d"2024-01-01T00:00:00Z"}
 *
 * <p>Usage:
 * <pre>
 * {@code
 * @JsonSerialize(using = SurrealDateTimeSerializer.class)
 * private Instant createdAt;
 * }
 * </pre>
 *
 * Or configure globally:
 * <pre>
 * {@code
 * ObjectMapper mapper = new ObjectMapper();
 * SimpleModule module = new SimpleModule();
 * module.addSerializer(Instant.class, new SurrealDateTimeSerializer());
 * mapper.registerModule(module);
 * }
 * </pre>
 *
 * @see Instant
 * @see java.time.LocalDateTime
 */
public class SurrealDateTimeSerializer extends JsonSerializer<Instant> {

    private static final DateTimeFormatter FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
            .withZone(ZoneOffset.UTC);

    @Override
    public void serialize(Instant value, JsonGenerator gen, SerializerProvider serializers)
            throws IOException {
        if (value == null) {
            gen.writeNull();
        } else {
            // SurrealDB datetime format: d"2024-01-01T00:00:00Z"
            gen.writeRawValue("d\"" + FORMATTER.format(value) + "\"");
        }
    }
}

