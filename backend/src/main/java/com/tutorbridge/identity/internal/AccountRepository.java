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

/** Identity tables only: accounts, account_credentials, terms_acceptances, auth_challenges. */
@Repository
class AccountRepository {

    static final String VERIFY_EMAIL = "VERIFY_EMAIL";

    record Challenge(String purpose, Instant expiresAt, Instant consumedAt) {}

    private final JdbcClient jdbc;

    AccountRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** False when the email digest is taken; nothing is written then. */
    @Transactional(propagation = Propagation.MANDATORY)
    boolean insertUnverified(UUID id, String role, String emailDigest, byte[] emailCiphertext,
            String displayName, Instant now) {
        return jdbc.sql("""
                insert into accounts (id, role, status, email_digest, email_ciphertext, display_name, created_at)
                values (?, ?, 'UNVERIFIED', ?, ?, ?, ?)
                on conflict (email_digest) do nothing
                """).params(id, role, emailDigest, emailCiphertext, displayName, at(now)).update() == 1;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    void insertCredential(UUID accountId, String passwordHash, String policy, Instant now) {
        jdbc.sql("insert into account_credentials (account_id, password_hash, hash_policy, changed_at) values (?, ?, ?, ?)")
                .params(accountId, passwordHash, policy, at(now)).update();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    void insertTermsAcceptance(UUID accountId, String termsVersion, Instant now) {
        jdbc.sql("insert into terms_acceptances (account_id, terms_version, accepted_at) values (?, ?, ?)")
                .params(accountId, termsVersion, at(now)).update();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    void insertChallenge(UUID id, UUID accountId, String purpose, String tokenDigest, Instant now, Instant expiresAt) {
        jdbc.sql("""
                insert into auth_challenges (id, account_id, purpose, token_digest, created_at, expires_at)
                values (?, ?, ?, ?, ?, ?)
                """).params(id, accountId, purpose, tokenDigest, at(now), at(expiresAt)).update();
    }

    /**
     * Consumes a live code of this purpose. The row lock makes a second, concurrent attempt wait and
     * then find the code consumed. Returns the account, or empty when nothing was consumed.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    Optional<UUID> consumeChallenge(String tokenDigest, String purpose, Instant now) {
        return jdbc.sql("""
                update auth_challenges set consumed_at = :now
                where token_digest = :digest and purpose = :purpose and consumed_at is null and expires_at > :now
                returning account_id
                """).param("now", at(now)).param("digest", tokenDigest).param("purpose", purpose)
                .query(UUID.class).optional();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    Optional<Challenge> findChallenge(String tokenDigest) {
        return jdbc.sql("select purpose, expires_at, consumed_at from auth_challenges where token_digest = ?")
                .param(tokenDigest)
                .query((rs, n) -> new Challenge(rs.getString(1),
                        rs.getObject(2, OffsetDateTime.class).toInstant(),
                        Optional.ofNullable(rs.getObject(3, OffsetDateTime.class))
                                .map(OffsetDateTime::toInstant).orElse(null)))
                .optional();
    }

    /** UNVERIFIED becomes ACTIVE. Returns false when the account was not UNVERIFIED. */
    @Transactional(propagation = Propagation.MANDATORY)
    boolean activate(UUID accountId, Instant now) {
        return jdbc.sql("""
                update accounts set status = 'ACTIVE', email_verified_at = ?, revision = revision + 1
                where id = ? and status = 'UNVERIFIED'
                """).params(at(now), accountId).update() == 1;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    Optional<String> status(UUID accountId) {
        return jdbc.sql("select status from accounts where id = ?").param(accountId).query(String.class).optional();
    }

    /** The UNVERIFIED account with this email, locked until the transaction ends. */
    @Transactional(propagation = Propagation.MANDATORY)
    Optional<UUID> lockUnverifiedByEmail(String emailDigest) {
        return jdbc.sql("select id from accounts where email_digest = ? and status = 'UNVERIFIED' for update")
                .param(emailDigest).query(UUID.class).optional();
    }

    /**
     * The email ciphertext of the account behind a VERIFY_EMAIL code that is still worth sending:
     * unconsumed, unexpired, and the account still UNVERIFIED. Empty otherwise.
     */
    Optional<byte[]> emailToVerify(UUID challengeId, Instant now) {
        return jdbc.sql("""
                select a.email_ciphertext from auth_challenges c join accounts a on a.id = c.account_id
                where c.id = ? and c.purpose = 'VERIFY_EMAIL' and c.consumed_at is null and c.expires_at > ?
                  and a.status = 'UNVERIFIED' and a.email_ciphertext is not null
                """).params(challengeId, at(now)).query(byte[].class).optional();
    }

    private static OffsetDateTime at(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
