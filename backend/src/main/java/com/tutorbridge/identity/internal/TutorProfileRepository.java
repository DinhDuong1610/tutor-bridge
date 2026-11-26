package com.tutorbridge.identity.internal;

import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code tutor_profiles} and the verification state of a version (module identity). Only a DRAFT row
 * is ever updated here; a version sent for verification, approved or rejected stays as it was (INV-54).
 */
@Repository
class TutorProfileRepository {

    record Row(UUID id, int version, long revision, String status, String description, int experienceMonths,
            int rateVnd60) {}

    private static final String COLUMNS =
            "id, version, revision, status, description, experience_months, rate_vnd_60 as rate_vnd60";

    private final JdbcClient jdbc;

    TutorProfileRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Verified tutors only: an UNVERIFIED account has no session, a LOCKED one is refused here. */
    @Transactional(propagation = Propagation.MANDATORY)
    boolean lockActiveTutor(UUID accountId) {
        return jdbc.sql("select 1 from accounts where id = ? and role = 'TUTOR' and status = 'ACTIVE' for share")
                .param(accountId).query((rs, n) -> 1).optional().isPresent();
    }

    Optional<Row> findLatest(UUID tutorId) {
        return jdbc.sql("select " + COLUMNS + " from tutor_profiles where tutor_id = ? order by version desc limit 1")
                .param(tutorId).query(Row.class).optional();
    }

    /** The newest version under a row lock. With no version yet there is nothing to lock: see the unique key. */
    @Transactional(propagation = Propagation.MANDATORY)
    Optional<Row> lockLatest(UUID tutorId) {
        return jdbc.sql("select " + COLUMNS
                        + " from tutor_profiles where tutor_id = ? order by version desc limit 1 for update")
                .param(tutorId).query(Row.class).optional();
    }

    Optional<Row> find(UUID id, UUID tutorId) {
        return jdbc.sql("select " + COLUMNS + " from tutor_profiles where id = ? and tutor_id = ?")
                .params(id, tutorId).query(Row.class).optional();
    }

    /**
     * NEEDS_INFO is a PENDING verification with a reason (G3-D07-05). Without such a verification a
     * PENDING version is waiting, the safe reading when no verification row exists at all.
     */
    boolean needsInformation(UUID profileId) {
        return jdbc.sql("""
                select coalesce(bool_and(reason is not null), false) from tutor_verifications
                where profile_id = ? and status = 'PENDING'
                """).param(profileId).query(Boolean.class).single();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    void insertDraft(UUID id, UUID tutorId, int version, long revision, String description, int experienceMonths,
            int rateVnd60, String subject) {
        jdbc.sql("""
                insert into tutor_profiles(id, tutor_id, version, revision, status, description, experience_months,
                                           rate_vnd_60, subject, current)
                values (?, ?, ?, ?, 'DRAFT', ?, ?, ?, ?, false)
                """).params(id, tutorId, version, revision, description, experienceMonths, rateVnd60, subject)
                .update();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    void updateDraft(UUID id, long revision, String description, int experienceMonths, int rateVnd60, String subject) {
        int updated = jdbc.sql("""
                update tutor_profiles
                set revision = ?, description = ?, experience_months = ?, rate_vnd_60 = ?, subject = ?
                where id = ? and status = 'DRAFT'
                """).params(revision, description, experienceMonths, rateVnd60, subject, id).update();
        if (updated != 1) {
            throw new IllegalStateException("Only a locked DRAFT version is edited in place");
        }
    }
}
