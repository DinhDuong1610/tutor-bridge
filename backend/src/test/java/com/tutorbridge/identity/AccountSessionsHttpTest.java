package com.tutorbridge.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * UC01 steps 3–5 with the real session resolver: login, every request checks the session in the
 * database, logout, list and revoke (TB-8 PR 3). Not an {@code HttpTest}, which swaps the resolver.
 */
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class AccountSessionsHttpTest extends IntegrationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PASSWORD = "mot-mat-khau-du-dai-15";
    private static final String ANDROID = "okhttp/4.12.0";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private AccountSecrets secrets;

    @Autowired
    private PasswordHasher hasher;

    // ---------------------------------------------------------------- loginAccount

    @Test
    void login_issues_an_opaque_session_that_the_next_request_accepts() throws Exception {
        Account account = account("PARENT", "ACTIVE");
        JsonNode credential = loginOk(account.email(), randomIp());

        assertThat(credential.get("role").asString()).isEqualTo("PARENT");
        String token = credential.get("accessToken").asString();
        assertThat(token).matches("[A-Za-z0-9_-]{43}");
        Map<String, Object> row = jdbc.sql("""
                select token_digest, device_label, expires_at - created_at = interval '7 days' as seven_days
                from auth_sessions where id = ?
                """).param(UUID.fromString(credential.get("sessionId").asString())).query().singleRow();
        assertThat(row).containsEntry("token_digest", AccountSecrets.linkTokenDigest(token))
                .containsEntry("device_label", "Android").containsEntry("seven_days", true);

        authed(get("/api/v1/me/sessions"), token)
                .andExpect(status().isOk())
                .andExpect(Contract.matches())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].current").value(true))
                .andExpect(jsonPath("$.items[0].deviceLabel").value("Android"))
                .andExpect(jsonPath("$.items[0].revoked").value(false));
    }

    @Test
    void an_unknown_email_and_a_wrong_password_get_the_same_401() throws Exception {
        Account account = account("TUTOR", "ACTIVE");
        JsonNode unknown = problem(login("khong-co-" + UUID.randomUUID() + "@example.test", PASSWORD, randomIp())
                .andExpect(status().isUnauthorized()).andExpect(Contract.matches()).andReturn());
        JsonNode wrong = problem(login(account.email(), PASSWORD + "x", randomIp())
                .andExpect(status().isUnauthorized()).andExpect(Contract.matches()).andReturn());

        assertThat(unknown.get("code").asString()).isEqualTo("AUTHENTICATION_FAILED");
        for (String field : List.of("type", "title", "status", "code", "detail")) {
            assertThat(wrong.get(field)).as(field).isEqualTo(unknown.get(field));
        }
    }

    @Test
    void an_admin_with_the_right_password_gets_the_same_401_and_no_session() throws Exception {
        Account admin = account("ADMIN", "ACTIVE");
        login(admin.email(), PASSWORD, randomIp())
                .andExpect(status().isUnauthorized())
                .andExpect(Contract.matches())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"))
                .andExpect(jsonPath("$.title").value("Email hoặc mật khẩu không đúng."));
        assertThat(sessionCount(admin.id())).isZero();
    }

    @Test
    void unverified_and_locked_accounts_are_told_only_after_a_correct_password() throws Exception {
        Account unverified = account("PARENT", "UNVERIFIED");
        login(unverified.email(), PASSWORD, randomIp())
                .andExpect(status().isForbidden()).andExpect(Contract.matches())
                .andExpect(jsonPath("$.code").value("ACCOUNT_UNVERIFIED"));
        login(unverified.email(), "sai-mat-khau-hoan-toan", randomIp())
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"));

        Account locked = account("TUTOR", "LOCKED");
        login(locked.email(), PASSWORD, randomIp())
                .andExpect(status().isForbidden()).andExpect(Contract.matches())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
        assertThat(sessionCount(unverified.id()) + sessionCount(locked.id())).isZero();
    }

    @Test
    void five_failures_delay_the_sixth_try_even_with_the_right_password() throws Exception {
        Account account = account("PARENT", "ACTIVE");
        for (int i = 0; i < 5; i++) {
            login(account.email(), "sai-" + i + "-mat-khau-dai", randomIp()).andExpect(status().isUnauthorized());
        }
        login(account.email(), PASSWORD, randomIp())
                .andExpect(status().isTooManyRequests())
                .andExpect(Contract.matches())
                .andExpect(jsonPath("$.code").value("LOGIN_DELAYED"))
                .andExpect(header().string("Retry-After", org.hamcrest.Matchers.matchesPattern("(8[5-9]\\d|900)")));
        assertThat(sessionCount(account.id())).isZero();
    }

    @Test
    void wrong_passwords_sent_at_the_same_time_check_only_five_and_delay_the_rest() throws Exception {
        // Issue #141: the count, the slow password check and the record were not atomic, so all of
        // these used to be checked (20 x 401). Each try uses its own address so only the per-email
        // limit applies.
        Account account = account("PARENT", "ACTIVE");
        List<Integer> statuses = Concurrently.atOnce(20,
                i -> login(account.email(), "sai-" + i + "-mat-khau-dai", randomIp()).andReturn().getResponse().getStatus());

        assertThat(statuses).filteredOn(status -> status == 401).hasSize(5);
        assertThat(statuses).filteredOn(status -> status == 429).hasSize(15);
        assertThat(failures("ACCOUNT", secrets.emailDigest(account.email()))).isEqualTo(5);
    }

    @Test
    void a_correct_password_leaves_no_failure_behind() throws Exception {
        Account account = account("PARENT", "ACTIVE");
        String ip = randomIp();

        loginOk(account.email(), ip);

        assertThat(failures("ACCOUNT", secrets.emailDigest(account.email()))).isZero();
        assertThat(failures("SOURCE", secrets.sourceDigest(ip))).isZero();
    }

    @Test
    void the_window_slides_so_failures_on_both_sides_of_a_quarter_hour_still_count() throws Exception {
        Account account = account("PARENT", "ACTIVE");
        String digest = secrets.emailDigest(account.email());
        for (int i = 0; i < 4; i++) {
            jdbc.sql("insert into auth_login_failures (scope, subject_digest, failed_at) values ('ACCOUNT', ?, now() - interval '14 minutes')")
                    .param(digest).update();
        }
        login(account.email(), "sai-mat-khau-lan-nam", randomIp()).andExpect(status().isUnauthorized());

        login(account.email(), PASSWORD, randomIp())
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", org.hamcrest.Matchers.matchesPattern("[1-6]\\d")));

        jdbc.sql("update auth_login_failures set failed_at = failed_at - interval '2 minutes' where subject_digest = ? and failed_at < now() - interval '10 minutes'")
                .param(digest).update();
        loginOk(account.email(), randomIp());
    }

    @Test
    void failures_from_one_source_delay_that_source_for_every_email() throws Exception {
        String ip = randomIp();
        for (int i = 0; i < 5; i++) {
            login("khong-co-" + UUID.randomUUID() + "@example.test", PASSWORD, ip).andExpect(status().isUnauthorized());
        }
        Account account = account("PARENT", "ACTIVE");
        login(account.email(), PASSWORD, ip).andExpect(status().isTooManyRequests());
        loginOk(account.email(), randomIp());
    }

    @Test
    void a_successful_login_clears_the_failures_of_that_email() throws Exception {
        Account account = account("PARENT", "ACTIVE");
        for (int i = 0; i < 4; i++) {
            login(account.email(), "sai-" + i + "-mat-khau-dai", randomIp()).andExpect(status().isUnauthorized());
        }
        loginOk(account.email(), randomIp());
        login(account.email(), "sai-lan-nua-mat-khau", randomIp()).andExpect(status().isUnauthorized());
        loginOk(account.email(), randomIp());
    }

    @Test
    void a_hash_under_an_older_policy_is_replaced_at_login() throws Exception {
        Account account = account("PARENT", "ACTIVE");
        jdbc.sql("update account_credentials set hash_policy = 'argon2id-v0' where account_id = ?").param(account.id()).update();
        loginOk(account.email(), randomIp());
        assertThat(jdbc.sql("select hash_policy from account_credentials where account_id = ?")
                .param(account.id()).query(String.class).single()).isEqualTo(PasswordHasher.POLICY);
    }

    // ---------------------------------------------------------------- AC-UC01-03

    @Test
    void AC_UC01_03_after_logout_the_old_session_is_refused_and_logout_again_is_harmless() throws Exception {
        String token = tokenFor(account("PARENT", "ACTIVE"));
        authed(post("/api/v1/auth/logout"), token).andExpect(status().isNoContent()).andExpect(Contract.matches());

        authed(get("/api/v1/me/sessions"), token).andExpect(status().isUnauthorized()).andExpect(Contract.matches());
        authed(post("/api/v1/auth/logout"), token).andExpect(status().isUnauthorized());
    }

    @Test
    void AC_UC01_03_a_session_revoked_from_another_device_is_refused_on_its_next_request() throws Exception {
        Account account = account("TUTOR", "ACTIVE");
        JsonNode phone = loginOk(account.email(), randomIp());
        String tablet = tokenFor(account);

        authed(delete("/api/v1/me/sessions/" + phone.get("sessionId").asString()), tablet)
                .andExpect(status().isNoContent()).andExpect(Contract.matches());

        authed(get("/api/v1/me/sessions"), phone.get("accessToken").asString()).andExpect(status().isUnauthorized());
        authed(get("/api/v1/me/sessions"), tablet)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.id=='%s')].revoked".formatted(phone.get("sessionId").asString()))
                        .value(true));
    }

    @Test
    void AC_UC01_03_locking_the_account_or_raising_its_epoch_refuses_every_session() throws Exception {
        Account locked = account("PARENT", "ACTIVE");
        String lockedToken = tokenFor(locked);
        jdbc.sql("update accounts set status = 'LOCKED' where id = ?").param(locked.id()).update();
        authed(get("/api/v1/me/sessions"), lockedToken).andExpect(status().isUnauthorized());

        Account bumped = account("PARENT", "ACTIVE");
        String bumpedToken = tokenFor(bumped);
        jdbc.sql("update accounts set auth_epoch = auth_epoch + 1 where id = ?").param(bumped.id()).update();
        authed(get("/api/v1/me/sessions"), bumpedToken).andExpect(status().isUnauthorized());
    }

    @Test
    void AC_UC01_03_sessions_end_after_24_hours_idle_or_7_days_in_all() throws Exception {
        Account account = account("PARENT", "ACTIVE");
        String idle = tokenFor(account);
        jdbc.sql("update auth_sessions set last_seen_at = now() - interval '25 hours', created_at = now() - interval '25 hours' where token_digest = ?")
                .param(AccountSecrets.linkTokenDigest(idle)).update();
        authed(get("/api/v1/me/sessions"), idle).andExpect(status().isUnauthorized());

        String old = tokenFor(account);
        jdbc.sql("""
                update auth_sessions set created_at = now() - interval '8 days', expires_at = now() - interval '1 day'
                where token_digest = ?
                """).param(AccountSecrets.linkTokenDigest(old)).update();
        authed(get("/api/v1/me/sessions"), old).andExpect(status().isUnauthorized());
    }

    @Test
    void a_request_moves_last_seen_forward_but_at_most_every_five_minutes() throws Exception {
        String token = tokenFor(account("PARENT", "ACTIVE"));
        String digest = AccountSecrets.linkTokenDigest(token);
        jdbc.sql("update auth_sessions set last_seen_at = now() - interval '6 minutes', created_at = now() - interval '6 minutes' where token_digest = ?")
                .param(digest).update();
        authed(get("/api/v1/me/sessions"), token).andExpect(status().isOk());
        assertThat(jdbc.sql("select last_seen_at > now() - interval '1 minute' from auth_sessions where token_digest = ?")
                .param(digest).query(Boolean.class).single()).isTrue();

        jdbc.sql("update auth_sessions set last_seen_at = now() - interval '3 minutes' where token_digest = ?").param(digest).update();
        authed(get("/api/v1/me/sessions"), token).andExpect(status().isOk());
        assertThat(jdbc.sql("select last_seen_at < now() - interval '2 minutes' from auth_sessions where token_digest = ?")
                .param(digest).query(Boolean.class).single()).isTrue();
    }

    // ---------------------------------------------------------------- listOwnSessions, revokeOwnSession

    @Test
    void the_list_shows_only_own_sessions_page_by_page_and_never_a_token_digest() throws Exception {
        Account account = account("PARENT", "ACTIVE");
        String token = tokenFor(account);
        tokenFor(account);
        tokenFor(account);
        tokenFor(account("PARENT", "ACTIVE"));

        MvcResult first = authed(get("/api/v1/me/sessions").param("limit", "2"), token)
                .andExpect(status().isOk()).andExpect(Contract.matches())
                .andExpect(jsonPath("$.items.length()").value(2)).andReturn();
        String body = first.getResponse().getContentAsString();
        assertThat(body).doesNotContain("token").doesNotContain(token);
        String cursor = JSON.readTree(body).get("nextCursor").asString();

        authed(get("/api/v1/me/sessions").param("limit", "2").param("cursor", cursor), token)
                .andExpect(status().isOk()).andExpect(Contract.matches())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.nextCursor").isEmpty());
    }

    @Test
    void another_accounts_session_and_an_unknown_id_get_the_same_404_and_nothing_changes() throws Exception {
        String mine = tokenFor(account("PARENT", "ACTIVE"));
        JsonNode theirs = loginOk(account("TUTOR", "ACTIVE").email(), randomIp());

        authed(delete("/api/v1/me/sessions/" + theirs.get("sessionId").asString()), mine)
                .andExpect(status().isNotFound()).andExpect(Contract.matches())
                .andExpect(jsonPath("$.code").value("SESSION_NOT_FOUND"));
        authed(delete("/api/v1/me/sessions/" + UUID.randomUUID()), mine)
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("SESSION_NOT_FOUND"));

        authed(get("/api/v1/me/sessions"), theirs.get("accessToken").asString()).andExpect(status().isOk());
    }

    @Test
    void session_endpoints_need_a_session() throws Exception {
        mvc.perform(get("/api/v1/me/sessions")).andExpect(status().isUnauthorized()).andExpect(Contract.matches());
        mvc.perform(post("/api/v1/auth/logout")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/v1/me/sessions/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
        authed(get("/api/v1/me/sessions"), "khong-phai-phien-nao-ca-" + UUID.randomUUID())
                .andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------- AC-UC01-04

    @Test
    void AC_UC01_04_passwords_and_session_tokens_do_not_reach_the_log(CapturedOutput output) throws Exception {
        Account account = account("PARENT", "ACTIVE");
        login(account.email(), "sai-mat-khau-de-ghi-log", randomIp()).andExpect(status().isUnauthorized());
        String token = tokenFor(account);
        authed(post("/api/v1/auth/logout"), token).andExpect(status().isNoContent());

        assertThat(output.getAll()).doesNotContain(PASSWORD).doesNotContain("sai-mat-khau-de-ghi-log")
                .doesNotContain(token).doesNotContain(account.email());
    }

    // ---------------------------------------------------------------- helpers

    private record Account(UUID id, String email) {}

    private Account account(String role, String status) {
        UUID id = UUID.randomUUID();
        String email = "phien-" + id + "@example.test";
        jdbc.sql("insert into accounts (id, role, status, email_digest, email_ciphertext, display_name) values (?, ?, ?, ?, ?, 'Lan')")
                .params(id, role, status, secrets.emailDigest(email), secrets.encryptEmail(id, email)).update();
        jdbc.sql("insert into account_credentials (account_id, password_hash, hash_policy) values (?, ?, ?)")
                .params(id, hasher.hash(PASSWORD), PasswordHasher.POLICY).update();
        return new Account(id, email);
    }

    private int failures(String scope, String digest) {
        return jdbc.sql("select count(*) from auth_login_failures where scope = ? and subject_digest = ?")
                .params(scope, digest).query(Integer.class).single();
    }

    private ResultActions login(String email, String password, String ip) throws Exception {
        return mvc.perform(post("/api/v1/auth/login")
                .with(request -> {
                    request.setRemoteAddr(ip);
                    return request;
                })
                .header("User-Agent", ANDROID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(Map.of("email", email, "password", password))));
    }

    private JsonNode loginOk(String email, String ip) throws Exception {
        MvcResult result = login(email, PASSWORD, ip)
                .andExpect(status().isOk())
                .andExpect(Contract.matches())
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andReturn();
        return JSON.readTree(result.getResponse().getContentAsString());
    }

    private String tokenFor(Account account) throws Exception {
        return loginOk(account.email(), randomIp()).get("accessToken").asString();
    }

    private ResultActions authed(MockHttpServletRequestBuilder request, String token) throws Exception {
        return mvc.perform(request.header("Authorization", "Bearer " + token));
    }

    private int sessionCount(UUID accountId) {
        return jdbc.sql("select count(*) from auth_sessions where account_id = ?").param(accountId)
                .query(Integer.class).single();
    }

    private static JsonNode problem(MvcResult result) throws Exception {
        return JSON.readTree(result.getResponse().getContentAsString());
    }

    private static String randomIp() {
        UUID u = UUID.randomUUID();
        return "10." + (u.getMostSignificantBits() & 0xff) + "." + ((u.getMostSignificantBits() >> 8) & 0xff) + "."
                + ((u.getLeastSignificantBits() & 0xfe) + 1);
    }
}
