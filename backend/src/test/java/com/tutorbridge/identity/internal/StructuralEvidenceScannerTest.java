package com.tutorbridge.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.tutorbridge.identity.internal.EvidenceScanner.Verdict;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

/** R0 structural check (#12 Q2): declared size, declared SHA-256 and the magic bytes of the declared type. */
class StructuralEvidenceScannerTest {

    private static final byte[] PDF = "%PDF-1.7\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xDB};
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    private final StructuralEvidenceScanner scanner = new StructuralEvidenceScanner();

    private static byte[] file(byte[] head, int size) {
        byte[] bytes = new byte[size];
        Arrays.fill(bytes, (byte) 7);
        System.arraycopy(head, 0, bytes, 0, Math.min(head.length, size));
        return bytes;
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private Verdict scan(byte[] stored, String mediaType, int sizeBytes, String sha256) throws IOException {
        return scanner.scan(new ByteArrayInputStream(stored), mediaType, sizeBytes, sha256);
    }

    private Verdict scanDeclared(byte[] stored, String mediaType) throws Exception {
        return scan(stored, mediaType, stored.length, sha256(stored));
    }

    @Test
    void each_type_with_its_own_magic_bytes_is_ready() throws Exception {
        assertThat(scanDeclared(file(PDF, 200_000), "application/pdf")).isEqualTo(Verdict.READY);
        assertThat(scanDeclared(file(JPEG, 10), "image/jpeg")).isEqualTo(Verdict.READY);
        assertThat(scanDeclared(file(PNG, 8), "image/png")).isEqualTo(Verdict.READY);
    }

    @Test
    void magic_bytes_of_another_type_or_of_a_program_are_failed() throws Exception {
        assertThat(scanDeclared(file(PNG, 100), "application/pdf")).isEqualTo(Verdict.FAILED);
        assertThat(scanDeclared(file(new byte[] {'M', 'Z'}, 100), "application/pdf")).isEqualTo(Verdict.FAILED);
        assertThat(scanDeclared(file(JPEG, 100), "image/png")).isEqualTo(Verdict.FAILED);
        assertThat(scanDeclared(file(PDF, 100), "image/jpeg")).isEqualTo(Verdict.FAILED);
        assertThat(scanDeclared(file(PDF, 100), "image/gif")).isEqualTo(Verdict.FAILED);
    }

    @Test
    void a_file_shorter_than_the_magic_bytes_is_failed() throws Exception {
        assertThat(scanDeclared(Arrays.copyOf(PNG, 5), "image/png")).isEqualTo(Verdict.FAILED);
        assertThat(scanDeclared(new byte[0], "application/pdf")).isEqualTo(Verdict.FAILED);
    }

    @Test
    void stored_bytes_other_than_declared_are_failed() throws Exception {
        byte[] stored = file(PDF, 1000);
        String sha = sha256(stored);
        assertThat(scan(stored, "application/pdf", 999, sha)).isEqualTo(Verdict.FAILED);
        assertThat(scan(stored, "application/pdf", 1001, sha)).isEqualTo(Verdict.FAILED);
        assertThat(scan(stored, "application/pdf", 1000, sha256(file(PDF, 1001)))).isEqualTo(Verdict.FAILED);
    }

    @Test
    void reading_stops_one_byte_past_the_declared_size() throws Exception {
        byte[] stored = file(PDF, 10_000);
        InputStream bytes = new ByteArrayInputStream(stored);
        assertThat(scanner.scan(bytes, "application/pdf", 100, sha256(Arrays.copyOf(stored, 100))))
                .isEqualTo(Verdict.FAILED);
        assertThat(bytes.available()).isEqualTo(10_000 - 101);
    }

    /** A stream that hands out one byte at a time, as a slow store might. */
    @Test
    void magic_bytes_split_across_reads_are_still_seen() throws Exception {
        byte[] stored = file(PNG, 50);
        InputStream trickle = new ByteArrayInputStream(stored) {
            @Override
            public synchronized int read(byte[] b, int off, int len) {
                return super.read(b, off, Math.min(len, 1));
            }
        };
        assertThat(scanner.scan(trickle, "image/png", 50, sha256(stored))).isEqualTo(Verdict.READY);
    }
}
