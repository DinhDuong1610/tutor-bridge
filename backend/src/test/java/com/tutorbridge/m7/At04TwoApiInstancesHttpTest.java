package com.tutorbridge.m7;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.tutorbridge.Contract;
import com.tutorbridge.IntegrationTest;
import com.tutorbridge.PostgresTestSupport;
import com.tutorbridge.TutorBridgeApplication;
import com.tutorbridge.classes.OfferFixture;
import com.tutorbridge.classes.OfferFixture.Student;
import com.tutorbridge.classes.OfferFixture.Tutor;
import com.tutorbridge.identity.internal.AccountSecrets;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * TB-23, AT04 (NFR04; AC-UC06-02): two API instances, each a real web server on its own port with its own
 * connection pool, share one PostgreSQL. Two acceptOffer requests go over HTTP, one to each instance, with
 * real bearer tokens that the instances look up in {@code auth_sessions} (no test resolver):
 * <ul>
 * <li>two families' offers for the same hours of one tutor;
 * <li>one child's offers from two tutors for the same hours.
 * </ul>
 * Each resource ends with at most one class, and no class or session is left half made. The requests only
 * count as concurrent once both are seen waiting on a row the test holds (as in AcceptOfferConcurrencyTest,
 * which runs the same races through the service, without HTTP).
 *
 * <p>Fake accounts only. The tokens are random per test and never leave this JVM.
 */
class At04TwoApiInstancesHttpTest extends IntegrationTest {

    private static final String ACCEPT = "{\"acceptUnchangedTerms\":true}";
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    private static final List<ConfigurableApplicationContext> INSTANCES = new ArrayList<>();

    @Autowired
    TransactionTemplate tx;

    private OfferFixture fixture;

    private record Sent(String path, HttpResponse<String> answer) {}

    /** Started once, after this context has migrated the database, so neither instance runs Flyway. */
    @BeforeEach
    void startInstances() {
        fixture = new OfferFixture(jdbc, tx);
        while (INSTANCES.size() < 2) {
            INSTANCES.add(new SpringApplicationBuilder(TutorBridgeApplication.class)
                    .web(WebApplicationType.SERVLET)
                    .run(Stream.concat(
                            Stream.of(
                                    "--server.port=0",
                                    "--spring.datasource.url=" + PostgresTestSupport.jdbcUrl(),
                                    "--spring.datasource.password=" + PostgresTestSupport.APP_PASSWORD,
                                    "--spring.flyway.enabled=false"),
                            identityProperties().entrySet().stream().map(e -> "--" + e.getKey() + "=" + e.getValue()))
                            .toArray(String[]::new)));
        }
    }

    @AfterAll
    static void stopInstances() {
        INSTANCES.forEach(ConfigurableApplicationContext::close);
        INSTANCES.clear();
    }

    private static int port(int instance) {
        return ((WebServerApplicationContext) INSTANCES.get(instance)).getWebServer().getPort();
    }

    // ---------------------------------------------------------------- AT04

    @Test
    void AT04_two_families_accepted_for_the_same_hours_of_one_tutor_on_two_instances_make_one_class()
            throws Exception {
        Tutor tutor = fixture.tutor();
        UUID firstOffer = fixture.offer(fixture.need(fixture.student()), tutor);
        UUID secondOffer = fixture.offer(fixture.need(fixture.student()), tutor);
        String token = signIn(tutor.id());

        List<Sent> sent = race("select 1 from accounts where id = ? for no key update", tutor.id(),
                token, firstOffer, token, secondOffer);

        assertOneClassAndOneConflict(sent);
        assertThat(fixture.count("select count(*) from classes where tutor_id = ?", tutor.id())).isEqualTo(1);
        assertThat(fixture.count("""
                select count(*) from class_offers where id in (?, ?) and state = 'ACCEPTED'
                """, firstOffer, secondOffer)).isEqualTo(1);
        assertNothingHalfMade();
    }

    @Test
    void AT04_one_child_accepted_by_two_tutors_for_the_same_hours_on_two_instances_makes_one_class()
            throws Exception {
        Student student = fixture.student();
        Tutor firstTutor = fixture.tutor();
        Tutor secondTutor = fixture.tutor();
        UUID firstOffer = fixture.offer(fixture.need(student), firstTutor);
        UUID secondOffer = fixture.offer(fixture.need(student), secondTutor);

        List<Sent> sent = race("select 1 from accounts where id = ? for no key update", student.parentId(),
                signIn(firstTutor.id()), firstOffer, signIn(secondTutor.id()), secondOffer);

        assertOneClassAndOneConflict(sent);
        assertThat(fixture.count("select count(*) from classes where student_id = ?", student.id())).isEqualTo(1);
        assertThat(fixture.count("""
                select count(*) from class_offers where id in (?, ?) and state = 'ACCEPTED'
                """, firstOffer, secondOffer)).isEqualTo(1);
        assertNothingHalfMade();
    }

    /** Both instances serve real requests with a real session, so the races above are not a set-up artefact. */
    @Test
    void each_instance_answers_over_http_with_the_session_found_in_the_database() throws Exception {
        String token = signIn(fixture.tutor().id());
        for (int instance = 0; instance < 2; instance++) {
            Sent read = send(instance, "GET", "/api/v1/me/sessions", token, null);
            assertThat(read.answer().statusCode()).as("instance " + instance).isEqualTo(200);
            Contract.assertMatches("GET", read.path(), read.answer());

            Sent anonymous = send(instance, "GET", "/api/v1/me/sessions", null, null);
            assertThat(anonymous.answer().statusCode()).as("instance " + instance).isEqualTo(401);
        }
    }

    // ---------------------------------------------------------------- the race

    /**
     * Holds {@code lockSql}'s row, sends the first accept to instance 0 and the second to instance 1, waits
     * until both requests are blocked in PostgreSQL, then lets them go.
     */
    private List<Sent> race(String lockSql, UUID lockedId, String firstToken, UUID firstOffer, String secondToken,
            UUID secondOffer) throws Exception {
        CompletableFuture<Sent> first;
        CompletableFuture<Sent> second;
        try (Connection gate = connect()) {
            gate.setAutoCommit(false);
            try (var hold = gate.prepareStatement(lockSql)) {
                hold.setObject(1, lockedId);
                hold.execute();
            }
            int waitingBefore = waitingOnLocks();
            first = CompletableFuture.supplyAsync(() -> accept(0, firstToken, firstOffer));
            second = CompletableFuture.supplyAsync(() -> accept(1, secondToken, secondOffer));
            await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(20))
                    .until(() -> waitingOnLocks() >= waitingBefore + 2);
            // Neither request could answer while the row is held.
            assertThat(first).isNotDone();
            assertThat(second).isNotDone();
            gate.commit();
        }
        return List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));
    }

    private Sent accept(int instance, String token, UUID offer) {
        try {
            return send(instance, "POST", "/api/v1/offers/" + offer + "/accept", token, ACCEPT);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private Sent send(int instance, String method, String path, String token, String json) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port(instance) + path))
                .timeout(Duration.ofSeconds(30));
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        if (json == null) {
            request.GET();
        } else {
            request.header("Content-Type", "application/json")
                    .header("Idempotency-Key", UUID.randomUUID().toString())
                    .header("If-Match", "\"1\"")
                    .method(method, HttpRequest.BodyPublishers.ofString(json));
        }
        return new Sent(path, HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString()));
    }

    private int waitingOnLocks() {
        return fixture.count("select count(*) from pg_stat_activity where wait_event_type = 'Lock'");
    }

    private static Connection connect() throws SQLException {
        return DriverManager.getConnection(
                PostgresTestSupport.jdbcUrl(), "tutorbridge_app", PostgresTestSupport.APP_PASSWORD);
    }

    /**
     * A live session row for this account, as login leaves it, and its token. Created a minute ago so a
     * database clock slightly ahead of the JVM's does not make the session look issued in the future.
     */
    private String signIn(UUID accountId) {
        String token = UUID.randomUUID() + "-" + UUID.randomUUID();
        jdbc.sql("""
                insert into auth_sessions(id, account_id, token_digest, expires_at, device_label, created_at,
                                          last_seen_at)
                values (?, ?, ?, now() + interval '1 day', 'AT04', now() - interval '1 minute',
                        now() - interval '1 minute')
                """).params(UUID.randomUUID(), accountId, AccountSecrets.linkTokenDigest(token)).update();
        return token;
    }

    // ---------------------------------------------------------------- outcomes

    /** One 201 with a class, one 409 SCHEDULE_CONFLICT; both answers as the contract says. */
    private static void assertOneClassAndOneConflict(List<Sent> sent) {
        for (Sent each : sent) {
            Contract.assertMatches("POST", each.path(), each.answer());
        }
        assertThat(sent).extracting(each -> each.answer().statusCode()).containsExactlyInAnyOrder(201, 409);
        Sent rejected = sent.stream().filter(each -> each.answer().statusCode() == 409).findFirst().orElseThrow();
        assertThat(rejected.answer().body()).contains("\"code\":\"SCHEDULE_CONFLICT\"");
    }

    /**
     * No class lacks a session of its accepted offer or has other than two bookings per session, and no
     * session is without its class. Counts every class in the database, as AcceptOfferConcurrencyTest does.
     */
    private void assertNothingHalfMade() {
        assertThat(fixture.count("""
                select count(*) from classes c
                where (select count(*) from lesson_sessions s where s.class_id = c.id)
                      <> (select count(*) from offer_sessions o where o.offer_id = c.accepted_offer_id)
                   or (select count(*) from resource_bookings b join lesson_sessions s on s.id = b.session_id
                       where s.class_id = c.id)
                      <> 2 * (select count(*) from offer_sessions o where o.offer_id = c.accepted_offer_id)
                """)).isZero();
        assertThat(fixture.count("""
                select count(*) from lesson_sessions s
                where not exists (select 1 from classes c where c.id = s.class_id)
                """)).isZero();
    }
}
