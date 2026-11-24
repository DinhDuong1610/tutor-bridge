package com.tutorbridge.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tutorbridge.IntegrationTest;
import com.tutorbridge.identity.internal.AccountSecrets;
import com.tutorbridge.identity.internal.AdminAccounts;
import com.tutorbridge.identity.internal.AdminSignIn;
import com.tutorbridge.identity.internal.Totp;
import com.tutorbridge.platform.api.ApiException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The admin-setup command of TB-13 (Đính 23/11/2026 on #13): creates or resets an ADMIN with a password, a
 * new TOTP enrollment and the VERIFY capability, and hands the enrollment URI over in a file, never a log.
 */
class AdminAccountsTest extends IntegrationTest {

    private static final String PASSWORD = "mat-khau-quan-tri-du-dai";
    private static final Pattern SECRET = Pattern.compile("secret=([A-Z2-7]+)&");

    @Autowired
    private AdminAccounts accounts;

    @Autowired
    private AdminSignIn signIn;

    @Autowired
    private AccountSecrets secrets;

    @Test
    void a_new_admin_gets_a_password_an_enrollment_and_verify_and_can_sign_in() throws IOException {
        String email = "quan-tri-" + UUID.randomUUID() + "@example.test";
        Path file = fileFor(email);

        UUID id = accounts.setUp(email, PASSWORD, file);

        assertThat(jdbc.sql("select role || ':' || status from accounts where id = ?").param(id).query(String.class)
                .single()).isEqualTo("ADMIN:ACTIVE");
        assertThat(jdbc.sql("""
                select count(*) from admin_assignments where account_id = ? and capability = 'VERIFY' and revoked_at is null""")
                .param(id).query(Integer.class).single()).isEqualTo(1);
        String uri = Files.readString(file).strip();
        assertThat(uri).startsWith("otpauth://totp/TutorBridge:");
        String handle = jdbc.sql("select secret_handle from admin_mfa_enrollments where account_id = ?").param(id)
                .query(String.class).single();
        assertThat(handle).startsWith("v1:").doesNotContain(secretOf(uri));

        signIn(email, uri);
    }

    @Test
    void running_it_again_replaces_the_secret_and_ends_the_old_sessions() throws IOException {
        String email = "quan-tri-" + UUID.randomUUID() + "@example.test";
        UUID id = accounts.setUp(email, PASSWORD, fileFor(email));
        String first = Files.readString(fileFor(email)).strip();
        signIn(email, first);
        long epoch = jdbc.sql("select auth_epoch from accounts where id = ?").param(id).query(Long.class).single();

        Path second = fileFor(email + ".2");
        assertThat(accounts.setUp(email, PASSWORD + "-moi", second)).isEqualTo(id);

        assertThat(secretOf(Files.readString(second))).isNotEqualTo(secretOf(first));
        assertThat(jdbc.sql("select auth_epoch from accounts where id = ?").param(id).query(Long.class).single())
                .isGreaterThan(epoch);
        assertThatThrownBy(() -> signIn.login(email, PASSWORD, "10.20.0.1")).isInstanceOf(ApiException.class);
        signIn(email, Files.readString(second).strip(), PASSWORD + "-moi");
    }

    @Test
    void an_email_of_a_parent_or_tutor_is_refused_and_nothing_changes() {
        String email = "phu-huynh-" + UUID.randomUUID() + "@example.test";
        UUID parent = UUID.randomUUID();
        jdbc.sql("insert into accounts (id, role, status, email_digest, email_ciphertext) values (?, 'PARENT', 'ACTIVE', ?, ?)")
                .params(parent, secrets.emailDigest(email), secrets.encryptEmail(parent, email)).update();

        assertThatThrownBy(() -> accounts.setUp(email, PASSWORD, fileFor(email)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("not an ADMIN");
        assertThat(Files.exists(fileFor(email))).isFalse();
        assertThat(jdbc.sql("select count(*) from admin_mfa_enrollments where account_id = ?").param(parent)
                .query(Integer.class).single()).isZero();
    }

    @Test
    void a_short_password_or_an_existing_file_is_refused_before_anything_is_written() throws IOException {
        String email = "quan-tri-" + UUID.randomUUID() + "@example.test";
        assertThatThrownBy(() -> accounts.setUp(email, "ngan-qua", fileFor(email)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("15");

        Path existing = fileFor(email);
        Files.writeString(existing, "da co");
        assertThatThrownBy(() -> accounts.setUp(email, PASSWORD, existing))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("exists");
        assertThat(Files.readString(existing)).isEqualTo("da co");
        assertThat(jdbc.sql("select count(*) from accounts where email_digest = ?").param(secrets.emailDigest(email))
                .query(Integer.class).single()).isZero();
    }

    @Test
    void INV_18_the_file_gets_its_name_only_after_the_commit_and_a_refused_run_leaves_no_staged_file() throws Exception {
        String email = "quan-tri-" + UUID.randomUUID() + "@example.test";
        UUID id = accounts.setUp(email, PASSWORD, fileFor(email));
        Path second = fileFor(email + ".lan-2");

        java.util.concurrent.CompletableFuture<UUID> reset;
        try (java.sql.Connection gate = java.sql.DriverManager.getConnection(
                com.tutorbridge.PostgresTestSupport.jdbcUrl(), "tutorbridge_app",
                com.tutorbridge.PostgresTestSupport.APP_PASSWORD)) {
            gate.setAutoCommit(false);
            gate.createStatement().execute("select 1 from admin_mfa_enrollments where account_id = '" + id + "' for update");
            reset = com.tutorbridge.Concurrently.supply(() -> accounts.setUp(email, PASSWORD, second));
            org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(10))
                    .pollInterval(java.time.Duration.ofMillis(20)).until(() -> jdbc.sql("""
                            select count(*) from pg_stat_activity
                            where wait_event_type = 'Lock' and query like 'select 1 from admin_mfa_enrollments%'""")
                            .query(Integer.class).single() == 1);
            // The transaction is open and waiting: the URI is staged, but not under its name yet.
            assertThat(Files.exists(second)).isFalse();
            assertThat(staged()).hasSize(1);
            gate.commit();
        }
        assertThat(reset.get(10, java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(id);
        assertThat(Files.readString(second)).startsWith("otpauth://totp/");
        assertThat(staged()).isEmpty();

        // A transaction that refuses removes what it staged.
        String parent = "phu-huynh-" + UUID.randomUUID() + "@example.test";
        UUID parentId = UUID.randomUUID();
        jdbc.sql("insert into accounts (id, role, status, email_digest, email_ciphertext) values (?, 'PARENT', 'ACTIVE', ?, ?)")
                .params(parentId, secrets.emailDigest(parent), secrets.encryptEmail(parentId, parent)).update();
        assertThatThrownBy(() -> accounts.setUp(parent, PASSWORD, fileFor(parent)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(staged()).isEmpty();
    }

    @Test
    void a_folder_that_does_not_exist_is_refused_before_anything_is_written() {
        String email = "quan-tri-" + UUID.randomUUID() + "@example.test";
        Path nowhere = evidenceDir().resolve("khong-co-" + UUID.randomUUID()).resolve("uri.txt");

        assertThatThrownBy(() -> accounts.setUp(email, PASSWORD, nowhere))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("does not exist");
        assertThat(jdbc.sql("select count(*) from accounts where email_digest = ?").param(secrets.emailDigest(email))
                .query(Integer.class).single()).isZero();
    }

    /** Files this command staged in the test folder and did not move or delete. */
    private static java.util.List<Path> staged() throws IOException {
        try (var files = Files.list(evidenceDir())) {
            return files.filter(f -> f.getFileName().toString().startsWith(".admin-setup-")).toList();
        }
    }

    private void signIn(String email, String uri) {
        signIn(email, uri, PASSWORD);
    }

    private void signIn(String email, String uri, String password) {
        AdminSignIn.Challenge challenge = signIn.login(email, password, "10.20.0." + (int) (Math.random() * 250));
        byte[] secret = base32Decode(secretOf(uri));
        assertThat(signIn.completeMfa(challenge.challengeToken(), Totp.code(secret, Totp.step(Instant.now())),
                "10.21.0.1", null).role()).isEqualTo("ADMIN");
    }

    private static String secretOf(String uri) {
        Matcher m = SECRET.matcher(uri);
        assertThat(m.find()).isTrue();
        return m.group(1);
    }

    private static byte[] base32Decode(String text) {
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int buffer = 0;
        int bits = 0;
        for (char c : text.toCharArray()) {
            buffer = (buffer << 5) | alphabet.indexOf(c);
            bits += 5;
            if (bits >= 8) {
                out.write((buffer >> (bits - 8)) & 0xff);
                bits -= 8;
            }
        }
        return out.toByteArray();
    }

    private static Path fileFor(String email) {
        // The per-run directory under build/, so a file of an earlier run never gets in the way.
        return evidenceDir().resolve("admin-setup-" + email.hashCode() + "-" + email.length() + ".txt");
    }
}
