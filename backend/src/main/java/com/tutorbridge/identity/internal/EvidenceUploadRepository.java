package com.tutorbridge.identity.internal;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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

    /**
     * The scan found other bytes than declared, or none: QUARANTINED becomes FAILED at {@code at} (V21). The
     * time is when it failed, not its ticket: complete goes on with a QUARANTINED upload after its ticket
     * expired, so a failure can be much newer than the ticket (Đính on #262).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    void moveToFailed(UUID id, Instant at) {
        int updated = jdbc.sql("""
                update tutor_evidence_uploads set state = 'FAILED', failed_at = ?
                where id = ? and state = 'QUARANTINED'
                """).params(at(at), id).update();
        if (updated != 1) {
            throw new IllegalStateException("Upload " + id + " is not QUARANTINED");
        }
    }

    /**
     * #182: marks DELETED, and returns the keys of, at most {@code limit} uploads whose bytes nothing can use
     * any more: UPLOADING that expired before {@code expiredBefore}, and FAILED that failed before
     * {@code failedBefore}. Neither state changes again: an expired UPLOADING takes no PUT or complete, and
     * nothing moves a FAILED one but this. A FAILED upload with no failure time (failed by code before V21)
     * is left alone. An upload linked to a profile version is never taken, whatever its state. SKIP LOCKED:
     * a row another transaction holds waits.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    List<String> markUnusedDeleted(Instant expiredBefore, Instant failedBefore, int limit) {
        return jdbc.sql("""
                with due as (
                    select u.id from tutor_evidence_uploads u
                    where ((u.state = 'UPLOADING' and u.expires_at < ?) or (u.state = 'FAILED' and u.failed_at < ?))
                      and not exists (select 1 from tutor_profile_evidence e where e.upload_id = u.id)
                    order by u.expires_at
                    limit ?
                    for update of u skip locked)
                update tutor_evidence_uploads u set state = 'DELETED' from due where u.id = due.id
                returning u.object_key
                """).params(at(expiredBefore), at(failedBefore), limit).query(String.class).list();
    }

    /**
     * #182: which of {@code objectKeys} still name bytes in use: any state but DELETED, or linked to a profile
     * version. A key with no row at all is not in use.
     */
    Set<String> inUse(Collection<String> objectKeys) {
        if (objectKeys.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(jdbc.sql("""
                select u.object_key from tutor_evidence_uploads u
                where u.object_key = any(?)
                  and (u.state <> 'DELETED'
                       or exists (select 1 from tutor_profile_evidence e where e.upload_id = u.id))
                """).param(objectKeys.toArray(String[]::new)).query(String.class).list());
    }

    private static OffsetDateTime at(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
