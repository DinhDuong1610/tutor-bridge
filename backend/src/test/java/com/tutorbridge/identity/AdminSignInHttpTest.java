package com.tutorbridge.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tutorbridge.Concurrently;
import com.tutorbridge.Contract;
import com.tutorbridge.IntegrationTest;
import com.tutorbridge.identity.internal.AccountSecrets;
import com.tutorbridge.identity.internal.PasswordHasher;
import com.tutorbridge.identity.internal.Totp;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The admin sign-in of TB-13 with the real session resolver: loginAdmin (Q17 on #136), completeAdminMfa
 * with TOTP and getAdminSession (Đính 23/11/2026 on #13). Not an {@code HttpTest}, which swaps the resolver.
 */
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class AdminSignInHttpTest extends IntegrationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PASSWORD = "mat-khau-quan-tri-du-dai";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private AccountSecrets secrets;

    @Autowired
    private PasswordHasher hasher;

    private record Admin(UUID id, String email, byte[] secret) {}

    // ---------------------------------------------------------------- the whole sign-in

    @Test
    void G3_D16_02_an_admin_signs_in_with_the_password_then_a_totp_code_and_gets_an_mfa_session() throws Exception {
        Admin admin = admin("ACTIVE", true);

        JsonNode challenge = challengeOk(admin.email());
        assertThat(challenge.get("mfaRequired").asBoolean()).isTrue();
        assertThat(sessionCount(admin.id())).as("no session before the second factor").isZero();

        JsonNode credential = mfaOk(challenge, currentCode(admin));
        assertThat(credential.get("role").asString()).isEqualTo("ADMIN");
        String token = credential.get("accessToken").asString();
        Map<String, Object> row = jdbc.sql("""
                select admin_mfa_verified_at is not null as mfa, expires_at - created_at = interval '12 hours' as twelve
                from auth_sessions where id = ?""")
                .param(UUID.fromString(credential.get("sessionId").asString())).query().singleRow();
        assertThat(row).containsEntry("mfa", true).containsEntry("twelve", true);
        Instant issued = Instant.parse(credential.get("absoluteExpiresAt").asString()).minusSeconds(12 * 3600);
        assertThat(Instant.parse(credential.get("idleExpiresAt").asString())).isEqualTo(issued.plusSeconds(30 * 60));

        mvc.perform(get("/api/v1/admin/session").cookie(new Cookie("tb_session", token)))
                .andExpect(status().isOk())
                .andExpect(Contract.matches())
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.sessionId").value(credential.get("sessionId").asString()));
    }

    // ---------------------------------------------------------------- loginAdmin

    @Test
    void Q17_an_unknown_email_a_wrong_password_a_non_admin_and_an_admin_without_mfa_get_the_same_401() throws Exception {
        Admin admin = admin("ACTIVE", true);
        Admin withoutMfa = admin("ACTIVE", false);
        Admin revoked = admin("ACTIVE", true);
        jdbc.sql("update admin_mfa_enrollments set revoked_at = now() where account_id = ?").param(revoked.id())
                .update();
        UUID parent = account("PARENT", "ACTIVE", "phu-huynh-" + UUID.randomUUID() + "@example.test");
        String parentEmail = emailOf(parent);

        List<JsonNode> problems = List.of(
                problem(adminLogin("khong-co-" + UUID.randomUUID() + "@example.test", PASSWORD), 401),
                problem(adminLogin(admin.email(), PASSWORD + "x"), 401),
                problem(adminLogin(parentEmail, PASSWORD), 401),
                problem(adminLogin(withoutMfa.email(), PASSWORD), 401),
                problem(adminLogin(revoked.email(), PASSWORD), 401));

        for (JsonNode problem : problems) {
            assertThat(problem.get("code").asString()).isEqualTo("AUTHENTICATION_FAILED");
            for (String field : List.of("type", "title", "status", "code", "detail")) {
                assertThat(problem.get(field)).as(field).isEqualTo(problems.getFirst().get(field));
            }
        }
        // AD01, approved wording.
        assertThat(problems.getFirst().get("title").asString()).isEqualTo("Email hoặc mật khẩu chưa đúng.");
        assertThat(jdbc.sql("select count(*) from auth_challenges where account_id in (?, ?, ?, ?)")
                .params(admin.id(), withoutMfa.id(), revoked.id(), parent).query(Integer.class).single()).isZero();
    }

    @Test
    void Q17_a_locked_admin_is_told_only_after_a_correct_password() throws Exception {
        Admin locked = admin("LOCKED", true);

        adminLogin(locked.email(), PASSWORD).andExpect(status().isForbidden()).andExpect(Contract.matches())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
        adminLogin(locked.email(), PASSWORD + "x").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"));
    }

    @Test
    void Q17_admin_and_parent_sign_in_share_the_failures_of_one_email() throws Exception {
        Admin admin = admin("ACTIVE", true);
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                    .content(JSON.writeValueAsString(Map.of("email", admin.email(), "password", "sai-" + i + "-mat-khau"))))
                    .andExpect(status().isUnauthorized());
        }

        adminLogin(admin.email(), PASSWORD).andExpect(status().isTooManyRequests()).andExpect(Contract.matches())
                .andExpect(jsonPath("$.code").value("LOGIN_DELAYED"))
                .andExpect(header().exists("Retry-After"));
    }

    // ---------------------------------------------------------------- completeAdminMfa

    @Test
    void Q1_of_13_a_wrong_code_counts_and_five_wrong_codes_end_the_challenge() throws Exception {
        Admin admin = admin("ACTIVE", true);
        JsonNode challenge = challengeOk(admin.email());

        JsonNode wrong = problem(mfa(challenge, wrongCode(admin)), 401);
        assertThat(wrong.get("code").asString()).isEqualTo("MFA_CODE_INVALID");
        // AD01, approved wording without the "Lỗi: " label the screen adds.
        assertThat(wrong.get("title").asString()).isEqualTo("Mã chưa đúng hoặc đã hết hạn. Nhập mã mới từ ứng dụng xác thực.");
        assertThat(failedAttempts(challenge)).isEqualTo(1);
        // Wrong codes also count against the email (see below); clear them before the fifth so the delay of
        // the email does not answer before the challenge does.
        for (int i = 0; i < 3; i++) {
            mfa(challenge, wrongCode(admin)).andExpect(status().isUnauthorized());
        }
        clearFailures(admin);
        mfa(challenge, wrongCode(admin)).andExpect(status().isUnauthorized());
        assertThat(failedAttempts(challenge)).isEqualTo(5);

        JsonNode ended = problem(mfa(challenge, currentCode(admin)), 401);
        assertThat(ended.get("code").asString()).isEqualTo("MFA_CHALLENGE_EXPIRED");
        assertThat(sessionCount(admin.id())).isZero();
    }

    @Test
    void Q1_of_13_a_code_already_used_is_refused_on_the_next_sign_in() throws Exception {
        Admin admin = admin("ACTIVE", true);
        String code = currentCode(admin);
        mfaOk(challengeOk(admin.email()), code);

        JsonNode again = problem(mfa(challengeOk(admin.email()), code), 401);

        assertThat(again.get("code").asString()).isEqualTo("MFA_CODE_INVALID");
        assertThat(sessionCount(admin.id())).isEqualTo(1);
    }

    @Test
    void an_unknown_a_used_and_an_expired_challenge_get_the_same_401() throws Exception {
        Admin admin = admin("ACTIVE", true);
        JsonNode used = challengeOk(admin.email());
        mfaOk(used, currentCode(admin));
        JsonNode expired = challengeOk(admin.email());
        jdbc.sql("update auth_challenges set created_at = created_at - interval '10 minutes', "
                + "expires_at = created_at - interval '10 minutes' + interval '5 minutes' where token_digest = ?")
                .param(AccountSecrets.linkTokenDigest(expired.get("challengeToken").asString())).update();
        String fresh = "c".repeat(43);

        for (JsonNode problem : List.of(
                problem(mfaWith(fresh, "123456"), 401),
                problem(mfa(used, nextCode(admin)), 401),
                problem(mfa(expired, nextCode(admin)), 401))) {
            assertThat(problem.get("code").asString()).isEqualTo("MFA_CHALLENGE_EXPIRED");
        }
        assertThat(sessionCount(admin.id())).isEqualTo(1);
    }

    @Test
    void wrong_codes_count_as_failed_sign_ins_so_a_known_password_cannot_try_every_code() throws Exception {
        Admin admin = admin("ACTIVE", true);
        for (int i = 0; i < 5; i++) {
            mfa(challengeOk(admin.email()), wrongCode(admin)).andExpect(status().isUnauthorized());
        }

        adminLogin(admin.email(), PASSWORD).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("LOGIN_DELAYED"));
    }

    @Test
    void a_right_code_clears_the_failures_of_the_email() throws Exception {
        Admin admin = admin("ACTIVE", true);
        adminLogin(admin.email(), PASSWORD + "x").andExpect(status().isUnauthorized());
        mfa(challengeOk(admin.email()), wrongCode(admin)).andExpect(status().isUnauthorized());

        mfaOk(challengeOk(admin.email()), currentCode(admin));

        assertThat(jdbc.sql("select count(*) from auth_login_failures where scope = 'ACCOUNT' and subject_digest = ?")
                .param(secrets.emailDigest(admin.email())).query(Integer.class).single()).isZero();
    }

    @Test
    void INV_20_one_challenge_sent_twice_at_once_with_the_right_code_gives_one_session() throws Exception {
        Admin admin = admin("ACTIVE", true);
        JsonNode challenge = challengeOk(admin.email());
        String code = currentCode(admin);

        List<Integer> statuses = Concurrently.atOnce(2,
                i -> mfa(challenge, code).andReturn().getResponse().getStatus());

        assertThat(statuses).containsExactlyInAnyOrder(200, 401);
        assertThat(sessionCount(admin.id())).isEqualTo(1);
    }

    @Test
    void INV_63_no_password_challenge_code_or_secret_reaches_the_log(CapturedOutput output) throws Exception {
        Admin admin = admin("ACTIVE", true);
        JsonNode challenge = challengeOk(admin.email());
        String code = currentCode(admin);
        JsonNode credential = mfaOk(challenge, code);

        assertThat(output.getAll())
                .doesNotContain(PASSWORD)
                .doesNotContain(challenge.get("challengeToken").asString())
                .doesNotContain(credential.get("accessToken").asString())
                .doesNotContain(Totp.base32(admin.secret()));
    }

    // ---------------------------------------------------------------- getAdminSession

    @Test
    void getAdminSession_without_a_session_is_401_and_with_a_parent_session_is_403() throws Exception {
        mvc.perform(get("/api/v1/admin/session")).andExpect(status().isUnauthorized()).andExpect(Contract.matches());

        UUID parent = account("PARENT", "ACTIVE", "phu-huynh-" + UUID.randomUUID() + "@example.test");
        String token = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(Map.of("email", emailOf(parent), "password", PASSWORD))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String parentToken = JSON.readTree(token).get("accessToken").asString();

        mvc.perform(get("/api/v1/admin/session").cookie(new Cookie("tb_session", parentToken)))
                .andExpect(status().isForbidden())
                .andExpect(Contract.matches())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    // ---------------------------------------------------------------- helpers

    private final Map<UUID, String> emails = new java.util.concurrent.ConcurrentHashMap<>();

    private UUID account(String role, String accountStatus, String email) {
        UUID id = UUID.randomUUID();
        jdbc.sql("insert into accounts (id, role, status, email_digest, email_ciphertext, display_name) values (?, ?, ?, ?, ?, 'Lan')")
                .params(id, role, accountStatus, secrets.emailDigest(email), secrets.encryptEmail(id, email)).update();
        jdbc.sql("insert into account_credentials (account_id, password_hash, hash_policy) values (?, ?, ?)")
                .params(id, hasher.hash(PASSWORD), PasswordHasher.POLICY).update();
        emails.put(id, email);
        return id;
    }

    private String emailOf(UUID id) {
        return emails.get(id);
    }

    private Admin admin(String accountStatus, boolean enrolled) {
        String email = "quan-tri-" + UUID.randomUUID() + "@example.test";
        UUID id = account("ADMIN", accountStatus, email);
        byte[] secret = secrets.newTotpSecret();
        if (enrolled) {
            jdbc.sql("insert into admin_mfa_enrollments (account_id, secret_handle, enrolled_at) values (?, ?, now())")
                    .params(id, secrets.encryptTotpSecret(id, secret)).update();
        }
        return new Admin(id, email, secret);
    }

    private String currentCode(Admin admin) {
        return Totp.code(admin.secret(), Totp.step(Instant.now()));
    }

    private String nextCode(Admin admin) {
        return Totp.code(admin.secret(), Totp.step(Instant.now()) + 1);
    }

    /** A code that matches no step the server may accept now. */
    private String wrongCode(Admin admin) {
        long step = Totp.step(Instant.now());
        Set<String> accepted = Set.of(Totp.code(admin.secret(), step - 1), Totp.code(admin.secret(), step),
                Totp.code(admin.secret(), step + 1), Totp.code(admin.secret(), step + 2));
        String code;
        do {
            code = String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
        } while (accepted.contains(code));
        return code;
    }

    private void clearFailures(Admin admin) {
        jdbc.sql("delete from auth_login_failures where subject_digest = ?").param(secrets.emailDigest(admin.email()))
                .update();
    }

    private int failedAttempts(JsonNode challenge) {
        return jdbc.sql("select failed_attempts from auth_challenges where token_digest = ?")
                .param(AccountSecrets.linkTokenDigest(challenge.get("challengeToken").asString()))
                .query(Integer.class).single();
    }

    private int sessionCount(UUID accountId) {
        return jdbc.sql("select count(*) from auth_sessions where account_id = ?").param(accountId)
                .query(Integer.class).single();
    }

    private ResultActions adminLogin(String email, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/admin-login")
                .with(request -> {
                    request.setRemoteAddr("10.13." + ThreadLocalRandom.current().nextInt(256) + "."
                            + ThreadLocalRandom.current().nextInt(256));
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(Map.of("email", email, "password", password))));
    }

    private JsonNode challengeOk(String email) throws Exception {
        String body = adminLogin(email, PASSWORD)
                .andExpect(status().isOk())
                .andExpect(Contract.matches())
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andReturn().getResponse().getContentAsString();
        return JSON.readTree(body);
    }

    private ResultActions mfa(JsonNode challenge, String code) throws Exception {
        return mfaWith(challenge.get("challengeToken").asString(), code);
    }

    private ResultActions mfaWith(String challengeToken, String code) throws Exception {
        return mvc.perform(post("/api/v1/auth/admin-mfa")
                .with(request -> {
                    request.setRemoteAddr("10.14." + ThreadLocalRandom.current().nextInt(256) + "."
                            + ThreadLocalRandom.current().nextInt(256));
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(Map.of("challengeToken", challengeToken, "code", code))));
    }

    private JsonNode mfaOk(JsonNode challenge, String code) throws Exception {
        String body = mfa(challenge, code)
                .andExpect(status().isOk())
                .andExpect(Contract.matches())
                .andReturn().getResponse().getContentAsString();
        return JSON.readTree(body);
    }

    private JsonNode problem(ResultActions actions, int expected) throws Exception {
        String body = actions.andExpect(status().is(expected)).andExpect(Contract.matches()).andReturn().getResponse()
                .getContentAsString();
        return JSON.readTree(body);
    }
}
