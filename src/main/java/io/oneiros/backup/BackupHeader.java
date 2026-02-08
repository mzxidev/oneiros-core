package io.oneiros.backup;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.time.Instant;

/**
 * Header for Oneiros backup files.
 *
 * Format:
 * - Magic Bytes: "ONRS" (4 bytes)
 * - Version: 1 byte
 * - Timestamp: 8 bytes (long)
 * - Total: 13 bytes
 */
public record BackupHeader(
    byte version,
    long timestamp
) {
    private static final byte[] MAGIC_BYTES = {0x4F, 0x4E, 0x52, 0x53}; // "ONRS"
    private static final byte CURRENT_VERSION = 1;

    /**
     * Create a new backup header with current timestamp.
     */
    public static BackupHeader create() {
        return new BackupHeader(CURRENT_VERSION, Instant.now().toEpochMilli());
    }

    /**
     * Write header to output stream.
     */
    public void writeTo(DataOutputStream out) throws IOException {
        out.write(MAGIC_BYTES);
        out.writeByte(version);
        out.writeLong(timestamp);
    }

    /**
     * Read header from input stream.
     *
     * @throws IOException if magic bytes don't match or stream error
     */
    public static BackupHeader readFrom(DataInputStream in) throws IOException {
        // Validate magic bytes
        byte[] magic = new byte[4];
        in.readFully(magic);

        for (int i = 0; i < MAGIC_BYTES.length; i++) {
            if (magic[i] != MAGIC_BYTES[i]) {
                throw new IOException("Invalid backup file: Magic bytes mismatch");
            }
        }

        // Read version and timestamp
        byte version = in.readByte();
        long timestamp = in.readLong();

        return new BackupHeader(version, timestamp);
    }

    /**
     * Get timestamp as Instant.
     */
    public Instant timestampAsInstant() {
        return Instant.ofEpochMilli(timestamp);
    }

    /**
     * Get header size in bytes.
     */
    public static int headerSize() {
        return 4 + 1 + 8; // magic + version + timestamp
    }
}

