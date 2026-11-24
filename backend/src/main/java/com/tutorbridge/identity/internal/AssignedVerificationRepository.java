package com.tutorbridge.identity.internal;

import com.tutorbridge.platform.api.Cursor;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * What a verifier reads of the verifications assigned to them (TB-13, OP-28): tutor_verifications with the
 * submitted version in tutor_profiles and its evidence in tutor_profile_evidence. Every query filters on
 * {@code assigned_to}, so a record of someone else is never read at all (INV-06). Never selects an email,
 * an evidence object key or hash (AC-UC03-03).
 */
@Repository
class AssignedVerificationRepository {

    /** One record with its version number and the tutor's display name. */
    record Row(UUID id, UUID profileVersionId, int profileVersion, String tutorDisplayName, UUID assignedTo,
            String status, String scope, String reason, Instant submittedAt, Instant reviewedAt, boolean superseded,
            long revision) {}

    /** The submitted version, frozen since it was sent (INV-54). */
    record Profile(UUID id, int version, String subject, String description, int experienceMonths, int rateVnd60) {}

    record Evidence(UUID id, String mediaType, int sizeBytes) {}

    /** A RETIRED version was replaced by its tutor (OP-24): its verification can no longer be decided. */
    private static final String COLUMNS = """
            v.id, v.profile_id, p.version, a.display_name, v.assigned_to, v.status, v.scope, v.reason,
            v.submitted_at, v.reviewed_at, p.status = 'RETIRED' as superseded, v.revision""";

    private final JdbcClient jdbc;

    AssignedVerificationRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    boolean canVerify(UUID accountId) {
        return jdbc.sql("""
                select exists(select 1 from admin_assignments
                              where account_id = ? and capability = 'VERIFY' and revoked_at is null)""")
                .param(accountId).query(Boolean.class).single();
    }

    /**
     * Oldest submission first, keyset on (submitted_at, id), which the V12 index
     * {@code tutor_verifications_assigned_queue} serves. A null status lists every assigned record; PENDING
     * leaves out a superseded one, which waits for nothing (OP-28 (2)).
     */
    List<Row> listAssigned(UUID verifierId, String status, Cursor after, int limit) {
        String byStatus = status == null ? "" : "and v.status = :status";
        String undecidable = "PENDING".equals(status) ? "and p.status <> 'RETIRED'" : "";
        String position = after == null ? "" : "and (v.submitted_at, v.id) > (:afterAt, :afterId)";
        var query = jdbc.sql("""
                select %s
                from tutor_verifications v
                join tutor_profiles p on p.id = v.profile_id
                join accounts a on a.id = p.tutor_id
                where v.assigned_to = :me %s %s %s
                order by v.submitted_at, v.id
                limit :limit
                """.formatted(COLUMNS, byStatus, undecidable, position))
                .param("me", verifierId).param("limit", limit);
        if (status != null) {
            query = query.param("status", status);
        }
        if (after != null) {
            query = query.param("afterAt", at(after.at())).param("afterId", after.id());
        }
        return query.query(AssignedVerificationRepository::row).list();
    }

    /** The record and its version in one statement, so both come from the same snapshot. */
    Optional<Detail> findAssigned(UUID verificationId, UUID verifierId) {
        return jdbc.sql("""
                select %s, p.subject, p.description, p.experience_months, p.rate_vnd_60
                from tutor_verifications v
                join tutor_profiles p on p.id = v.profile_id
                join accounts a on a.id = p.tutor_id
                where v.id = ? and v.assigned_to = ?
                """.formatted(COLUMNS))
                .params(verificationId, verifierId)
                .query((rs, n) -> new Detail(row(rs, n), new Profile(rs.getObject("profile_id", UUID.class),
                        rs.getInt("version"), rs.getString("subject"), rs.getString("description"),
                        rs.getInt("experience_months"), rs.getInt("rate_vnd_60"))))
                .optional();
    }

    record Detail(Row verification, Profile profile) {}

    /** The evidence the version was submitted with; frozen with it (V11 guard), so a second read is safe. */
    List<Evidence> evidenceOf(UUID profileId) {
        return jdbc.sql("""
                select u.id, u.media_type, u.size_bytes
                from tutor_profile_evidence e join tutor_evidence_uploads u on u.id = e.upload_id
                where e.profile_id = ?
                order by u.id
                """).param(profileId).query(Evidence.class).list();
    }

    record StoredEvidence(String objectKey, int sizeBytes) {}

    /**
     * One READY evidence file of the version a record assigned to the verifier was submitted with. Any other
     * case, another version's file included, is simply missing (INV-06). The key stays in this module.
     */
    Optional<StoredEvidence> findAssignedEvidence(UUID verificationId, UUID verifierId, UUID evidenceId) {
        return jdbc.sql("""
                select u.object_key, u.size_bytes
                from tutor_verifications v
                join tutor_profile_evidence e on e.profile_id = v.profile_id
                join tutor_evidence_uploads u on u.id = e.upload_id
                where v.id = ? and v.assigned_to = ? and u.id = ? and u.state = 'READY'
                """).params(verificationId, verifierId, evidenceId).query(StoredEvidence.class).optional();
    }

    // ---------------------------------------------------------------- decideVerification (VerificationDecisions)

    /** The verifier's own account, FOR SHARE, first in the lock order (INV-16). */
    @Transactional(propagation = Propagation.MANDATORY)
    boolean lockActiveAdmin(UUID accountId) {
        return jdbc.sql("select 1 from accounts where id = ? and role = 'ADMIN' and status = 'ACTIVE' for share")
                .param(accountId).query((rs, n) -> 1).optional().isPresent();
    }

    record Target(UUID profileId, UUID tutorId) {}

    /** Which version and tutor a record is about, read without a lock to know what to lock next. */
    Optional<Target> findTarget(UUID verificationId) {
        return jdbc.sql("""
                select v.profile_id, p.tutor_id from tutor_verifications v join tutor_profiles p on p.id = v.profile_id
                where v.id = ?""").param(verificationId).query(Target.class).optional();
    }

    record Version(UUID id, int version, String status, boolean current) {}

    /** The record's version and the tutor's current approved one, FOR UPDATE by version (INV-16). */
    @Transactional(propagation = Propagation.MANDATORY)
    List<Version> lockVersions(UUID tutorId, UUID profileId) {
        return jdbc.sql("""
                select id, version, status, current from tutor_profiles where tutor_id = ? and (id = ? or current)
                order by version for update""").params(tutorId, profileId).query(Version.class).list();
    }

    record Locked(UUID id, UUID profileId, UUID assignedTo, String status, long revision) {}

    @Transactional(propagation = Propagation.MANDATORY)
    Optional<Locked> lockVerification(UUID verificationId) {
        return jdbc.sql("""
                select id, profile_id, assigned_to, status, revision from tutor_verifications where id = ? for update""")
                .param(verificationId).query(Locked.class).optional();
    }

    /** The VERIFY row, FOR SHARE and last (INV-16): a revocation waits for this decision, or this sees it. */
    @Transactional(propagation = Propagation.MANDATORY)
    boolean lockVerify(UUID accountId) {
        return jdbc.sql("""
                select 1 from admin_assignments where account_id = ? and capability = 'VERIFY' and revoked_at is null
                for share""").param(accountId).query((rs, n) -> 1).optional().isPresent();
    }

    /** Whether the record is still assigned to this verifier, for a replay (INV-22). */
    boolean isAssigned(UUID verificationId, UUID verifierId) {
        return jdbc.sql("select exists(select 1 from tutor_verifications where id = ? and assigned_to = ?)")
                .params(verificationId, verifierId).query(Boolean.class).single();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    void decide(UUID verificationId, String status, String scope, String reason, UUID reviewer, Instant now,
            long revision) {
        int updated = jdbc.sql("""
                update tutor_verifications
                set status = ?, scope = ?, reason = ?, reviewed_by = ?, reviewed_at = ?, revision = ?
                where id = ? and revision = ?""")
                .params(status, scope, reason, reviewer, at(now), revision, verificationId, revision - 1).update();
        if (updated != 1) {
            throw new IllegalStateException("Only the locked revision is decided");
        }
    }

    /** OP-24 (1): the old current version stops being shown; it stays APPROVED for the record. */
    @Transactional(propagation = Propagation.MANDATORY)
    void retireCurrent(UUID profileId) {
        jdbc.sql("update tutor_profiles set current = false, revision = revision + 1 where id = ? and current")
                .param(profileId).update();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    void settleVersion(UUID profileId, String status, boolean current) {
        int updated = jdbc.sql("""
                update tutor_profiles set status = ?, current = ?, revision = revision + 1
                where id = ? and status = 'PENDING'""").params(status, current, profileId).update();
        if (updated != 1) {
            throw new IllegalStateException("Only a locked PENDING version is settled");
        }
    }

    private static Row row(ResultSet rs, int n) throws SQLException {
        return new Row(rs.getObject("id", UUID.class), rs.getObject("profile_id", UUID.class), rs.getInt("version"),
                rs.getString("display_name"), rs.getObject("assigned_to", UUID.class), rs.getString("status"),
                rs.getString("scope"), rs.getString("reason"), instant(rs, "submitted_at"),
                instant(rs, "reviewed_at"), rs.getBoolean("superseded"), rs.getLong("revision"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private static OffsetDateTime at(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
