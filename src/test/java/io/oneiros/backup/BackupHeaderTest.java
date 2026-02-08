package io.oneiros.backup;

import org.junit.jupiter.api.Test;

import java.io.*;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for BackupHeader serialization.
 */
class BackupHeaderTest {

    @Test
    void shouldCreateHeaderWithCurrentTimestamp() {
        Instant before = Instant.now();
        BackupHeader header = BackupHeader.create();
        Instant after = Instant.now();

        assertThat(header.version()).isEqualTo((byte) 1);

        // BackupHeader stores timestamp in milliseconds, so we need to truncate to millis for comparison
        Instant headerTime = header.timestampAsInstant();
        Instant beforeMillis = before.truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        Instant afterMillis = after.truncatedTo(java.time.temporal.ChronoUnit.MILLIS).plusMillis(1);

        assertThat(headerTime).isBetween(beforeMillis, afterMillis);
    }

    @Test
    void shouldWriteAndReadHeader() throws IOException {
        BackupHeader original = BackupHeader.create();

        // Write
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        original.writeTo(dos);

        // Read
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        DataInputStream dis = new DataInputStream(bais);
        BackupHeader read = BackupHeader.readFrom(dis);

        assertThat(read.version()).isEqualTo(original.version());
        assertThat(read.timestamp()).isEqualTo(original.timestamp());
    }

    @Test
    void shouldHaveCorrectHeaderSize() throws IOException {
        BackupHeader header = BackupHeader.create();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        header.writeTo(dos);

        assertThat(baos.toByteArray().length).isEqualTo(BackupHeader.headerSize());
        assertThat(BackupHeader.headerSize()).isEqualTo(13); // 4 + 1 + 8
    }

    @Test
    void shouldValidateMagicBytes() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);

        // Write invalid magic bytes
        dos.write(new byte[]{0x00, 0x00, 0x00, 0x00});
        dos.writeByte(1);
        dos.writeLong(System.currentTimeMillis());

        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        DataInputStream dis = new DataInputStream(bais);

        assertThatThrownBy(() -> BackupHeader.readFrom(dis))
            .isInstanceOf(IOException.class)
            .hasMessageContaining("Invalid backup file");
    }

    @Test
    void shouldConvertTimestampToInstant() {
        long timestamp = 1234567890000L;
        BackupHeader header = new BackupHeader((byte) 1, timestamp);

        Instant instant = header.timestampAsInstant();
        assertThat(instant.toEpochMilli()).isEqualTo(timestamp);
    }

    @Test
    void shouldHandleMagicBytesCorrectly() throws IOException {
        BackupHeader header = BackupHeader.create();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        header.writeTo(dos);

        byte[] data = baos.toByteArray();

        // Check magic bytes: "ONRS"
        assertThat(data[0]).isEqualTo((byte) 0x4F); // O
        assertThat(data[1]).isEqualTo((byte) 0x4E); // N
        assertThat(data[2]).isEqualTo((byte) 0x52); // R
        assertThat(data[3]).isEqualTo((byte) 0x53); // S
    }

    @Test
    void shouldThrowOnIncompleteHeader() {
        ByteArrayInputStream bais = new ByteArrayInputStream(new byte[5]);
        DataInputStream dis = new DataInputStream(bais);

        assertThatThrownBy(() -> BackupHeader.readFrom(dis))
            .isInstanceOf(IOException.class);
    }
}

