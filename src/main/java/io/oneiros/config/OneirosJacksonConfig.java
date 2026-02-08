package io.oneiros.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Jackson ObjectMapper configuration for SurrealDB compatibility.
 *
 * <p>Handles:
 * <ul>
 *   <li>Datetime serialization to SurrealDB d"..." format</li>
 *   <li>Datetime deserialization from both d"..." and ISO-8601 formats</li>
 *   <li>Non-null serialization</li>
 *   <li>Unknown property handling</li>
 * </ul>
 */
@Slf4j
public class OneirosJacksonConfig {

    private static final DateTimeFormatter SURREAL_DATETIME_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC);

    /**
     * Create a Jackson ObjectMapper configured for SurrealDB.
     *
     * @return Configured ObjectMapper instance
     */
    public static ObjectMapper createSurrealObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        configureSurrealObjectMapper(mapper);
        return mapper;
    }

    /**
     * Configure an existing ObjectMapper for SurrealDB compatibility.
     *
     * @param mapper The ObjectMapper to configure
     */
    public static void configureSurrealObjectMapper(ObjectMapper mapper) {
        // Create module for datetime handling
        SimpleModule surrealModule = new SimpleModule("OneirosModule");

        // Instant serializer/deserializer
        surrealModule.addSerializer(Instant.class, new SurrealInstantSerializer());
        surrealModule.addDeserializer(Instant.class, new SurrealInstantDeserializer());

        // LocalDateTime serializer/deserializer
        surrealModule.addSerializer(LocalDateTime.class, new SurrealLocalDateTimeSerializer());
        surrealModule.addDeserializer(LocalDateTime.class, new SurrealLocalDateTimeDeserializer());

        // ZonedDateTime serializer/deserializer
        surrealModule.addSerializer(ZonedDateTime.class, new SurrealZonedDateTimeSerializer());
        surrealModule.addDeserializer(ZonedDateTime.class, new SurrealZonedDateTimeDeserializer());

        // OffsetDateTime serializer/deserializer
        surrealModule.addSerializer(OffsetDateTime.class, new SurrealOffsetDateTimeSerializer());
        surrealModule.addDeserializer(OffsetDateTime.class, new SurrealOffsetDateTimeDeserializer());

        mapper.registerModule(surrealModule);

        // Don't serialize null values (use configOverride for non-deprecated approach)
        mapper.configOverride(Object.class)
            .setInclude(JsonInclude.Value.construct(JsonInclude.Include.NON_NULL, JsonInclude.Include.NON_NULL));

        // Don't write dates as timestamps (use ISO format)
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // Ignore unknown properties during deserialization
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        // Accept null for non-primitive fields (SurrealDB NONE -> Java null)
        mapper.disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES);

        // Accept empty strings as null
        mapper.enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT);

        log.debug("🔧 Configured ObjectMapper for SurrealDB compatibility");
    }

    // ============================================================
    // INSTANT SERIALIZER/DESERIALIZER
    // ============================================================

    /**
     * Serializes Instant to SurrealDB datetime format: d"2024-01-01T00:00:00Z"
     *
     * <p>Note: We use writeRawValue to output the SurrealDB datetime literal.
     * This works correctly with writeValueAsString but requires special handling
     * when using valueToTree (which doesn't support raw values).
     */
    public static class SurrealInstantSerializer extends StdSerializer<Instant> {

        public SurrealInstantSerializer() {
            super(Instant.class);
        }

        @Override
        public void serialize(Instant value, JsonGenerator gen, SerializerProvider provider) throws IOException {
            if (value == null) {
                gen.writeNull();
            } else {
                // SurrealDB datetime literal format: d"2024-01-01T00:00:00Z"
                // Truncate to seconds for SurrealDB compatibility
                String formatted = SURREAL_DATETIME_FORMATTER.format(value.truncatedTo(java.time.temporal.ChronoUnit.SECONDS));
                gen.writeRawValue("d\"" + formatted + "\"");
            }
        }
    }

    /**
     * Deserializes both SurrealDB d"..." format and standard ISO-8601 strings to Instant.
     */
    public static class SurrealInstantDeserializer extends StdDeserializer<Instant> {

        public SurrealInstantDeserializer() {
            super(Instant.class);
        }

        @Override
        public Instant deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            String text = p.getText();
            if (text == null || text.isEmpty()) {
                return null;
            }

            // Remove SurrealDB d"..." wrapper if present
            if (text.startsWith("d\"") && text.endsWith("\"")) {
                text = text.substring(2, text.length() - 1);
            }

            try {
                return Instant.parse(text);
            } catch (DateTimeParseException e) {
                // Try parsing as LocalDateTime and convert to Instant
                try {
                    return LocalDateTime.parse(text).toInstant(ZoneOffset.UTC);
                } catch (DateTimeParseException e2) {
                    log.warn("Failed to parse datetime: {}", text);
                    return null;
                }
            }
        }
    }

    // ============================================================
    // LOCALDATETIME SERIALIZER/DESERIALIZER
    // ============================================================

    public static class SurrealLocalDateTimeSerializer extends StdSerializer<LocalDateTime> {

        public SurrealLocalDateTimeSerializer() {
            super(LocalDateTime.class);
        }

        @Override
        public void serialize(LocalDateTime value, JsonGenerator gen, SerializerProvider provider) throws IOException {
            if (value == null) {
                gen.writeNull();
            } else {
                gen.writeRawValue("d\"" + value.atZone(ZoneOffset.UTC).format(SURREAL_DATETIME_FORMATTER) + "\"");
            }
        }
    }

    public static class SurrealLocalDateTimeDeserializer extends StdDeserializer<LocalDateTime> {

        public SurrealLocalDateTimeDeserializer() {
            super(LocalDateTime.class);
        }

        @Override
        public LocalDateTime deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            String text = p.getText();
            if (text == null || text.isEmpty()) {
                return null;
            }

            if (text.startsWith("d\"") && text.endsWith("\"")) {
                text = text.substring(2, text.length() - 1);
            }

            try {
                return LocalDateTime.parse(text.replace("Z", ""));
            } catch (DateTimeParseException e) {
                try {
                    return Instant.parse(text).atZone(ZoneOffset.UTC).toLocalDateTime();
                } catch (DateTimeParseException e2) {
                    log.warn("Failed to parse LocalDateTime: {}", text);
                    return null;
                }
            }
        }
    }

    // ============================================================
    // ZONEDDATETIME SERIALIZER/DESERIALIZER
    // ============================================================

    public static class SurrealZonedDateTimeSerializer extends StdSerializer<ZonedDateTime> {

        public SurrealZonedDateTimeSerializer() {
            super(ZonedDateTime.class);
        }

        @Override
        public void serialize(ZonedDateTime value, JsonGenerator gen, SerializerProvider provider) throws IOException {
            if (value == null) {
                gen.writeNull();
            } else {
                gen.writeRawValue("d\"" + value.withZoneSameInstant(ZoneOffset.UTC).format(SURREAL_DATETIME_FORMATTER) + "\"");
            }
        }
    }

    public static class SurrealZonedDateTimeDeserializer extends StdDeserializer<ZonedDateTime> {

        public SurrealZonedDateTimeDeserializer() {
            super(ZonedDateTime.class);
        }

        @Override
        public ZonedDateTime deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            String text = p.getText();
            if (text == null || text.isEmpty()) {
                return null;
            }

            if (text.startsWith("d\"") && text.endsWith("\"")) {
                text = text.substring(2, text.length() - 1);
            }

            try {
                return ZonedDateTime.parse(text);
            } catch (DateTimeParseException e) {
                try {
                    return Instant.parse(text).atZone(ZoneOffset.UTC);
                } catch (DateTimeParseException e2) {
                    log.warn("Failed to parse ZonedDateTime: {}", text);
                    return null;
                }
            }
        }
    }

    // ============================================================
    // OFFSETDATETIME SERIALIZER/DESERIALIZER
    // ============================================================

    public static class SurrealOffsetDateTimeSerializer extends StdSerializer<OffsetDateTime> {

        public SurrealOffsetDateTimeSerializer() {
            super(OffsetDateTime.class);
        }

        @Override
        public void serialize(OffsetDateTime value, JsonGenerator gen, SerializerProvider provider) throws IOException {
            if (value == null) {
                gen.writeNull();
            } else {
                gen.writeRawValue("d\"" + value.withOffsetSameInstant(ZoneOffset.UTC).format(SURREAL_DATETIME_FORMATTER) + "\"");
            }
        }
    }

    public static class SurrealOffsetDateTimeDeserializer extends StdDeserializer<OffsetDateTime> {

        public SurrealOffsetDateTimeDeserializer() {
            super(OffsetDateTime.class);
        }

        @Override
        public OffsetDateTime deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            String text = p.getText();
            if (text == null || text.isEmpty()) {
                return null;
            }

            if (text.startsWith("d\"") && text.endsWith("\"")) {
                text = text.substring(2, text.length() - 1);
            }

            try {
                return OffsetDateTime.parse(text);
            } catch (DateTimeParseException e) {
                try {
                    return Instant.parse(text).atOffset(ZoneOffset.UTC);
                } catch (DateTimeParseException e2) {
                    log.warn("Failed to parse OffsetDateTime: {}", text);
                    return null;
                }
            }
        }
    }
}
