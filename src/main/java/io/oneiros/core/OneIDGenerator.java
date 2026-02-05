package io.oneiros.core;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

public class OneIDGenerator implements IdGenerator {

    private static final String ALPHABET_STR = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final char[] ALPHABET = ALPHABET_STR.toCharArray();
    private static final int BASE = ALPHABET.length;

    // Wir reservieren 8 Zeichen für den Timestamp (reicht für hunderte Jahre)
    private static final int TIME_CHARS = 8;
    // 12 Zeichen Zufall für massive Sicherheit
    private static final int RANDOM_CHARS = 12;

    private final SecureRandom random = new SecureRandom();
    private final AtomicLong lastTimestamp = new AtomicLong(-1L);

    @Override
    public String generate() {
        // 1. Hole aktuellen Timestamp (Monotonicity Check)
        long now = System.currentTimeMillis();
        synchronized (this) {
            long last = lastTimestamp.get();
            if (now <= last) {
                now = last + 1; // Clock moved back or too fast? Increment anyway.
            }
            lastTimestamp.set(now);
        }

        StringBuilder sb = new StringBuilder(TIME_CHARS + RANDOM_CHARS);

        // 2. Encode Timestamp (Vorne für Sortierbarkeit)
        encodeBase62(sb, now, TIME_CHARS);

        // 3. Encode Randomness (Hinten für Uniqueness)
        // Wir generieren 12 zufällige Base62 Zeichen
        for (int i = 0; i < RANDOM_CHARS; i++) {
            sb.append(ALPHABET[random.nextInt(BASE)]);
        }

        return sb.toString();
    }

    /**
     * Feature: ID Prediction / Extraction
     * Extrahiert den Zeitstempel aus einer OneID.
     * Damit kannst du vorhersagen/prüfen, wann eine ID erstellt wurde.
     */
    public static Instant extractTime(String id) {
        if (id == null || id.length() < TIME_CHARS) {
            throw new IllegalArgumentException("Invalid OneID format");
        }
        String timePart = id.substring(0, TIME_CHARS);
        long timestamp = decodeBase62(timePart);
        return Instant.ofEpochMilli(timestamp);
    }

    // --- Private Helper ---

    private void encodeBase62(StringBuilder sb, long value, int targetLength) {
        StringBuilder temp = new StringBuilder();
        while (value > 0) {
            temp.append(ALPHABET[(int) (value % BASE)]);
            value /= BASE;
        }
        // Padding mit '0', falls Zahl zu klein (damit Sortierung stimmt)
        while (temp.length() < targetLength) {
            temp.append(ALPHABET[0]);
        }
        // Base62 entsteht rückwärts, also umdrehen
        sb.append(temp.reverse());
    }

    private static long decodeBase62(String input) {
        long result = 0;
        long power = 1;
        for (int i = input.length() - 1; i >= 0; i--) {
            int digit = ALPHABET_STR.indexOf(input.charAt(i));
            result += digit * power;
            power *= BASE;
        }
        return result;
    }
}
