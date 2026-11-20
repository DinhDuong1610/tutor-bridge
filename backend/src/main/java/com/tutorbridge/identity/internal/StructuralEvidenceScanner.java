package com.tutorbridge.identity.internal;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * R0 check of stored evidence (Đính, #12 Q2, 15/11/2026): exactly the declared size, the declared
 * SHA-256, and leading magic bytes of the declared type, so a file named PDF that is a PNG or a program
 * is FAILED. It does not look for malware: that is TBD (OP-07) before real evidence is accepted.
 */
@Component
class StructuralEvidenceScanner implements EvidenceScanner {

    private static final Map<String, byte[]> MAGIC = Map.of(
            "application/pdf", new byte[] {'%', 'P', 'D', 'F', '-'},
            "image/jpeg", new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF},
            "image/png", new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});
    private static final int BUFFER = 64 * 1024;

    @Override
    public Verdict scan(InputStream bytes, String mediaType, int sizeBytes, String sha256) throws IOException {
        byte[] magic = MAGIC.get(mediaType);
        if (magic == null) {
            return Verdict.FAILED;
        }
        MessageDigest digest = sha256();
        byte[] head = new byte[magic.length];
        byte[] buffer = new byte[BUFFER];
        long size = 0;
        long allowed = (long) sizeBytes + 1;
        int read;
        while (size < allowed && (read = bytes.read(buffer, 0, (int) Math.min(buffer.length, allowed - size))) != -1) {
            if (size < head.length) {
                System.arraycopy(buffer, 0, head, (int) size, (int) Math.min(read, head.length - size));
            }
            digest.update(buffer, 0, read);
            size += read;
        }
        boolean declared = size == sizeBytes
                && HexFormat.of().formatHex(digest.digest()).equals(sha256)
                && size >= magic.length
                && Arrays.equals(head, magic);
        return declared ? Verdict.READY : Verdict.FAILED;
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
