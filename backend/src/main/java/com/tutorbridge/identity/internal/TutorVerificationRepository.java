package com.tutorbridge.identity.internal;

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

    @Transactional(propagation = Propagation.MANDATORY)
    void insertVerification(UUID id, UUID profileId, String scope, String evidenceKey) {
        jdbc.sql("""
                insert into tutor_verifications(id, profile_id, scope, status, evidence_key, revision)
                values (?, ?, ?, 'PENDING', ?, 1)
                """).params(id, profileId, scope, evidenceKey).update();
    }

    /** A verification of one of this tutor's versions; another tutor's is the same as none (INV-06). */
    Optional<Submitted> findOwn(UUID verificationId, UUID tutorId) {
        return jdbc.sql("""
                select v.id, v.profile_id, v.scope from tutor_verifications v
                join tutor_profiles p on p.id = v.profile_id
                where v.id = ? and p.tutor_id = ?
                """).params(verificationId, tutorId).query(Submitted.class).optional();
    }

    private static void requireOne(int updated, String rule) {
        if (updated != 1) {
            throw new IllegalStateException(rule);
        }
    }
}
