package com.tutorbridge.identity.internal;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * What submitting a profile version writes (module identity): {@code tutor_verifications},
 * {@code tutor_profile_evidence} and the version's move out of DRAFT. Every lock here follows the order
 * of {@link TutorProfileSubmissions}: versions before their verifications, then the evidence (INV-16).
 */
@Repository
class TutorVerificationRepository {

    record PendingVerification(UUID id, String reason) {}

    record Evidence(UUID id, UUID ownerId, String state) {}

    record Submitted(UUID id, UUID profileId, String scope) {}

    private final JdbcClient jdbc;

    TutorVerificationRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** The tutor's other versions that were sent for verification and have no decision yet. */
    @Transactional(propagation = Propagation.MANDATORY)
    List<UUID> lockOtherPendingVersions(UUID tutorId, UUID newest) {
        return jdbc.sql("""
                select id from tutor_profiles where tutor_id = ? and status = 'PENDING' and id <> ?
                order by version for update
                """).params(tutorId, newest).query(UUID.class).list();
    }

    /** Called only after the version itself is locked, which the V11 guard on verifications reads. */
    @Transactional(propagation = Propagation.MANDATORY)
    List<PendingVerification> lockPendingVerifications(UUID profileId) {
        return jdbc.sql("""
                select id, reason from tutor_verifications where profile_id = ? and status = 'PENDING'
                order by id for update
                """).param(profileId).query(PendingVerification.class).list();
    }

    /** The evidence rows the submission names, in a stable order. Unknown ids are simply missing. */
    @Transactional(propagation = Propagation.MANDATORY)
    List<Evidence> lockEvidence(List<UUID> ids) {
        return jdbc.sql("""
                select id, owner_id, state from tutor_evidence_uploads where id in (:ids)
                order by id for share
                """).param("ids", ids).query(Evidence.class).list();
    }

    String subjectOf(UUID profileId) {
        return jdbc.sql("select subject from tutor_profiles where id = ?").param(profileId).query(String.class)
                .single();
    }

    /** OP-24: a version that needed information is replaced; its verification stays as the verifier left it. */
    @Transactional(propagation = Propagation.MANDATORY)
    void retire(UUID profileId) {
        requireOne(jdbc.sql("update tutor_profiles set status = 'RETIRED' where id = ? and status = 'PENDING'")
                .param(profileId).update(), "Only a locked PENDING version is retired");
    }

    @Transactional(propagation = Propagation.MANDATORY)
    void link(UUID profileId, UUID uploadId) {
        jdbc.sql("insert into tutor_profile_evidence(profile_id, upload_id) values (?, ?)").params(profileId, uploadId)
                .update();
    }

    /** INV-54: from here on the version and its evidence are frozen. */
    @Transactional(propagation = Propagation.MANDATORY)
    void submit(UUID profileId, long revision) {
        requireOne(jdbc.sql("update tutor_profiles set status = 'PENDING', revision = ? where id = ? and status = 'DRAFT'")
                .params(revision, profileId).update(), "Only a locked DRAFT version is submitted");
    }

    /** {@code assignedTo} null when no verifier is there yet (decision 4 of TB-13). */
    @Transactional(propagation = Propagation.MANDATORY)
    void insertVerification(UUID id, UUID profileId, String scope, String evidenceKey, UUID assignedTo) {
        jdbc.sql("""
                insert into tutor_verifications(id, profile_id, scope, status, evidence_key, assigned_to, assigned_at,
                                                revision)
                values (?, ?, ?, 'PENDING', ?, ?, case when ?::uuid is null then null else clock_timestamp() end, 1)
                """).params(id, profileId, scope, evidenceKey, assignedTo, assignedTo).update();
    }

    /**
     * A verification of one of this tutor's versions; another tutor's is the same as none (INV-06). The
     * scope is the submitted version's subject, frozen by INV-54, which is what the first answer gave: a
     * verifier may change the verification's own scope later, and a replay must not show it (INV-20).
     */
    Optional<Submitted> findOwn(UUID verificationId, UUID tutorId) {
        return jdbc.sql("""
                select v.id, v.profile_id, p.subject as scope from tutor_verifications v
                join tutor_profiles p on p.id = v.profile_id
                where v.id = ? and p.tutor_id = ?
                """).params(verificationId, tutorId).query(Submitted.class).optional();
    }

    record OwnVerification(UUID id, UUID profileVersionId, int profileVersion, String status, String scope,
            String reason, Instant reviewedAt, Integer approvedVersion) {}

    /**
     * What the tutor may read about their own verification: the verification of their most recently
     * submitted version and the version parents see now ({@code current}), in one statement so both come
     * from the same snapshot. A version is submitted once, so it has at most one verification; the order
     * after the version only keeps the answer stable. Never selects the verifier or the evidence.
     */
    Optional<OwnVerification> findLatestOwn(UUID tutorId) {
        return jdbc.sql("""
                select v.id, p.id as profile_version_id, p.version as profile_version, v.status, v.scope, v.reason,
                       v.reviewed_at,
                       (select c.version from tutor_profiles c where c.tutor_id = p.tutor_id and c.current)
                           as approved_version
                from tutor_verifications v
                join tutor_profiles p on p.id = v.profile_id
                where p.tutor_id = ?
                order by p.version desc, (v.status = 'PENDING') desc, v.reviewed_at desc nulls first, v.id
                limit 1
                """).param(tutorId).query(OwnVerification.class).optional();
    }

    private static void requireOne(int updated, String rule) {
        if (updated != 1) {
            throw new IllegalStateException(rule);
        }
    }
}
