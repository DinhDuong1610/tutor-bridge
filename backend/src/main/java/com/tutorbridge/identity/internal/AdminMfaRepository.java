package com.tutorbridge.identity.internal;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * ADMIN_MFA challenges in auth_challenges and the TOTP enrollments in admin_mfa_enrollments (TB-13, V5 and
 * V16). The challenge stores only the SHA-256 of its token; the enrollment only an encrypted secret.
 */
@Repository
class AdminMfaRepository {

    static final String PURPOSE = "ADMIN_MFA";

    /** The account behind a challenge, read without a lock, to count the try against its email first. */
    record ChallengeOwner(UUID accountId, String emailDigest) {}

    /** A challenge under its row lock, with the account and its live enrollment (null when revoked). */
    record Locked(UUID id, UUID accountId, String accountStatus, String role, long authEpoch, Instant expiresAt,
            Instant consumedAt, int failedAttempts, String secretHandle, Long lastUsedStep) {}

    private final JdbcClient jdbc;

    AdminMfaRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    boolean hasLiveEnrollment(UUID accountId) {
        return jdbc.sql("select exists(select 1 from admin_mfa_enrollments where account_id = ? and revoked_at is null)")
                .param(accountId).query(Boolean.class).single();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    void insertChallenge(UUID id, UUID accountId, String tokenDigest, Instant now, Instant expiresAt) {
        jdbc.sql("""
                insert into auth_challenges (id, account_id, purpose, token_digest, created_at, expires_at)
                values (?, ?, ?, ?, ?, ?)""")
                .params(id, accountId, PURPOSE, tokenDigest, at(now), at(expiresAt)).update();
    }

    Optional<ChallengeOwner> findOwner(String tokenDigest) {
        return jdbc.sql("""
                select a.id, a.email_digest from auth_challenges c join accounts a on a.id = c.account_id
                where c.token_digest = ? and c.purpose = ?""")
                .params(tokenDigest, PURPOSE)
                .query((rs, n) -> new ChallengeOwner(rs.getObject(1, UUID.class), rs.getString(2)))
                .optional();
    }

    /**
     * Locks the challenge row, so two tries of one challenge run one after the other (INV-16: the
     * challenge, then the enrollment, then the account the session insert shares).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    Optional<Locked> lock(String tokenDigest) {
        return jdbc.sql("""
                select c.id, c.account_id, a.status, a.role, a.auth_epoch, c.expires_at, c.consumed_at,
                       c.failed_attempts, e.secret_handle, e.last_used_step
                from auth_challenges c
                join accounts a on a.id = c.account_id
                left join admin_mfa_enrollments e on e.account_id = c.account_id and e.revoked_at is null
                where c.token_digest = ? and c.purpose = ?
                for update of c""")
                .params(tokenDigest, PURPOSE)
                .query(AdminMfaRepository::locked).optional();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    void countWrongCode(UUID challengeId) {
        jdbc.sql("update auth_challenges set failed_attempts = failed_attempts + 1 where id = ?").param(challengeId)
                .update();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    void consume(UUID challengeId, Instant now) {
        jdbc.sql("update auth_challenges set consumed_at = ? where id = ? and consumed_at is null")
                .params(at(now), challengeId).update();
    }

    /**
     * Records the accepted step, only when it is after the last one: false when another sign-in of this
     * admin took this step or a later one meanwhile, so one code never opens two sessions.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    boolean useStep(UUID accountId, long step) {
        return jdbc.sql("""
                update admin_mfa_enrollments set last_used_step = ?
                where account_id = ? and revoked_at is null and (last_used_step is null or last_used_step < ?)""")
                .params(step, accountId, step).update() == 1;
    }

    private static Locked locked(ResultSet rs, int n) throws SQLException {
        return new Locked(rs.getObject("id", UUID.class), rs.getObject("account_id", UUID.class),
                rs.getString("status"), rs.getString("role"), rs.getLong("auth_epoch"), instant(rs, "expires_at"),
                instant(rs, "consumed_at"), rs.getInt("failed_attempts"), rs.getString("secret_handle"),
                rs.getObject("last_used_step", Long.class));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private static OffsetDateTime at(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
