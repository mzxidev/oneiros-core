package io.oneiros.backup;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for LZ4 compression/decompression streams.
 */
class Lz4StreamTest {

    @Test
    void shouldCompressAndDecompressSimpleData() throws IOException {
        String original = "Hello, World! This is a test of LZ4 compression.";
        byte[] originalBytes = original.getBytes(StandardCharsets.UTF_8);

        // Compress
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (Lz4BlockOutputStream out = new Lz4BlockOutputStream(baos)) {
            out.write(originalBytes);
        }

        byte[] compressed = baos.toByteArray();

        // Decompress
        ByteArrayInputStream bais = new ByteArrayInputStream(compressed);
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        try (Lz4BlockInputStream in = new Lz4BlockInputStream(bais)) {
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                result.write(buffer, 0, bytesRead);
            }
        }

        String decompressed = result.toString(StandardCharsets.UTF_8);
        assertThat(decompressed).isEqualTo(original);
    }

    @Test
    void shouldCompressLargeData() throws IOException {
        // Generate 1MB of random data
        Random random = new Random(42);
        byte[] original = new byte[1024 * 1024];
        random.nextBytes(original);

        // Compress
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (Lz4BlockOutputStream out = new Lz4BlockOutputStream(baos)) {
            out.write(original);
        }

        byte[] compressed = baos.toByteArray();

        // Decompress
        ByteArrayInputStream bais = new ByteArrayInputStream(compressed);
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        try (Lz4BlockInputStream in = new Lz4BlockInputStream(bais)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                result.write(buffer, 0, bytesRead);
            }
        }

        assertThat(result.toByteArray()).isEqualTo(original);
    }

    @Test
    void shouldHandleMultipleBlocks() throws IOException {
        // Create data larger than default block size (64KB)
        int dataSize = 200 * 1024; // 200KB
        byte[] original = new byte[dataSize];
        for (int i = 0; i < dataSize; i++) {
            original[i] = (byte) (i % 256);
        }

        // Compress
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (Lz4BlockOutputStream out = new Lz4BlockOutputStream(baos)) {
            out.write(original);
        }

        // Decompress
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        try (Lz4BlockInputStream in = new Lz4BlockInputStream(bais)) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                result.write(buffer, 0, bytesRead);
            }
        }

        assertThat(result.toByteArray()).isEqualTo(original);
    }

    @Test
    void shouldCompressIncrementally() throws IOException {
        String[] parts = {
            "First part of the data. ",
            "Second part of the data. ",
            "Third part of the data."
        };

        // Compress incrementally
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (Lz4BlockOutputStream out = new Lz4BlockOutputStream(baos)) {
            for (String part : parts) {
                out.write(part.getBytes(StandardCharsets.UTF_8));
            }
        }

        // Decompress
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        try (Lz4BlockInputStream in = new Lz4BlockInputStream(bais)) {
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                result.write(buffer, 0, bytesRead);
            }
        }

        String expected = String.join("", parts);
        assertThat(result.toString(StandardCharsets.UTF_8)).isEqualTo(expected);
    }

    @Test
    void shouldHandleEmptyData() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (Lz4BlockOutputStream out = new Lz4BlockOutputStream(baos)) {
            // Write nothing
        }

        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        try (Lz4BlockInputStream in = new Lz4BlockInputStream(bais)) {
            assertThat(in.read()).isEqualTo(-1);
        }
    }

    @Test
    void shouldHandleSingleByteWrites() throws IOException {
        byte[] original = "ABC".getBytes(StandardCharsets.UTF_8);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (Lz4BlockOutputStream out = new Lz4BlockOutputStream(baos)) {
            for (byte b : original) {
                out.write(b);
            }
        }

        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        try (Lz4BlockInputStream in = new Lz4BlockInputStream(bais)) {
            int b;
            while ((b = in.read()) != -1) {
                result.write(b);
            }
        }

        assertThat(result.toByteArray()).isEqualTo(original);
    }

    @Test
    void shouldFlushDataCorrectly() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (Lz4BlockOutputStream out = new Lz4BlockOutputStream(baos)) {
            out.write("First".getBytes(StandardCharsets.UTF_8));
            out.flush();
            out.write("Second".getBytes(StandardCharsets.UTF_8));
        }

        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        try (Lz4BlockInputStream in = new Lz4BlockInputStream(bais)) {
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                result.write(buffer, 0, bytesRead);
            }
        }

        assertThat(result.toString(StandardCharsets.UTF_8)).isEqualTo("FirstSecond");
    }

    @Test
    void shouldHandleCustomBlockSize() throws IOException {
        int blockSize = 8 * 1024; // 8KB
        byte[] original = new byte[20 * 1024]; // 20KB
        Arrays.fill(original, (byte) 0xFF);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (Lz4BlockOutputStream out = new Lz4BlockOutputStream(baos, blockSize)) {
            out.write(original);
        }

        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        try (Lz4BlockInputStream in = new Lz4BlockInputStream(bais, blockSize)) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                result.write(buffer, 0, bytesRead);
            }
        }

        assertThat(result.toByteArray()).isEqualTo(original);
    }

    @Test
    void shouldThrowOnClosedOutputStream() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Lz4BlockOutputStream out = new Lz4BlockOutputStream(baos);
        out.close();

        assertThatThrownBy(() -> out.write(1))
            .isInstanceOf(IOException.class)
            .hasMessageContaining("Stream closed");
    }

    @Test
    void shouldThrowOnClosedInputStream() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (Lz4BlockOutputStream out = new Lz4BlockOutputStream(baos)) {
            out.write("test".getBytes(StandardCharsets.UTF_8));
        }

        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        Lz4BlockInputStream in = new Lz4BlockInputStream(bais);
        in.close();

        assertThatThrownBy(() -> in.read())
            .isInstanceOf(IOException.class)
            .hasMessageContaining("Stream closed");
    }

    @Test
    void shouldWorkWithFiles(@TempDir Path tempDir) throws IOException {
        String original = "File compression test with LZ4!";
        Path compressed = tempDir.resolve("test.lz4");

        // Compress to file
        try (FileOutputStream fos = new FileOutputStream(compressed.toFile());
             Lz4BlockOutputStream out = new Lz4BlockOutputStream(fos)) {
            out.write(original.getBytes(StandardCharsets.UTF_8));
        }

        // Decompress from file
        StringBuilder result = new StringBuilder();
        try (FileInputStream fis = new FileInputStream(compressed.toFile());
             Lz4BlockInputStream in = new Lz4BlockInputStream(fis)) {
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                result.append(new String(buffer, 0, bytesRead, StandardCharsets.UTF_8));
            }
        }

        assertThat(result.toString()).isEqualTo(original);
    }

    @Test
    void shouldAchieveCompressionWithRepetitiveData() throws IOException {
        // Create highly repetitive data
        String pattern = "AAAAAAAA";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            sb.append(pattern);
        }
        byte[] original = sb.toString().getBytes(StandardCharsets.UTF_8);

        // Compress
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (Lz4BlockOutputStream out = new Lz4BlockOutputStream(baos)) {
            out.write(original);
        }

        byte[] compressed = baos.toByteArray();

        // Verify compression ratio
        assertThat(compressed.length).isLessThan(original.length);

        // Verify correctness
        ByteArrayInputStream bais = new ByteArrayInputStream(compressed);
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        try (Lz4BlockInputStream in = new Lz4BlockInputStream(bais)) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                result.write(buffer, 0, bytesRead);
            }
        }

        assertThat(result.toByteArray()).isEqualTo(original);
    }
}

