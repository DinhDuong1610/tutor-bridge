package com.tutorbridge.identity.internal;

import java.io.IOException;
import java.io.InputStream;

/**
 * Decides whether quarantined evidence bytes may become READY (#12 Q2). R0 checks structure only
 * ({@link StructuralEvidenceScanner}). TBD (OP-07): a real malware scanner before any real evidence is
 * accepted. Runs outside any database transaction (INV-18).
 */
interface EvidenceScanner {

    enum Verdict { READY, FAILED }

    /** {@code bytes} is the stored object; the declared values are those of createEvidenceUpload. */
    Verdict scan(InputStream bytes, String mediaType, int sizeBytes, String sha256) throws IOException;
}
