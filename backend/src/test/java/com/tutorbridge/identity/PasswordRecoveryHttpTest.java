package com.tutorbridge.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tutorbridge.Concurrently;
import com.tutorbridge.Contract;
import com.tutorbridge.IntegrationTest;
import com.tutorbridge.identity.internal.AccountSecrets;
import com.tutorbridge.identity.internal.PasswordHasher;
import com.tutorbridge.identity.internal.PasswordRecovery;
import com.tutorbridge.platform.api.ApiException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;
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
import tools.jackson.databind.json.JsonMapper;

/** UC01 step 4 by email: requestRecovery and resetPassword with real sessions (TB-9 PR 1). */
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class PasswordRecoveryHttpTest extends IntegrationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String OLD = "mat-khau-cu-du-dai-15";
    private static final String NEW = "mat-khau-moi-du-dai-15";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private AccountSecrets secrets;

    @Autowired
    private PasswordHasher hasher;

    @Autowired
    private PasswordRecovery recovery;

    // ---------------------------------------------------------------- requestRecovery

    @Test
    void every_email_gets_the_same_202_and_only_active_or_unverified_accounts_get_a_reset_link() throws Exception {
        Account active = account("ACTIVE");
        Account unverified = account("UNVERIFIED");
        Account locked = account("LOCKED");
        Account deleted = account("DELETED");

        String unknownReply = message(recover("khong-co-" + UUID.randomUUID() + "@example.test")
                .andExpect(status().isAccepted()).andExpect(Contract.matches()).andReturn());
        for (Account account : List.of(active, unverified, locked, deleted)) {
            assertThat(message(recover(account.email().toUpperCase(java.util.Locale.ROOT))
                    .andExpect(status().isAccepted()).andExpect(Contract.matches()).andReturn()))
                    .isEqualTo(unknownReply);
        }

        assertThat(resetEvents(active.id())).hasSize(1);
        assertThat(resetEvents(unverified.id())).hasSize(1);
        assertThat(resetEvents(locked.id())).isEmpty();
        assertThat(resetEvents(deleted.id())).isEmpty();
        Map<String, Object> challenge = jdbc.sql("""
                select purpose, expires_at - created_at = interval '15 minutes' as fifteen
                from auth_challenges where account_id = ?
                """).param(active.id()).query().singleRow();
        assertThat(challenge).containsEntry("purpose", "RESET_PASSWORD").containsEntry("fifteen", true);
        String payload = (String) resetEvents(active.id()).getFirst().get("payload");
        assertThat(payload).doesNotContain(active.email()).doesNotContain(resetToken(active.id()));
    }

    @Test
    void recovery_is_limited_per_email_across_sources_and_says_when_to_retry() throws Exception {
        String email = account("ACTIVE").email();
        for (int i = 0; i < 3; i++) {
            recover(email).andExpect(status().isAccepted());
        }
        recover(email)
                .andExpect(status().isTooManyRequests())
                .andExpect(Contract.matches())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andExpect(header().string("Retry-After", org.hamcrest.Matchers.matchesPattern("[1-9][0-9]*")));
    }

    @Test
    void recovery_is_limited_per_source_across_emails() throws Exception {
        String ip = randomIp();
        for (int i = 0; i < 10; i++) {
            recover("khong-co-" + UUID.randomUUID() + "@example.test", ip).andExpect(status().isAccepted());
        }
        recover("khong-co-" + UUID.randomUUID() + "@example.test", ip).andExpect(status().isTooManyRequests());
    }

    // ---------------------------------------------------------------- resetPassword

    @Test
    void AC_UC01_03_a_reset_changes_the_password_and_ends_every_old_session() throws Exception {
        Account account = account("ACTIVE");
        String phone = login(account.email(), OLD);
        String tablet = login(account.email(), OLD);
        long epoch = epoch(account.id());
        recover(account.email()).andExpect(status().isAccepted());

        reset(resetToken(account.id()), NEW).andExpect(status().isNoContent()).andExpect(Contract.matches());

        for (String token : List.of(phone, tablet)) {
            mvc.perform(get("/api/v1/me/sessions").header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized());
        }
        assertThat(epoch(account.id())).isEqualTo(epoch + 1);
        assertThat(jdbc.sql("select count(*) from auth_sessions where account_id = ? and revoked_at is null")
                .param(account.id()).query(Integer.class).single()).isZero();
        loginAttempt(account.email(), OLD).andExpect(status().isUnauthorized());
        login(account.email(), NEW);
        assertThat(jdbc.sql("select status from accounts where id = ?").param(account.id())
                .query(String.class).single()).isEqualTo("ACTIVE");
    }

    @Test
    void AC_UC01_02_a_reset_link_works_once_and_an_older_link_dies_with_it() throws Exception {
        Account account = account("ACTIVE");
        recover(account.email()).andExpect(status().isAccepted());
        recover(account.email()).andExpect(status().isAccepted());
        List<Map<String, Object>> events = resetEvents(account.id());
        String older = tokenFrom(events.get(0));
        String newer = tokenFrom(events.get(1));

        reset(newer, NEW).andExpect(status().isNoContent());

        reset(newer, NEW + "-2").andExpect(status().isConflict()).andExpect(Contract.matches())
                .andExpect(jsonPath("$.code").value("LINK_USED"));
        reset(older, NEW + "-3").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LINK_USED"));
        login(account.email(), NEW);
    }

    @Test
    void AC_UC01_02_an_expired_reset_link_is_refused_and_the_password_stays() throws Exception {
        Account account = account("ACTIVE");
        recover(account.email()).andExpect(status().isAccepted());
        String token = resetToken(account.id());
        jdbc.sql("""
                update auth_challenges set created_at = created_at - interval '16 minutes',
                    expires_at = expires_at - interval '16 minutes' where account_id = ?
                """).param(account.id()).update();

        reset(token, NEW).andExpect(status().isBadRequest()).andExpect(Contract.matches())
                .andExpect(jsonPath("$.code").value("LINK_EXPIRED"));
        login(account.email(), OLD);
    }

    @Test
    void AC_UC01_02_a_verification_code_cannot_reset_a_password() throws Exception {
        Account account = account("UNVERIFIED");
        String token = secrets.newLinkToken();
        jdbc.sql("""
                insert into auth_challenges (id, account_id, purpose, token_digest, expires_at)
                values (?, ?, 'VERIFY_EMAIL', ?, now() + interval '30 minutes')
                """).params(UUID.randomUUID(), account.id(), AccountSecrets.linkTokenDigest(token)).update();

        reset(token, NEW).andExpect(status().isBadRequest()).andExpect(Contract.matches())
                .andExpect(jsonPath("$.code").value("LINK_INVALID"));
        assertThat(jdbc.sql("select consumed_at is null from auth_challenges where account_id = ?")
                .param(account.id()).query(Boolean.class).single()).isTrue();
    }

    @Test
    void AC_UC01_02_two_concurrent_resets_with_one_link_succeed_exactly_once() throws Exception {
        Account account = account("ACTIVE");
        recover(account.email()).andExpect(status().isAccepted());
        String token = resetToken(account.id());

        List<CompletableFuture<String>> attempts = IntStream.range(0, 2)
                .mapToObj(i -> Concurrently.supply(() -> {
                    try {
                        recovery.resetPassword(token, NEW + "-" + i, randomIp());
                        return "OK";
                    } catch (ApiException e) {
                        return e.code();
                    }
                })).toList();

        assertThat(attempts.stream().map(CompletableFuture::join).toList())
                .containsExactlyInAnyOrder("OK", "LINK_USED");
        assertThat(epoch(account.id())).isEqualTo(2);
    }

    @Test
    void an_unverified_account_can_reset_and_stays_unverified() throws Exception {
        Account account = account("UNVERIFIED");
        recover(account.email()).andExpect(status().isAccepted());
        reset(resetToken(account.id()), NEW).andExpect(status().isNoContent());

        assertThat(jdbc.sql("select status from accounts where id = ?").param(account.id())
                .query(String.class).single()).isEqualTo("UNVERIFIED");
        loginAttempt(account.email(), NEW).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_UNVERIFIED"));
    }

    @Test
    void an_account_locked_after_the_mail_cannot_use_its_link() throws Exception {
        Account account = account("ACTIVE");
        recover(account.email()).andExpect(status().isAccepted());
        String token = resetToken(account.id());
        jdbc.sql("update accounts set status = 'LOCKED' where id = ?").param(account.id()).update();

        reset(token, NEW).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("LINK_INVALID"));
        assertThat(jdbc.sql("select password_hash from account_credentials where account_id = ?")
                .param(account.id()).query(String.class).single()).satisfies(hash -> assertThat(hasher.matches(OLD, hash)).isTrue());
    }

    @Test
    void the_new_password_follows_the_15_to_128_code_point_rule() throws Exception {
        Account account = account("ACTIVE");
        recover(account.email()).andExpect(status().isAccepted());
        String token = resetToken(account.id());

        reset(token, "😀".repeat(14)).andExpect(status().isUnprocessableContent()).andExpect(Contract.matches())
                .andExpect(jsonPath("$.fields[0].path").value("newPassword"));
        reset(token, "😀".repeat(15)).andExpect(status().isNoContent());
    }

    @Test
    void a_reset_clears_the_login_failures_of_that_email() throws Exception {
        Account account = account("ACTIVE");
        for (int i = 0; i < 5; i++) {
            loginAttempt(account.email(), "sai-" + i + "-mat-khau-dai").andExpect(status().isUnauthorized());
        }
        loginAttempt(account.email(), OLD).andExpect(status().isTooManyRequests());
        recover(account.email()).andExpect(status().isAccepted());

        reset(resetToken(account.id()), NEW).andExpect(status().isNoContent());
        login(account.email(), NEW);
    }

    @Test
    void AC_UC01_04_reset_codes_passwords_and_emails_do_not_reach_the_log(CapturedOutput output) throws Exception {
        Account account = account("ACTIVE");
        recover(account.email()).andExpect(status().isAccepted());
        String token = resetToken(account.id());
        reset(token, NEW).andExpect(status().isNoContent());
        reset(token, NEW).andExpect(status().isConflict());

        assertThat(output.getAll()).doesNotContain(token).doesNotContain(NEW).doesNotContain(account.email());
    }

    // ---------------------------------------------------------------- helpers

    private record Account(UUID id, String email) {}

    private Account account(String status) {
        UUID id = UUID.randomUUID();
        String email = "khoi-phuc-" + id + "@example.test";
        jdbc.sql("insert into accounts (id, role, status, email_digest, email_ciphertext, display_name) values (?, 'PARENT', ?, ?, ?, 'Lan')")
                .params(id, status, secrets.emailDigest(email), secrets.encryptEmail(id, email)).update();
        jdbc.sql("insert into account_credentials (account_id, password_hash, hash_policy) values (?, ?, ?)")
                .params(id, hasher.hash(OLD), PasswordHasher.POLICY).update();
        return new Account(id, email);
    }

    private ResultActions recover(String email) throws Exception {
        return recover(email, randomIp());
    }

    private ResultActions recover(String email, String ip) throws Exception {
        return post("/api/v1/auth/recovery", Map.of("email", email), ip);
    }

    private ResultActions reset(String token, String newPassword) throws Exception {
        return post("/api/v1/auth/reset", Map.of("token", token, "newPassword", newPassword), randomIp());
    }

    private ResultActions loginAttempt(String email, String password) throws Exception {
        return post("/api/v1/auth/login", Map.of("email", email, "password", password), randomIp());
    }

    private String login(String email, String password) throws Exception {
        MvcResult result = loginAttempt(email, password).andExpect(status().isOk()).andReturn();
        return JSON.readTree(result.getResponse().getContentAsString()).get("accessToken").asString();
    }

    private ResultActions post(String path, Map<String, String> body, String ip) throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path)
                .with(request -> {
                    request.setRemoteAddr(ip);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(body)));
    }

    private List<Map<String, Object>> resetEvents(UUID accountId) {
        return jdbc.sql("""
                select payload::text as payload, payload->>'challengeId' as challenge,
                       payload->>'tokenCiphertext' as ciphertext
                from outbox_events where aggregate_id = ? and event_type = ? order by created_at, id
                """).params(accountId, PasswordRecovery.RESET_REQUESTED).query().listOfRows();
    }

    private String resetToken(UUID accountId) {
        return tokenFrom(resetEvents(accountId).getLast());
    }

    private String tokenFrom(Map<String, Object> event) {
        return secrets.decryptLinkToken(UUID.fromString((String) event.get("challenge")), (String) event.get("ciphertext"));
    }

    private long epoch(UUID accountId) {
        return jdbc.sql("select auth_epoch from accounts where id = ?").param(accountId).query(Long.class).single();
    }

    private static String message(MvcResult result) throws Exception {
        return JSON.readTree(result.getResponse().getContentAsString()).get("message").asString();
    }

    private static String randomIp() {
        UUID u = UUID.randomUUID();
        return "10." + (u.getMostSignificantBits() & 0xff) + "." + ((u.getMostSignificantBits() >> 8) & 0xff) + "."
                + ((u.getLeastSignificantBits() & 0xfe) + 1);
    }
}
