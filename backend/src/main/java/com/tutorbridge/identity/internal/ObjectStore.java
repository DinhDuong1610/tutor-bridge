package com.tutorbridge.identity.internal;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.stream.Stream;

/**
 * Private storage for evidence bytes (#12 Q1). R0 writes to a local directory ({@link LocalObjectStore});
 * an object store such as S3 replaces it later without an API change. Never called inside a database
 * transaction (INV-18), and no key or path it uses ever reaches a DTO, an error or a log (AC-UC03-03).
 */
interface ObjectStore {

    /**
     * Copies at most {@code limit} + 1 bytes of {@code body} to a staging area, hashing them on the way.
     * Reading stops there, so a body longer than declared costs at most one extra byte.
     */
    Staged stage(InputStream body, long limit) throws IOException;

    /** Whether bytes were ever committed to {@code objectKey}. */
    boolean exists(String objectKey) throws IOException;

    /** The committed bytes; {@link java.nio.file.NoSuchFileException} when there are none. */
    InputStream open(String objectKey) throws IOException;

    /** Removes the committed bytes of {@code objectKey}; finding none is not an error (#182). */
    void delete(String objectKey) throws IOException;

    /** Keys whose bytes were last written before {@code cutoff}, for the orphan sweep (#182). The caller closes it. */
    Stream<String> keysWrittenBefore(Instant cutoff) throws IOException;

    /** Removes staged bytes last written before {@code cutoff}: left by a process that died mid-PUT (#182). */
    int deleteStagedBefore(Instant cutoff) throws IOException;

    /** Bytes in the staging area. Closing it removes them unless {@link #commit} moved them first. */
    interface Staged extends AutoCloseable {

        /** Bytes read, at most {@code limit} + 1. */
        long size();

        /** Lowercase hex SHA-256 of the bytes read. */
        String sha256();

        /**
         * Moves the bytes to {@code objectKey} atomically, replacing what was there. When the file system
         * will not replace it (Windows, while another commit or a reader holds it) and it already holds
         * these exact bytes, they are kept and the staged copy is dropped.
         */
        void commit(String objectKey) throws IOException;

        @Override
        void close() throws IOException;
    }
}
