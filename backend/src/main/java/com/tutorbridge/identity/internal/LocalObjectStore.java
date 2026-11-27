package com.tutorbridge.identity.internal;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;
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

    @Override
    public boolean exists(String objectKey) {
        return Files.isRegularFile(object(objectKey));
    }

    @Override
    public InputStream open(String objectKey) throws IOException {
        return Files.newInputStream(object(objectKey));
    }

    @Override
    public void delete(String objectKey) throws IOException {
        Files.deleteIfExists(object(objectKey));
    }

    /** Only names that are keys: anything else in {@code objects} was not put there by this service. */
    @Override
    public Stream<String> keysWrittenBefore(Instant cutoff) throws IOException {
        return Files.list(objects)
                .filter(path -> KEY.matcher(path.getFileName().toString()).matches())
                .filter(path -> writtenBefore(path, cutoff))
                .map(path -> path.getFileName().toString());
    }

    @Override
    public int deleteStagedBefore(Instant cutoff) throws IOException {
        int deleted = 0;
        try (Stream<Path> files = Files.list(staging)) {
            for (Path file : (Iterable<Path>) files::iterator) {
                if (writtenBefore(file, cutoff) && Files.deleteIfExists(file)) {
                    deleted++;
                }
            }
        }
        return deleted;
    }

    /** A file gone meanwhile, or one that cannot be read, is not old: it is left for the next run. */
    private static boolean writtenBefore(Path file, Instant cutoff) {
        try {
            return Files.isRegularFile(file) && Files.getLastModifiedTime(file).toInstant().isBefore(cutoff);
        } catch (IOException e) {
            return false;
        }
    }

    /** Only keys minted by this service, so no key can name a file outside {@code objects}. */
    private Path object(String objectKey) {
        if (!KEY.matcher(objectKey).matches()) {
            throw new IllegalArgumentException("not an object key minted by this service");
        }
        return objects.resolve(objectKey);
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
            Path target = object(objectKey);
            try {
                Files.move(file, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (FileSystemException e) {
                // Windows refuses to replace a file another commit is moving in or a reader holds open.
                // When that file already holds these exact bytes, the commit has nothing left to do.
                if (!sameBytes(target)) {
                    throw e;
                }
                Files.deleteIfExists(file);
            }
        }

        private boolean sameBytes(Path target) {
            MessageDigest digest = LocalObjectStore.sha256();
            try (InputStream in = Files.newInputStream(target)) {
                byte[] buffer = new byte[BUFFER];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            } catch (IOException e) {
                return false;
            }
            return HexFormat.of().formatHex(digest.digest()).equals(sha256);
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
