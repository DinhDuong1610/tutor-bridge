package com.tutorbridge.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tutorbridge.Contract;
import com.tutorbridge.HttpTest;
import com.tutorbridge.WebDebugLogging;
import com.tutorbridge.identity.internal.AccountRegistration;
import com.tutorbridge.identity.internal.AccountSecrets;
import com.tutorbridge.identity.internal.PasswordHasher;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.json.JsonMapper;

/** UC01 steps 1–2 over HTTP: registerAccount, verifyEmail, resendVerification (TB-8 PR 2). */
@ExtendWith(OutputCaptureExtension.class)
class AccountRegistrationHttpTest extends HttpTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PASSWORD = "mot-mat-khau-du-dai-15";

    @Autowired
    private AccountSecrets secrets;

    @Autowired
    private AccountRegistration registration;

    // ---------------------------------------------------------------- registerAccount

    @Test
    void register_creates_an_unverified_account_with_protected_email_hashed_password_and_terms() throws Exception {
        String email = uniqueEmail();
        register(email, "PARENT", source())
                .andExpect(status().isAccepted())
                .andExpect(Contract.matches())
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());

        UUID id = accountId(email);
        Map<String, Object> row = jdbc.sql("""
                select a.role, a.status, a.display_name, a.email_ciphertext, c.password_hash, c.hash_policy,
                       t.terms_version
                from accounts a join account_credentials c on c.account_id = a.id
                join terms_acceptances t on t.account_id = a.id where a.id = ?
                """).param(id).query().singleRow();
        assertThat(row).containsEntry("role", "PARENT").containsEntry("status", "UNVERIFIED")
                .containsEntry("display_name", "Lan").containsEntry("hash_policy", PasswordHasher.POLICY)
                .containsEntry("terms_version", TERMS_VERSION);
        assertThat((String) row.get("password_hash")).startsWith("$argon2id$").doesNotContain(PASSWORD);
        byte[] ciphertext = (byte[]) row.get("email_ciphertext");
        assertThat(new String(ciphertext, StandardCharsets.ISO_8859_1)).doesNotContain(email);
        assertThat(secrets.decryptEmail(id, ciphertext)).isEqualTo(email);
    }

    @Test
    void register_queues_one_verification_event_that_holds_neither_the_code_nor_the_email() throws Exception {
        String email = uniqueEmail();
        register(email, "TUTOR", source()).andExpect(status().isAccepted());
        UUID id = accountId(email);

        List<Map<String, Object>> events = verificationEvents(id);
        assertThat(events).hasSize(1);
        String payload = (String) events.getFirst().get("payload");
        String token = tokenFrom(events.getFirst());
        assertThat(payload).doesNotContain(token).doesNotContain(email);
        Map<String, Object> challenge = jdbc.sql("""
                select purpose, token_digest, expires_at - created_at = interval '30 minutes' as thirty
                from auth_challenges where account_id = ?
                """).param(id).query().singleRow();
        assertThat(challenge).containsEntry("purpose", "VERIFY_EMAIL").containsEntry("thirty", true)
                .containsEntry("token_digest", AccountSecrets.linkTokenDigest(token));
    }

    @Test
    void AC_UC01_01_a_taken_email_in_another_case_creates_no_second_account_and_gets_the_same_reply()
            throws Exception {
        String email = uniqueEmail();
        MvcResult first = register(email, "PARENT", source()).andExpect(status().isAccepted()).andReturn();
        MvcResult again = register(email.toUpperCase(java.util.Locale.ROOT), "TUTOR", source())
                .andExpect(status().isAccepted())
                .andExpect(Contract.matches())
                .andReturn();

        assertThat(message(again)).isEqualTo(message(first));
        assertThat(jdbc.sql("select count(*) from accounts where email_digest = ?")
                .param(secrets.emailDigest(email)).query(Integer.class).single()).isEqualTo(1);
        UUID id = accountId(email);
        assertThat(jdbc.sql("select role from accounts where id = ?").param(id).query(String.class).single())
                .isEqualTo("PARENT");
        assertThat(verificationEvents(id)).hasSize(1);
    }

    @Test
    void AC_UC01_01_admin_or_student_in_the_payload_is_rejected_and_creates_nothing() throws Exception {
        for (String role : List.of("ADMIN", "STUDENT", "parent")) {
            String email = uniqueEmail();
            register(email, role, source())
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(Contract.matches())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.fields[0].path").value("role"));
            assertThat(countAccounts(email)).isZero();
        }
    }

    @Test
    void register_counts_password_length_in_code_points_like_the_contract() throws Exception {
        String fourteen = "😀".repeat(14); // 28 UTF-16 units, 14 code points
        postJson("/api/v1/auth/register", registration(uniqueEmail(), fourteen, "PARENT", TERMS_VERSION), source())
                .andExpect(status().isUnprocessableContent())
                .andExpect(Contract.matches())
                .andExpect(jsonPath("$.fields[0].path").value("password"));

        String email = uniqueEmail();
        postJson("/api/v1/auth/register", registration(email, "😀".repeat(15), "PARENT", TERMS_VERSION), source())
                .andExpect(status().isAccepted());
        assertThat(countAccounts(email)).isEqualTo(1);
    }

    @Test
    void register_rejects_a_terms_version_the_server_does_not_list() throws Exception {
        String email = uniqueEmail();
        postJson("/api/v1/auth/register", registration(email, PASSWORD, "PARENT", "old-terms"), source())
                .andExpect(status().isUnprocessableContent())
                .andExpect(Contract.matches())
                .andExpect(jsonPath("$.fields[0].path").value("termsVersion"));
        assertThat(countAccounts(email)).isZero();
    }

    @Test
    void register_from_one_source_is_limited_and_says_when_to_retry() throws Exception {
        RequestPostProcessor from = source();
        for (int i = 0; i < 5; i++) {
            register(uniqueEmail(), "PARENT", from).andExpect(status().isAccepted());
        }
        String blocked = uniqueEmail();
        register(blocked, "PARENT", from)
                .andExpect(status().isTooManyRequests())
                .andExpect(Contract.matches())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andExpect(header().string("Retry-After", org.hamcrest.Matchers.matchesPattern("[1-9][0-9]*")));
        assertThat(countAccounts(blocked)).isZero();

        register(uniqueEmail(), "PARENT", source()).andExpect(status().isAccepted());
    }

    @Test
    void register_is_allowed_again_once_the_window_has_passed() throws Exception {
        String ip = randomIp();
        for (int i = 0; i < 6; i++) {
            register(uniqueEmail(), "PARENT", source(ip));
        }
        jdbc.sql("""
                update auth_rate_counters set window_started_at = window_started_at - interval '61 minutes'
                where action = 'REGISTER' and subject_digest = ?
                """).param(secrets.sourceDigest(ip)).update();
        register(uniqueEmail(), "PARENT", source(ip)).andExpect(status().isAccepted());
    }

    // ---------------------------------------------------------------- verifyEmail

    @Test
    void AC_UC01_02_a_verification_link_activates_the_account_once_and_is_then_used() throws Exception {
        String email = uniqueEmail();
        register(email, "PARENT", source()).andExpect(status().isAccepted());
        UUID id = accountId(email);
        String token = tokenFrom(verificationEvents(id).getFirst());

        verify(token).andExpect(status().isNoContent()).andExpect(Contract.matches());
        Map<String, Object> account = jdbc.sql("select status, email_verified_at from accounts where id = ?")
                .param(id).query().singleRow();
        assertThat(account.get("status")).isEqualTo("ACTIVE");
        assertThat(account.get("email_verified_at")).isNotNull();

        verify(token)
                .andExpect(status().isConflict())
                .andExpect(Contract.matches())
                .andExpect(jsonPath("$.code").value("LINK_USED"));
    }

    @Test
    void AC_UC01_02_two_concurrent_uses_of_one_link_succeed_exactly_once() throws Exception {
        String email = uniqueEmail();
        register(email, "PARENT", source()).andExpect(status().isAccepted());
        String token = tokenFrom(verificationEvents(accountId(email)).getFirst());

        var attempts = java.util.stream.IntStream.range(0, 2)
                .mapToObj(i -> com.tutorbridge.Concurrently.supply(() -> {
                    try {
                        registration.verifyEmail(token);
                        return "OK";
                    } catch (com.tutorbridge.platform.api.ApiException e) {
                        return e.code();
                    }
                })).toList();
        List<String> outcomes = attempts.stream().map(java.util.concurrent.CompletableFuture::join).toList();

        assertThat(outcomes).containsExactlyInAnyOrder("OK", "LINK_USED");
        assertThat(accountStatus(accountId(email))).isEqualTo("ACTIVE");
    }

    @Test
    void AC_UC01_02_an_expired_link_is_rejected_and_leaves_the_account_unverified() throws Exception {
        String email = uniqueEmail();
        register(email, "PARENT", source()).andExpect(status().isAccepted());
        UUID id = accountId(email);
        String token = tokenFrom(verificationEvents(id).getFirst());
        jdbc.sql("""
                update auth_challenges set created_at = created_at - interval '31 minutes',
                    expires_at = expires_at - interval '31 minutes' where account_id = ?
                """).param(id).update();

        verify(token)
                .andExpect(status().isBadRequest())
                .andExpect(Contract.matches())
                .andExpect(jsonPath("$.code").value("LINK_EXPIRED"));
        assertThat(accountStatus(id)).isEqualTo("UNVERIFIED");
    }

    @Test
    void AC_UC01_02_a_code_made_for_another_purpose_is_invalid_and_stays_unconsumed() throws Exception {
        UUID id = newAccount();
        String token = secrets.newLinkToken();
        jdbc.sql("""
                insert into auth_challenges (id, account_id, purpose, token_digest, expires_at)
                values (?, ?, 'RESET_PASSWORD', ?, now() + interval '15 minutes')
                """).params(UUID.randomUUID(), id, AccountSecrets.linkTokenDigest(token)).update();

        verify(token)
                .andExpect(status().isBadRequest())
                .andExpect(Contract.matches())
                .andExpect(jsonPath("$.code").value("LINK_INVALID"));
        assertThat(jdbc.sql("select consumed_at is null from auth_challenges where account_id = ?")
                .param(id).query(Boolean.class).single()).isTrue();
    }

    @Test
    void an_unknown_code_is_invalid_and_a_malformed_one_never_reaches_the_service() throws Exception {
        verify(secrets.newLinkToken())
                .andExpect(status().isBadRequest())
                .andExpect(Contract.matches())
                .andExpect(jsonPath("$.code").value("LINK_INVALID"));
        verify("too-short")
                .andExpect(status().isUnprocessableContent())
                .andExpect(Contract.matches());
    }

    @Test
    void a_locked_account_is_not_reactivated_by_its_verification_link() throws Exception {
        String email = uniqueEmail();
        register(email, "PARENT", source()).andExpect(status().isAccepted());
        UUID id = accountId(email);
        String token = tokenFrom(verificationEvents(id).getFirst());
        jdbc.sql("update accounts set status = 'LOCKED' where id = ?").param(id).update();

        verify(token).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("LINK_INVALID"));
        assertThat(accountStatus(id)).isEqualTo("LOCKED");
    }

    // ---------------------------------------------------------------- resendVerification

    @Test
    void resend_gives_the_same_answer_for_unknown_unverified_and_active_emails_and_only_queues_for_unverified()
            throws Exception {
        String unverified = uniqueEmail();
        register(unverified, "PARENT", source()).andExpect(status().isAccepted());
        String active = uniqueEmail();
        register(active, "PARENT", source()).andExpect(status().isAccepted());
        verify(tokenFrom(verificationEvents(accountId(active)).getFirst())).andExpect(status().isNoContent());

        String unknownReply = message(resend(uniqueEmail(), source()).andExpect(status().isAccepted())
                .andExpect(Contract.matches()).andReturn());
        String unverifiedReply = message(resend(unverified.toUpperCase(), source()).andExpect(status().isAccepted())
                .andExpect(Contract.matches()).andReturn());
        String activeReply = message(resend(active, source()).andExpect(status().isAccepted()).andReturn());

        assertThat(List.of(unverifiedReply, activeReply)).containsOnly(unknownReply);
        assertThat(verificationEvents(accountId(unverified))).hasSize(2);
        assertThat(verificationEvents(accountId(active))).hasSize(1);
    }

    @Test
    void a_resent_link_works_and_the_earlier_link_still_works_until_one_is_used() throws Exception {
        String email = uniqueEmail();
        register(email, "PARENT", source()).andExpect(status().isAccepted());
        resend(email, source()).andExpect(status().isAccepted());
        List<Map<String, Object>> events = verificationEvents(accountId(email));
        assertThat(events).hasSize(2);

        verify(tokenFrom(events.get(1))).andExpect(status().isNoContent());
        verify(tokenFrom(events.get(0))).andExpect(status().isNoContent());
    }

    @Test
    void resend_is_limited_per_email_across_sources_and_per_source_across_emails() throws Exception {
        String email = uniqueEmail();
        for (int i = 0; i < 3; i++) {
            resend(email, source()).andExpect(status().isAccepted());
        }
        resend(email, source())
                .andExpect(status().isTooManyRequests())
                .andExpect(Contract.matches())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andExpect(header().exists("Retry-After"));

        RequestPostProcessor from = source();
        for (int i = 0; i < 10; i++) {
            resend(uniqueEmail(), from).andExpect(status().isAccepted());
        }
        resend(uniqueEmail(), from).andExpect(status().isTooManyRequests());
    }

    // ---------------------------------------------------------------- AC-UC01-04

    @Test
    @ExtendWith(WebDebugLogging.class)
    void AC_UC01_04_passwords_codes_and_emails_do_not_reach_the_log(CapturedOutput output) throws Exception {
        String email = uniqueEmail();
        register(email, "PARENT", source()).andExpect(status().isAccepted());
        String token = tokenFrom(verificationEvents(accountId(email)).getFirst());
        verify(token).andExpect(status().isNoContent());
        verify(token).andExpect(status().isConflict());
        resend(email, source()).andExpect(status().isAccepted());

        assertThat(output.getAll()).doesNotContain(PASSWORD).doesNotContain(token).doesNotContain(email);
    }

    // ---------------------------------------------------------------- helpers

    private ResultActions register(String email, String role, RequestPostProcessor from) throws Exception {
        return postJson("/api/v1/auth/register", registration(email, PASSWORD, role, TERMS_VERSION), from);
    }

    private ResultActions verify(String token) throws Exception {
        return postJson("/api/v1/auth/verify", Map.of("token", token), source());
    }

    private ResultActions resend(String email, RequestPostProcessor from) throws Exception {
        return postJson("/api/v1/auth/verification-resend", Map.of("email", email), from);
    }

    private ResultActions postJson(String path, Map<String, ?> body, RequestPostProcessor from) throws Exception {
        return mvc.perform(post(path).with(from).contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(body)));
    }

    private static Map<String, String> registration(String email, String password, String role, String terms) {
        return Map.of("email", email, "password", password, "displayName", " Lan ", "role", role,
                "termsVersion", terms);
    }

    private List<Map<String, Object>> verificationEvents(UUID accountId) {
        return jdbc.sql("""
                select payload::text as payload, payload->>'challengeId' as challenge,
                       payload->>'tokenCiphertext' as ciphertext
                from outbox_events where aggregate_id = ? and event_type = ? order by created_at, id
                """).params(accountId, AccountRegistration.VERIFICATION_REQUESTED).query().listOfRows();
    }

    /** What the mail sender will do: open the code with the key bound to its challenge. */
    private String tokenFrom(Map<String, Object> event) {
        return secrets.decryptLinkToken(UUID.fromString((String) event.get("challenge")),
                (String) event.get("ciphertext"));
    }

    private UUID accountId(String email) {
        return jdbc.sql("select id from accounts where email_digest = ?")
                .param(secrets.emailDigest(AccountSecrets.normalizeEmail(email))).query(UUID.class).single();
    }

    private int countAccounts(String email) {
        return jdbc.sql("select count(*) from accounts where email_digest = ?")
                .param(secrets.emailDigest(AccountSecrets.normalizeEmail(email))).query(Integer.class).single();
    }

    private String accountStatus(UUID id) {
        return jdbc.sql("select status from accounts where id = ?").param(id).query(String.class).single();
    }

    private static String message(MvcResult result) throws Exception {
        return JSON.readTree(result.getResponse().getContentAsString()).get("message").asString();
    }

    private static String uniqueEmail() {
        return "nguoi-dung-" + UUID.randomUUID() + "@example.test";
    }

    /** Each test calls from its own address, so counters of other tests never interfere. */
    private static RequestPostProcessor source() {
        return source(randomIp());
    }

    private static RequestPostProcessor source(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    private static String randomIp() {
        UUID u = UUID.randomUUID();
        return "10." + (u.getMostSignificantBits() & 0xff) + "." + ((u.getMostSignificantBits() >> 8) & 0xff) + "."
                + ((u.getLeastSignificantBits() & 0xfe) + 1);
    }
}
