package com.tutorbridge.identity.internal;

import com.tutorbridge.platform.api.Actor;
import com.tutorbridge.platform.api.Cursor;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
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
 * auth_sessions, account_credentials and auth_login_failures. Session lifetimes follow UC01 and the
 * {@code valid_session} function of V5: 24 h idle and 7 days absolute; ADMIN 30 min and 12 h.
 */
@Repository
class SessionRepository {

    static final Duration IDLE = Duration.ofHours(24);
    static final Duration ABSOLUTE = Duration.ofDays(7);

    record Login(UUID accountId, String role, String status, long authEpoch, String passwordHash, String hashPolicy) {}

    record Live(UUID sessionId, UUID accountId, Actor.Role role, Instant lastSeenAt) {}

    record Listed(UUID id, String deviceLabel, Instant createdAt, Instant lastSeenAt, Instant expiresAt,
            boolean revoked) {}

    private final JdbcClient jdbc;

    SessionRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    Optional<Login> findLogin(String emailDigest) {
        return jdbc.sql("""
                select a.id, a.role, a.status, a.auth_epoch, c.password_hash, c.hash_policy
                from accounts a left join account_credentials c on c.account_id = a.id
                where a.email_digest = ?
                """).param(emailDigest)
                .query((rs, n) -> new Login(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                        rs.getLong(4), rs.getString(5), rs.getString(6)))
                .optional();
    }

    record Own(String status, long authEpoch, String emailDigest, String passwordHash) {}

    /** The signed-in account's password and the email digest its failures are counted under. */
    Optional<Own> findOwn(UUID accountId) {
        return jdbc.sql("""
                select a.status, a.auth_epoch, a.email_digest, c.password_hash
                from accounts a left join account_credentials c on c.account_id = a.id where a.id = ?
                """).param(accountId)
                .query((rs, n) -> new Own(rs.getString(1), rs.getLong(2), rs.getString(3), rs.getString(4)))
                .optional();
    }

    /** Failed attempts in the window, newest first, at most {@code limit}. */
    List<Instant> recentFailures(String scope, String subjectDigest, Instant since, int limit) {
        return jdbc.sql("""
                select failed_at from auth_login_failures
                where scope = ? and subject_digest = ? and failed_at > ?
                order by failed_at desc limit ?
                """).params(scope, subjectDigest, at(since), limit)
                .query((rs, n) -> rs.getObject(1, OffsetDateTime.class).toInstant()).list();
    }

    /** The row id, so a try that turns out to be correct can take its row back (Issue #141). */
    long recordFailure(String scope, String subjectDigest, Instant now) {
        return jdbc.sql("insert into auth_login_failures (scope, subject_digest, failed_at) values (?, ?, ?) returning id")
                .params(scope, subjectDigest, at(now)).query(Long.class).single();
    }

    void deleteFailures(List<Long> ids) {
        jdbc.sql("delete from auth_login_failures where id in (:ids)").param("ids", ids).update();
    }

    /**
     * Serializes every try against one login subject until the transaction ends, so counting and
     * recording a try is atomic across requests and API instances (ADR-0007; no session-level lock
     * because of the pooler, INV-83).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    void lockSubject(String scope, String subjectDigest) {
        jdbc.sql("select pg_advisory_xact_lock(hashtextextended(?, 0))")
                .param("LOGIN:" + scope + ":" + subjectDigest).query((rs, row) -> 1).single();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    void clearAccountFailures(String emailDigest) {
        jdbc.sql("delete from auth_login_failures where scope = 'ACCOUNT' and subject_digest = ?")
                .param(emailDigest).update();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    void rehash(UUID accountId, String passwordHash, String policy, Instant now) {
        jdbc.sql("update account_credentials set password_hash = ?, hash_policy = ?, changed_at = ? where account_id = ?")
                .params(passwordHash, policy, at(now), accountId).update();
    }

    /** Fails with no row when the account changed since {@code findLogin}: status or epoch moved. */
    @Transactional(propagation = Propagation.MANDATORY)
    boolean insertSession(UUID id, UUID accountId, long authEpoch, String tokenDigest, String deviceLabel,
            Instant now) {
        return jdbc.sql("""
                insert into auth_sessions (id, account_id, token_digest, expires_at, issued_epoch, created_at,
                                           last_seen_at, device_label)
                select ?, a.id, ?, ?, a.auth_epoch, ?, ?, ?
                from accounts a where a.id = ? and a.status = 'ACTIVE' and a.auth_epoch = ?
                for share of a
                """).params(id, tokenDigest, at(now.plus(ABSOLUTE)), at(now), at(now), deviceLabel, accountId,
                        authEpoch).update() == 1;
    }

    /** The same rules as V5 {@code valid_session}, with time from the application clock. */
    Optional<Live> resolve(String tokenDigest, Instant now) {
        return jdbc.sql("""
                select s.id, s.account_id, a.role, s.last_seen_at
                from auth_sessions s join accounts a on a.id = s.account_id
                where s.token_digest = :digest and s.revoked_at is null and a.status = 'ACTIVE'
                  and s.issued_epoch = a.auth_epoch and s.expires_at > :now
                  and s.created_at <= :now and s.last_seen_at <= :now
                  and s.created_at > :now - case when a.role = 'ADMIN' then interval '12 hours' else interval '7 days' end
                  and s.last_seen_at > :now - case when a.role = 'ADMIN' then interval '30 minutes' else interval '24 hours' end
                  and (a.role <> 'ADMIN' or (s.admin_mfa_verified_at is not null and exists (
                       select 1 from admin_mfa_enrollments e where e.account_id = a.id and e.revoked_at is null)))
                """).param("digest", tokenDigest).param("now", at(now))
                .query((rs, n) -> new Live(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                        Actor.Role.valueOf(rs.getString(3)), instant(rs, 4)))
                .optional();
    }

    /** Writes last_seen_at at most every five minutes (Q11 of TB-8). */
    void touch(UUID sessionId, Instant now) {
        jdbc.sql("""
                update auth_sessions set last_seen_at = :now
                where id = :id and last_seen_at < :now - interval '5 minutes'
                """).param("now", at(now)).param("id", sessionId).update();
    }

    /** Revokes one session of this account. False when it is not this account's session. */
    boolean revoke(UUID sessionId, UUID accountId, Instant now) {
        int changed = jdbc.sql("""
                update auth_sessions set revoked_at = coalesce(revoked_at, ?) where id = ? and account_id = ?
                """).params(at(now), sessionId, accountId).update();
        return changed == 1;
    }

    /**
     * Own sessions still within their time limits, revoked ones included so the list can say so.
     * Newest first; keyset by (created_at, id). Never selects token_digest.
     */
    List<Listed> listOwn(UUID accountId, Instant now, Cursor after, int limit) {
        String position = after == null ? "" : "and (s.created_at, s.id) < (:afterAt, :afterId)";
        var query = jdbc.sql("""
                select s.id, s.device_label, s.created_at, s.last_seen_at,
                       least(s.expires_at, s.last_seen_at + case when a.role = 'ADMIN' then interval '30 minutes'
                                                              else interval '24 hours' end) as effective_expiry,
                       (s.revoked_at is not null or s.issued_epoch <> a.auth_epoch) as revoked
                from auth_sessions s join accounts a on a.id = s.account_id
                where s.account_id = :account and s.expires_at > :now
                  and s.last_seen_at > :now - case when a.role = 'ADMIN' then interval '30 minutes'
                                                   else interval '24 hours' end
                  %s
                order by s.created_at desc, s.id desc
                limit :limit
                """.formatted(position)).param("account", accountId).param("now", at(now)).param("limit", limit);
        if (after != null) {
            query = query.param("afterAt", at(after.at())).param("afterId", after.id());
        }
        return query.query((rs, n) -> new Listed(rs.getObject(1, UUID.class), rs.getString(2), instant(rs, 3),
                instant(rs, 4), instant(rs, 5), rs.getBoolean(6))).list();
    }

    private static Instant instant(ResultSet rs, int column) throws SQLException {
        return rs.getObject(column, OffsetDateTime.class).toInstant();
    }

    private static OffsetDateTime at(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
