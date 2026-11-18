package com.tutorbridge.identity.internal;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code tutorbridge.identity.evidence.*}: where {@link LocalObjectStore} keeps evidence bytes (#12 Q1).
 * Required; the app refuses to start without it rather than writing into its working directory.
 *
 * @param dir a directory every API instance shares, outside any public web root
 */
@ConfigurationProperties("tutorbridge.identity.evidence")
public record EvidenceStoreProperties(String dir) {

    public EvidenceStoreProperties {
        if (dir == null || dir.isBlank()) {
            throw new IllegalStateException("tutorbridge.identity.evidence.dir (TB_EVIDENCE_DIR) is required");
        }
    }

    Path root() {
        return Path.of(dir).toAbsolutePath().normalize();
    }
}
