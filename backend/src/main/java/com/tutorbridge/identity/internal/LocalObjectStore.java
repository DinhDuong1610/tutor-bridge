package com.tutorbridge.identity.internal;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * {@link ObjectStore} in a local directory: {@code <dir>/objects/<key>}, staged under {@code <dir>/tmp}
 * on the same file system so the final move is atomic. Every API instance must see the same directory
 * (INV-80); nothing is kept in memory.
 */
@Component
class LocalObjectStore implements ObjectStore {

    /** Keys are minted by {@link EvidenceUploads}; anything else could leave the directory. */
    private static final Pattern KEY = Pattern.compile("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}");
    private static final int BUFFER = 64 * 1024;

    private final Path objects;
    private final Path staging;

    LocalObjectStore(EvidenceStoreProperties properties) {
        this.objects = properties.root().resolve("objects");
        this.staging = properties.root().resolve("tmp");
        try {
            Files.createDirectories(objects);
            Files.createDirectories(staging);
        } catch (IOException e) {
            throw new UncheckedIOException("tutorbridge.identity.evidence.dir is not writable", e);
        }
    }

    @Override
    public Staged stage(InputStream body, long limit) throws IOException {
        Path file = staging.resolve(UUID.randomUUID().toString());
        MessageDigest digest = sha256();
        long size = 0;
        try (OutputStream out = new DigestOutputStream(Files.newOutputStream(file), digest)) {
            byte[] buffer = new byte[BUFFER];
            long allowed = limit + 1;
            int read;
            while (size < allowed
                    && (read = body.read(buffer, 0, (int) Math.min(buffer.length, allowed - size))) != -1) {
                out.write(buffer, 0, read);
                size += read;
            }
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(file);
            throw e;
        }
        return new LocalStaged(file, size, HexFormat.of().formatHex(digest.digest()));
    }

    private final class LocalStaged implements Staged {

        private final Path file;
        private final long size;
        private final String sha256;

        LocalStaged(Path file, long size, String sha256) {
            this.file = file;
            this.size = size;
            this.sha256 = sha256;
        }

        @Override
        public long size() {
            return size;
        }

        @Override
        public String sha256() {
            return sha256;
        }

        @Override
        public void commit(String objectKey) throws IOException {
            if (!KEY.matcher(objectKey).matches()) {
                throw new IllegalArgumentException("not an object key minted by this service");
            }
            Files.move(file, objects.resolve(objectKey), StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        }

        @Override
        public void close() throws IOException {
            Files.deleteIfExists(file);
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
