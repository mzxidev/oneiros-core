package io.oneiros.backup;

import net.jpountz.lz4.LZ4Factory;
import net.jpountz.lz4.LZ4FastDecompressor;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

/**
 * Custom InputStream that decompresses LZ4 block-compressed data.
 *
 * Reads blocks written by {@link Lz4BlockOutputStream}.
 */
public class Lz4BlockInputStream extends InputStream {
    private static final int DEFAULT_BLOCK_SIZE = 64 * 1024; // 64KB

    private final InputStream in;
    private final LZ4FastDecompressor decompressor;
    private final byte[] decompressedBuffer;
    private int bufferPosition;
    private int bufferLimit;
    private boolean closed;
    private boolean eof;

    public Lz4BlockInputStream(InputStream in) {
        this(in, DEFAULT_BLOCK_SIZE);
    }

    public Lz4BlockInputStream(InputStream in, int blockSize) {
        this.in = in;
        this.decompressor = LZ4Factory.fastestInstance().fastDecompressor();
        this.decompressedBuffer = new byte[blockSize];
        this.bufferPosition = 0;
        this.bufferLimit = 0;
        this.closed = false;
        this.eof = false;
    }

    @Override
    public int read() throws IOException {
        ensureOpen();

        if (bufferPosition >= bufferLimit) {
            if (!readNextBlock()) {
                return -1;
            }
        }

        return decompressedBuffer[bufferPosition++] & 0xFF;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        ensureOpen();

        if (b == null) {
            throw new NullPointerException();
        }
        if (off < 0 || len < 0 || off + len > b.length) {
            throw new IndexOutOfBoundsException();
        }
        if (len == 0) {
            return 0;
        }

        int totalRead = 0;

        while (totalRead < len) {
            if (bufferPosition >= bufferLimit) {
                if (!readNextBlock()) {
                    return totalRead == 0 ? -1 : totalRead;
                }
            }

            int available = bufferLimit - bufferPosition;
            int toRead = Math.min(len - totalRead, available);

            System.arraycopy(decompressedBuffer, bufferPosition, b, off + totalRead, toRead);
            bufferPosition += toRead;
            totalRead += toRead;
        }

        return totalRead;
    }

    @Override
    public int available() throws IOException {
        ensureOpen();
        return bufferLimit - bufferPosition;
    }

    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }

        closed = true;
        in.close();
    }

    /**
     * Read and decompress the next block.
     *
     * @return true if block was read, false if EOF
     */
    private boolean readNextBlock() throws IOException {
        if (eof) {
            return false;
        }

        // Read uncompressed length
        int uncompressedLength;
        try {
            uncompressedLength = readInt();
        } catch (EOFException e) {
            eof = true;
            return false;
        }

        if (uncompressedLength <= 0) {
            throw new IOException("Invalid uncompressed block length: " + uncompressedLength);
        }

        // Read compressed length
        int compressedLength = readInt();

        if (compressedLength <= 0) {
            throw new IOException("Invalid compressed block length: " + compressedLength);
        }

        // Read compressed data
        byte[] compressedData = new byte[compressedLength];
        int bytesRead = 0;

        while (bytesRead < compressedLength) {
            int n = in.read(compressedData, bytesRead, compressedLength - bytesRead);
            if (n < 0) {
                throw new EOFException("Unexpected EOF while reading compressed block");
            }
            bytesRead += n;
        }

        // Decompress
        decompressor.decompress(
            compressedData, 0,
            decompressedBuffer, 0, uncompressedLength
        );

        bufferPosition = 0;
        bufferLimit = uncompressedLength;

        return true;
    }

    private int readInt() throws IOException {
        int ch1 = in.read();
        int ch2 = in.read();
        int ch3 = in.read();
        int ch4 = in.read();

        if ((ch1 | ch2 | ch3 | ch4) < 0) {
            throw new EOFException();
        }

        return ((ch1 << 24) + (ch2 << 16) + (ch3 << 8) + ch4);
    }

    private void ensureOpen() throws IOException {
        if (closed) {
            throw new IOException("Stream closed");
        }
    }
}

