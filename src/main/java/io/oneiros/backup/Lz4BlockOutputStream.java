package io.oneiros.backup;

import net.jpountz.lz4.LZ4Compressor;
import net.jpountz.lz4.LZ4Factory;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Custom OutputStream that compresses data in blocks using LZ4.
 *
 * Format per block:
 * - Uncompressed Length (int, 4 bytes)
 * - Compressed Length (int, 4 bytes)
 * - Compressed Data (variable)
 *
 * Uses block-based compression for memory efficiency.
 */
public class Lz4BlockOutputStream extends OutputStream {
    private static final int DEFAULT_BLOCK_SIZE = 64 * 1024; // 64KB

    private final OutputStream out;
    private final LZ4Compressor compressor;
    private final byte[] buffer;
    private final byte[] compressedBuffer;
    private int bufferPosition;
    private boolean closed;

    public Lz4BlockOutputStream(OutputStream out) {
        this(out, DEFAULT_BLOCK_SIZE);
    }

    public Lz4BlockOutputStream(OutputStream out, int blockSize) {
        this.out = out;
        this.compressor = LZ4Factory.fastestInstance().fastCompressor();
        this.buffer = new byte[blockSize];
        this.compressedBuffer = new byte[compressor.maxCompressedLength(blockSize)];
        this.bufferPosition = 0;
        this.closed = false;
    }

    @Override
    public void write(int b) throws IOException {
        ensureOpen();

        buffer[bufferPosition++] = (byte) b;

        if (bufferPosition >= buffer.length) {
            flushBlock();
        }
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        ensureOpen();

        if (b == null) {
            throw new NullPointerException();
        }
        if (off < 0 || len < 0 || off + len > b.length) {
            throw new IndexOutOfBoundsException();
        }

        int remaining = len;
        int offset = off;

        while (remaining > 0) {
            int available = buffer.length - bufferPosition;
            int toWrite = Math.min(remaining, available);

            System.arraycopy(b, offset, buffer, bufferPosition, toWrite);
            bufferPosition += toWrite;
            offset += toWrite;
            remaining -= toWrite;

            if (bufferPosition >= buffer.length) {
                flushBlock();
            }
        }
    }

    @Override
    public void flush() throws IOException {
        ensureOpen();

        if (bufferPosition > 0) {
            flushBlock();
        }

        out.flush();
    }

    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }

        try {
            flush();
        } finally {
            closed = true;
            out.close();
        }
    }

    private void flushBlock() throws IOException {
        if (bufferPosition == 0) {
            return;
        }

        // Compress the block
        int compressedLength = compressor.compress(
            buffer, 0, bufferPosition,
            compressedBuffer, 0, compressedBuffer.length
        );

        // Write: [Uncompressed Length (int)] + [Compressed Length (int)] + [Compressed Data]
        writeInt(bufferPosition); // Original/uncompressed length
        writeInt(compressedLength); // Compressed length
        out.write(compressedBuffer, 0, compressedLength);

        // Reset buffer
        bufferPosition = 0;
    }

    private void writeInt(int value) throws IOException {
        out.write((value >>> 24) & 0xFF);
        out.write((value >>> 16) & 0xFF);
        out.write((value >>> 8) & 0xFF);
        out.write(value & 0xFF);
    }

    private void ensureOpen() throws IOException {
        if (closed) {
            throw new IOException("Stream closed");
        }
    }
}

