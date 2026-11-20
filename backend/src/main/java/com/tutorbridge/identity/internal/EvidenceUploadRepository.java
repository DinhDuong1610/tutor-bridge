package com.tutorbridge.identity.internal;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** {@code tutor_evidence_uploads} (V5), owned by identity (module-map). */
@Repository
class EvidenceUploadRepository {

    record Row(UUID id, UUID ownerId, String objectKey, String mediaType, int sizeBytes, String sha256,
            String state, Instant expiresAt) {}

    private static final String COLUMNS = "id, owner_id, object_key, media_type, size_bytes, sha256, state, expires_at";

    private final JdbcClient jdbc;

    EvidenceUploadRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Verified tutors only: an UNVERIFIED account has no session, a LOCKED one is refused here. */
    @Transactional(propagation = Propagation.MANDATORY)
    boolean lockActiveTutor(UUID accountId) {
        return jdbc.sql("select 1 from accounts where id = ? and role = 'TUTOR' and status = 'ACTIVE' for share")
                .param(accountId).query((rs, n) -> 1).optional().isPresent();
    }

    /** The same rule without a lock, to refuse before any byte is read. */
    boolean isActiveTutor(UUID accountId) {
        return jdbc.sql("select 1 from accounts where id = ? and role = 'TUTOR' and status = 'ACTIVE'")
                .param(accountId).query((rs, n) -> 1).optional().isPresent();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    void insert(UUID id, UUID ownerId, String objectKey, String mediaType, int sizeBytes, String sha256,
            Instant createdAt, Instant expiresAt) {
        jdbc.sql("""
                insert into tutor_evidence_uploads(id, owner_id, object_key, media_type, size_bytes, sha256, state,
                                                   created_at, expires_at)
                values (?, ?, ?, ?, ?, ?, 'UPLOADING', ?, ?)
                """).params(id, ownerId, objectKey, mediaType, sizeBytes, sha256,
                        at(createdAt), at(expiresAt))
                .update();
    }

    Optional<Row> find(UUID id) {
        return jdbc.sql("select " + COLUMNS + " from tutor_evidence_uploads where id = ?")
                .param(id).query(Row.class).optional();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    Optional<Row> lock(UUID id) {
        return jdbc.sql("select " + COLUMNS + " from tutor_evidence_uploads where id = ? for update")
                .param(id).query(Row.class).optional();
    }

    /** Moves a locked upload from one state to the next; anything else is a bug in the caller. */
    @Transactional(propagation = Propagation.MANDATORY)
    void moveState(UUID id, String from, String to) {
        int updated = jdbc.sql("update tutor_evidence_uploads set state = ? where id = ? and state = ?")
                .params(to, id, from).update();
        if (updated != 1) {
            throw new IllegalStateException("Upload " + id + " is not " + from);
        }
    }

    private static OffsetDateTime at(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
