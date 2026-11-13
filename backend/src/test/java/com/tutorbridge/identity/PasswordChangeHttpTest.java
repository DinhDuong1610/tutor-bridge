package com.tutorbridge.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** changePassword and reauthenticate with real sessions (TB-9 PR 2). */
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class PasswordChangeHttpTest extends IntegrationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String OLD = "mat-khau-cu-du-dai-15";
    private static final String NEW = "mat-khau-moi-du-dai-15";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private AccountSecrets secrets;

    @Autowired
    private PasswordHasher hasher;

    // ---------------------------------------------------------------- changePassword

    @Test
    void AC_UC01_03_a_change_ends_every_session_this_one_included_and_only_the_new_password_works() throws Exception {
        Account account = account();
        String phone = login(account.email(), OLD);
        String tablet = login(account.email(), OLD);

        change(phone, OLD, NEW).andExpect(status().isNoContent()).andExpect(Contract.matches());

        for (String token : List.of(phone, tablet)) {
            mvc.perform(get("/api/v1/me/sessions").header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized());
        }
        assertThat(jdbc.sql("select auth_epoch from accounts where id = ?").param(account.id())
                .query(Long.class).single()).isEqualTo(2);
        loginAttempt(account.email(), OLD).andExpect(status().isUnauthorized());
        login(account.email(), NEW);
    }

    @Test
    void a_wrong_current_password_is_a_422_on_that_field_and_the_session_stays() throws Exception {
        Account account = account();
        String token = login(account.email(), OLD);

        change(token, "khong-phai-mat-khau-nay", NEW)
                .andExpect(status().isUnprocessableContent())
                .andExpect(Contract.matches())
                .andExpect(jsonPath("$.code").value("CURRENT_PASSWORD_INVALID"))
                .andExpect(jsonPath("$.fields[0].path").value("currentPassword"));

        mvc.perform(get("/api/v1/me/sessions").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        login(account.email(), OLD);
    }

    @Test
    void wrong_current_passwords_sent_at_the_same_time_check_only_five() throws Exception {
        // Issue #141: changePassword and reauthenticate share the login limit, so they had the same race.
        Account account = account();
        String token = login(account.email(), OLD);
        List<Integer> statuses = Concurrently.atOnce(20,
                i -> change(token, "sai-" + i + "-mat-khau-dai", NEW, randomIp()).andReturn().getResponse().getStatus());

        assertThat(statuses).filteredOn(status -> status == 422).hasSize(5);
        assertThat(statuses).filteredOn(status -> status == 429).hasSize(15);
    }

    @Test
    void wrong_current_passwords_count_toward_the_login_delay() throws Exception {
        Account account = account();
        String token = login(account.email(), OLD);
        String ip = randomIp();
        for (int i = 0; i < 5; i++) {
            change(token, "sai-" + i + "-mat-khau-dai", NEW, ip).andExpect(status().isUnprocessableContent());
        }

        change(token, OLD, NEW, ip)
                .andExpect(status().isTooManyRequests())
                .andExpect(Contract.matches())
                .andExpect(jsonPath("$.code").value("LOGIN_DELAYED"));
        loginAttempt(account.email(), OLD).andExpect(status().isTooManyRequests());
    }

    @Test
    void a_change_clears_the_login_failures_of_that_email() throws Exception {
        Account account = account();
        String token = login(account.email(), OLD);
        for (int i = 0; i < 4; i++) {
            loginAttempt(account.email(), "sai-" + i + "-mat-khau-dai").andExpect(status().isUnauthorized());
        }
        change(token, OLD, NEW).andExpect(status().isNoContent());

        loginAttempt(account.email(), "sai-lan-nua-mat-khau").andExpect(status().isUnauthorized());
        login(account.email(), NEW);
    }

    @Test
    void the_new_password_follows_the_15_to_128_code_point_rule() throws Exception {
        Account account = account();
        String token = login(account.email(), OLD);

        change(token, OLD, "😀".repeat(14))
                .andExpect(status().isUnprocessableContent())
                .andExpect(Contract.matches())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields[0].path").value("newPassword"));
        change(token, OLD, "😀".repeat(15)).andExpect(status().isNoContent());
    }

    // ---------------------------------------------------------------- reauthenticate

    @Test
    void reauthentication_gives_a_five_minute_one_time_token_stored_only_as_a_digest() throws Exception {
        Account account = account();
        String token = login(account.email(), OLD);

        MvcResult result = reauth(token, OLD, "PRIVACY")
                .andExpect(status().isOk())
                .andExpect(Contract.matches())
                .andReturn();
        JsonNode body = JSON.readTree(result.getResponse().getContentAsString());
        String reauthToken = body.get("reauthToken").asString();
        assertThat(reauthToken).matches("[A-Za-z0-9_-]{43}");

        Map<String, Object> row = jdbc.sql("""
                select purpose, token_digest, expires_at - created_at = interval '5 minutes' as five, consumed_at is null as open
                from auth_challenges where account_id = ?
                """).param(account.id()).query().singleRow();
        assertThat(row).containsEntry("purpose", "REAUTH").containsEntry("five", true).containsEntry("open", true)
                .containsEntry("token_digest", AccountSecrets.linkTokenDigest(reauthToken));
        mvc.perform(get("/api/v1/me/sessions").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
    }

    @Test
    void reauthentication_with_a_wrong_password_is_a_422_that_counts_as_a_failure() throws Exception {
        Account account = account();
        String token = login(account.email(), OLD);

        reauth(token, "khong-phai-mat-khau-nay", "REVOKE_SESSIONS")
                .andExpect(status().isUnprocessableContent())
                .andExpect(Contract.matches())
                .andExpect(jsonPath("$.code").value("CURRENT_PASSWORD_INVALID"))
                .andExpect(jsonPath("$.fields[0].path").value("password"));
        assertThat(jdbc.sql("select count(*) from auth_login_failures where scope = 'ACCOUNT' and subject_digest = ?")
                .param(secrets.emailDigest(account.email())).query(Integer.class).single()).isEqualTo(1);
        assertThat(jdbc.sql("select count(*) from auth_challenges where account_id = ?")
                .param(account.id()).query(Integer.class).single()).isZero();
    }

    @Test
    void reauthentication_refuses_a_purpose_outside_the_contract() throws Exception {
        String token = login(account().email(), OLD);
        reauth(token, OLD, "ADMIN").andExpect(status().isUnprocessableContent()).andExpect(Contract.matches())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void both_need_a_session() throws Exception {
        post("/api/v1/me/password", null, Map.of("currentPassword", OLD, "newPassword", NEW), randomIp())
                .andExpect(status().isUnauthorized()).andExpect(Contract.matches());
        post("/api/v1/auth/reauth", null, Map.of("password", OLD, "purpose", "PRIVACY"), randomIp())
                .andExpect(status().isUnauthorized()).andExpect(Contract.matches());
    }

    @Test
    void AC_UC01_04_passwords_and_reauth_tokens_do_not_reach_the_log(CapturedOutput output) throws Exception {
        Account account = account();
        String token = login(account.email(), OLD);
        String reauthToken = JSON.readTree(reauth(token, OLD, "PRIVACY").andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString()).get("reauthToken").asString();
        change(token, "sai-mat-khau-de-ghi-log", NEW).andExpect(status().isUnprocessableContent());
        change(token, OLD, NEW).andExpect(status().isNoContent());

        assertThat(output.getAll()).doesNotContain(OLD).doesNotContain(NEW).doesNotContain("sai-mat-khau-de-ghi-log")
                .doesNotContain(reauthToken).doesNotContain(token).doesNotContain(account.email());
    }

    // ---------------------------------------------------------------- helpers

    private record Account(UUID id, String email) {}

    private Account account() {
        UUID id = UUID.randomUUID();
        String email = "doi-mat-khau-" + id + "@example.test";
        jdbc.sql("insert into accounts (id, role, status, email_digest, email_ciphertext, display_name) values (?, 'TUTOR', 'ACTIVE', ?, ?, 'Lan')")
                .params(id, secrets.emailDigest(email), secrets.encryptEmail(id, email)).update();
        jdbc.sql("insert into account_credentials (account_id, password_hash, hash_policy) values (?, ?, ?)")
                .params(id, hasher.hash(OLD), PasswordHasher.POLICY).update();
        return new Account(id, email);
    }

    private ResultActions change(String token, String current, String next) throws Exception {
        return change(token, current, next, randomIp());
    }

    private ResultActions change(String token, String current, String next, String ip) throws Exception {
        return post("/api/v1/me/password", token, Map.of("currentPassword", current, "newPassword", next), ip);
    }

    private ResultActions reauth(String token, String password, String purpose) throws Exception {
        return post("/api/v1/auth/reauth", token, Map.of("password", password, "purpose", purpose), randomIp());
    }

    private ResultActions loginAttempt(String email, String password) throws Exception {
        return post("/api/v1/auth/login", null, Map.of("email", email, "password", password), randomIp());
    }

    private String login(String email, String password) throws Exception {
        MvcResult result = loginAttempt(email, password).andExpect(status().isOk()).andReturn();
        return JSON.readTree(result.getResponse().getContentAsString()).get("accessToken").asString();
    }

    private ResultActions post(String path, String token, Map<String, String> body, String ip) throws Exception {
        var request = MockMvcRequestBuilders.post(path)
                .with(r -> {
                    r.setRemoteAddr(ip);
                    return r;
                })
                .contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(body));
        if (token != null) {
            request = request.header("Authorization", "Bearer " + token);
        }
        return mvc.perform(request);
    }

    private static String randomIp() {
        UUID u = UUID.randomUUID();
        return "10." + (u.getMostSignificantBits() & 0xff) + "." + ((u.getMostSignificantBits() >> 8) & 0xff) + "."
                + ((u.getLeastSignificantBits() & 0xfe) + 1);
    }
}
