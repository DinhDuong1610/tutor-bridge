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
