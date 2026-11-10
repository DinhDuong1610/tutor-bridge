package com.tutorbridge.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tutorbridge.Contract;
import com.tutorbridge.HttpTest;
import com.tutorbridge.identity.internal.AccountRegistration;
import com.tutorbridge.identity.internal.AccountSecrets;
import com.tutorbridge.platform.internal.OutboxDispatcher;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The verification email end to end on the worker role: register, the dispatcher sends through a
 * real SMTP server (Mailpit), the link in the email verifies the account (TB-8 PR 2b).
 */
@ExtendWith(OutputCaptureExtension.class)
class VerificationMailTest extends HttpTest {

    /** Same image as infra/compose.yaml. */
    static final GenericContainer<?> MAILPIT = new GenericContainer<>("axllent/mailpit:v1.32.0")
            .withExposedPorts(1025, 8025)
            .waitingFor(Wait.forHttp("/readyz").forPort(8025));

    static {
        MAILPIT.start();
    }

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final Pattern LINK = Pattern.compile("tutorbridge://verify-email\\?token=([A-Za-z0-9_-]+)");

    @DynamicPropertySource
    static void worker(DynamicPropertyRegistry registry) {
        registry.add("spring.mail.host", MAILPIT::getHost);
        registry.add("spring.mail.port", () -> MAILPIT.getMappedPort(1025));
        registry.add("tutorbridge.worker.enabled", () -> "true");
        // The test drives the dispatcher itself; the scheduled poll never fires during a test.
        registry.add("tutorbridge.worker.poll-interval", () -> "PT1H");
        registry.add("tutorbridge.identity.mail.from", () -> "no-reply@tutorbridge.invalid");
        registry.add("tutorbridge.identity.mail.link-base", () -> "tutorbridge://");
    }

    @Autowired
    private OutboxDispatcher dispatcher;

    @Autowired
    private AccountSecrets secrets;

    @BeforeEach
    void onlyThisTestsEvents() throws Exception {
        jdbc.sql("delete from outbox_events").update();
        HTTP.send(HttpRequest.newBuilder(mailpit("/api/v1/messages")).DELETE().build(),
                HttpResponse.BodyHandlers.discarding());
    }

    @Test
    void the_worker_mails_a_link_that_verifies_the_account() throws Exception {
        String email = uniqueEmail();
        register(email).andExpect(status().isAccepted());

        assertThat(dispatcher.dispatchOnce()).isEqualTo(1);

        List<JsonNode> mails = mailsTo(email);
        assertThat(mails).hasSize(1);
        JsonNode mail = mails.getFirst();
        assertThat(mail.get("Subject").asString()).isEqualTo("Xác minh email TutorBridge");
        assertThat(mail.get("From").get("Address").asString()).isEqualTo("no-reply@tutorbridge.invalid");
        String text = text(mail);
        assertThat(text).contains("30 phút");
        Matcher link = LINK.matcher(text);
        assertThat(link.find()).as("link in: %s", text).isTrue();

        verify(link.group(1)).andExpect(status().isNoContent()).andExpect(Contract.matches());
        assertThat(jdbc.sql("select status from accounts where email_digest = ?")
                .param(secrets.emailDigest(email)).query(String.class).single()).isEqualTo("ACTIVE");
        assertThat(jdbc.sql("select count(*) from outbox_events where delivered_at is null")
                .query(Integer.class).single()).isZero();
    }

    @Test
    void nothing_is_mailed_for_an_expired_code_and_the_event_is_still_closed() throws Exception {
        String email = uniqueEmail();
        register(email).andExpect(status().isAccepted());
        jdbc.sql("""
                update auth_challenges set created_at = created_at - interval '31 minutes',
                    expires_at = expires_at - interval '31 minutes'
                where account_id = (select id from accounts where email_digest = ?)
                """).param(secrets.emailDigest(email)).update();

        assertThat(dispatcher.dispatchOnce()).isEqualTo(1);
        assertThat(mailsTo(email)).isEmpty();
    }

    @Test
    void nothing_is_mailed_once_the_account_is_verified() throws Exception {
        String email = uniqueEmail();
        register(email).andExpect(status().isAccepted());
        Map<String, Object> event = jdbc.sql("""
                select payload->>'challengeId' as challenge, payload->>'tokenCiphertext' as ciphertext
                from outbox_events where event_type = ?
                """).param(AccountRegistration.VERIFICATION_REQUESTED).query().singleRow();
        verify(secrets.decryptLinkToken(UUID.fromString((String) event.get("challenge")),
                (String) event.get("ciphertext"))).andExpect(status().isNoContent());

        assertThat(dispatcher.dispatchOnce()).isEqualTo(1);
        assertThat(mailsTo(email)).isEmpty();
    }

    @Test
    void AC_UC01_04_sending_logs_neither_the_code_nor_the_email(CapturedOutput output) throws Exception {
        String email = uniqueEmail();
        register(email).andExpect(status().isAccepted());
        dispatcher.dispatchOnce();
        Matcher link = LINK.matcher(text(mailsTo(email).getFirst()));
        assertThat(link.find()).isTrue();

        assertThat(output.getAll()).doesNotContain(link.group(1)).doesNotContain(email);
    }

    private ResultActions register(String email) throws Exception {
        return mvc.perform(post("/api/v1/auth/register")
                .with(request -> {
                    request.setRemoteAddr("10.250." + (email.hashCode() & 0xff) + ".1");
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(Map.of("email", email, "password", "mot-mat-khau-du-dai-15",
                        "displayName", "Lan", "role", "PARENT", "termsVersion", TERMS_VERSION))));
    }

    private ResultActions verify(String token) throws Exception {
        return mvc.perform(post("/api/v1/auth/verify").contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(Map.of("token", token))));
    }

    private static List<JsonNode> mailsTo(String email) throws Exception {
        String query = URLEncoder.encode("to:\"" + email + "\"", StandardCharsets.UTF_8);
        JsonNode found = getJson("/api/v1/search?query=" + query);
        return found.get("messages").valueStream().toList();
    }

    private static String text(JsonNode summary) throws Exception {
        return getJson("/api/v1/message/" + summary.get("ID").asString()).get("Text").asString();
    }

    private static JsonNode getJson(String path) throws Exception {
        HttpResponse<String> response = HTTP.send(HttpRequest.newBuilder(mailpit(path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as("Mailpit %s", path).isEqualTo(200);
        return JSON.readTree(response.body());
    }

    private static URI mailpit(String path) {
        return URI.create("http://" + MAILPIT.getHost() + ":" + MAILPIT.getMappedPort(8025) + path);
    }

    private static String uniqueEmail() {
        return "thu-" + UUID.randomUUID() + "@example.test";
    }
}
