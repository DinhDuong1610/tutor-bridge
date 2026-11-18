package com.tutorbridge.classes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.tutorbridge.Concurrently;
import com.tutorbridge.IntegrationTest;
import com.tutorbridge.PostgresTestSupport;
import com.tutorbridge.TutorBridgeApplication;
import com.tutorbridge.classes.OfferFixture.Need;
import com.tutorbridge.classes.OfferFixture.Student;
import com.tutorbridge.classes.OfferFixture.Tutor;
import com.tutorbridge.classes.api.AcceptOffer;
import com.tutorbridge.classes.api.AcceptOffer.AcceptedClass;
import com.tutorbridge.platform.api.ApiException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Each command runs through its own application instance with its own connection pool, so only
 * PostgreSQL is shared. A test holds a row lock until both commands are seen waiting, which proves
 * that they truly overlap instead of running one after the other.
 */
class AcceptOfferConcurrencyTest extends IntegrationTest {

    private record Outcome(AcceptedClass created, ApiException rejected) {}

    private static ConfigurableApplicationContext secondInstance;

    @Autowired
    AcceptOffer firstApi;

    @Autowired
    TransactionTemplate tx;

    AcceptOffer secondApi;

    OfferFixture fixture;

    @BeforeAll
    static void startSecondInstance() {
        secondInstance = new SpringApplicationBuilder(TutorBridgeApplication.class)
                .web(WebApplicationType.NONE)
                .run(java.util.stream.Stream.concat(
                        java.util.stream.Stream.of(
                                "--spring.datasource.url=" + PostgresTestSupport.jdbcUrl(),
                                "--spring.datasource.password=" + PostgresTestSupport.APP_PASSWORD,
                                "--spring.flyway.enabled=false"),
                        identityProperties().entrySet().stream().map(e -> "--" + e.getKey() + "=" + e.getValue()))
                        .toArray(String[]::new));
    }

    @AfterAll
    static void stopSecondInstance() {
        secondInstance.close();
    }

    @BeforeEach
    void setUp() {
        secondApi = secondInstance.getBean(AcceptOffer.class);
        fixture = new OfferFixture(jdbc, tx);
    }

    private static Connection connect() throws SQLException {
        return DriverManager.getConnection(
                PostgresTestSupport.jdbcUrl(), "tutorbridge_app", PostgresTestSupport.APP_PASSWORD);
    }

    private CompletableFuture<Outcome> submit(AcceptOffer api, Tutor tutor, UUID offer, String key) {
        return Concurrently.supply(() -> {
            try {
                return new Outcome(api.accept(tutor.id(), offer, 1, key), null);
            } catch (ApiException e) {
                return new Outcome(null, e);
            }
        });
    }

    private void awaitWaiters(int commands) {
        await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(20)).until(() ->
                fixture.count("select count(*) from pg_stat_activity where wait_event_type = 'Lock'") >= commands);
    }

    /**
     * Holds the row, proves both commands wait, then lets them race. Accounts are held with
     * FOR NO KEY UPDATE: FOR UPDATE would also stop the receipt foreign key, before any other lock.
     */
    private List<Outcome> race(String lockSql, UUID lockedId, Tutor firstTutor, UUID firstOffer,
            Tutor secondTutor, UUID secondOffer) throws Exception {
        CompletableFuture<Outcome> first;
        CompletableFuture<Outcome> second;
        try (Connection gate = connect()) {
            gate.setAutoCommit(false);
            try (var hold = gate.prepareStatement(lockSql)) {
                hold.setObject(1, lockedId);
                hold.execute();
            }
            first = submit(firstApi, firstTutor, firstOffer, UUID.randomUUID().toString());
            second = submit(secondApi, secondTutor, secondOffer, UUID.randomUUID().toString());
            awaitWaiters(2);
            gate.commit();
        }
        return List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));
    }

    private void assertOneWinner(List<Outcome> outcomes, String loserCode) {
        assertThat(outcomes).filteredOn(outcome -> outcome.created() != null).hasSize(1);
        assertThat(outcomes).filteredOn(outcome -> outcome.rejected() != null).singleElement()
                .satisfies(outcome -> assertThat(outcome.rejected().code()).isEqualTo(loserCode));
    }

    private void assertNoOrphans() {
        assertThat(fixture.count("""
                select count(*) from classes c
                where (select count(*) from lesson_sessions s where s.class_id = c.id) <> 4
                   or (select count(*) from resource_bookings b join lesson_sessions s on s.id = b.session_id
                       where s.class_id = c.id) <> 8
                """)).isZero();
    }

    @Test
    void AC_UC06_02_two_accepts_for_the_same_tutor_create_one_class() throws Exception {
        Tutor tutor = fixture.tutor();
        UUID firstOffer = fixture.offer(fixture.need(fixture.student()), tutor);
        UUID secondOffer = fixture.offer(fixture.need(fixture.student()), tutor);

        List<Outcome> outcomes = race(
                "select 1 from accounts where id = ? for no key update", tutor.id(), tutor, firstOffer, tutor, secondOffer);

        assertOneWinner(outcomes, "SCHEDULE_CONFLICT");
        assertThat(fixture.count("select count(*) from classes where tutor_id = ?", tutor.id())).isEqualTo(1);
        assertNoOrphans();
    }

    @Test
    void AC_UC06_02_two_accepts_for_the_same_student_create_one_class() throws Exception {
        Student student = fixture.student();
        Tutor firstTutor = fixture.tutor();
        Tutor secondTutor = fixture.tutor();
        UUID firstOffer = fixture.offer(fixture.need(student), firstTutor);
        UUID secondOffer = fixture.offer(fixture.need(student), secondTutor);

        List<Outcome> outcomes = race("select 1 from accounts where id = ? for no key update", student.parentId(),
                firstTutor, firstOffer, secondTutor, secondOffer);

        assertOneWinner(outcomes, "SCHEDULE_CONFLICT");
        assertThat(fixture.count("select count(*) from classes where student_id = ?", student.id())).isEqualTo(1);
        assertNoOrphans();
    }

    @Test
    void AC_UC06_03_two_offers_of_one_need_create_one_class() throws Exception {
        Need need = fixture.need(fixture.student());
        Tutor firstTutor = fixture.tutor();
        Tutor secondTutor = fixture.tutor();
        UUID firstOffer = fixture.offer(need, firstTutor);
        UUID secondOffer = fixture.offer(need, secondTutor);

        List<Outcome> outcomes = race("select 1 from learning_needs where id = ? for update", need.id(),
                firstTutor, firstOffer, secondTutor, secondOffer);

        assertOneWinner(outcomes, "SCOPE_CHANGED");
        assertThat(fixture.count("select count(*) from classes where originating_need_id = ?", need.id())).isEqualTo(1);
        assertThat(fixture.count("""
                select count(*) from class_offers
                where need_id = ? and state = 'CANCELLED' and reason_code = 'NEED_CLOSED'
                """, need.id())).isEqualTo(1);
        assertNoOrphans();
    }

    @Test
    void AC_UC06_04_duplicate_on_two_instances_returns_one_class() throws Exception {
        Need need = fixture.need(fixture.student());
        Tutor tutor = fixture.tutor();
        UUID offer = fixture.offer(need, tutor);
        String key = UUID.randomUUID().toString();
        CompletableFuture<Outcome> first;
        CompletableFuture<Outcome> second;
        try (Connection gate = connect()) {
            gate.setAutoCommit(false);
            try (var hold = gate.prepareStatement("select 1 from learning_needs where id = ? for update")) {
                hold.setObject(1, need.id());
                hold.execute();
            }
            first = submit(firstApi, tutor, offer, key);
            second = submit(secondApi, tutor, offer, key);
            awaitWaiters(2);
            gate.commit();
        }

        assertThat(first.get(30, TimeUnit.SECONDS).created()).isNotNull();
        assertThat(second.get(30, TimeUnit.SECONDS).created()).isEqualTo(first.get().created());
        assertThat(fixture.count("select count(*) from classes where originating_need_id = ?", need.id())).isEqualTo(1);
    }

    @Test
    void replay_after_the_first_instance_is_gone_returns_the_stored_class() {
        Need need = fixture.need(fixture.student());
        Tutor tutor = fixture.tutor();
        UUID offer = fixture.offer(need, tutor);
        String key = UUID.randomUUID().toString();

        AcceptedClass created = firstApi.accept(tutor.id(), offer, 1, key);

        assertThat(secondApi.accept(tutor.id(), offer, 1, key)).isEqualTo(created);
    }

    /**
     * ADR-0007: lock_timeout covers the whole transaction once the resource locks are taken, so a row
     * lock that is held too long after them is SERVICE_BUSY too, not an internal error (#160).
     */
    @Test
    void adr_0007_a_row_lock_held_past_the_timeout_after_the_resource_locks_is_503_and_writes_nothing()
            throws Exception {
        Student student = fixture.student();
        Need need = fixture.need(student);
        Tutor tutor = fixture.tutor();
        UUID offer = fixture.offer(need, tutor);
        String key = UUID.randomUUID().toString();
        Outcome outcome;
        try (Connection gate = connect()) {
            gate.setAutoCommit(false);
            try (var hold = gate.prepareStatement("select 1 from students where id = ? for update")) {
                hold.setObject(1, student.id());
                hold.execute();
            }
            CompletableFuture<Outcome> accepted = submit(firstApi, tutor, offer, key);
            // Waiting on the student row means the resource locks, and so lock_timeout, are already in place.
            await().atMost(Duration.ofSeconds(4)).pollInterval(Duration.ofMillis(20)).until(() -> fixture.count("""
                    select count(*) from pg_stat_activity
                    where wait_event_type = 'Lock' and query like 'select parent_id from students%'
                    """) == 1);
            outcome = accepted.get(30, TimeUnit.SECONDS);
            gate.rollback();
        }

        assertThat(outcome.rejected()).isNotNull();
        assertThat(outcome.rejected().status().value()).isEqualTo(503);
        assertThat(outcome.rejected().code()).isEqualTo("SERVICE_BUSY");
        assertThat(fixture.count("select count(*) from classes where originating_need_id = ?", need.id())).isZero();
        assertThat(fixture.count("select count(*) from command_receipts where idempotency_key = ?", key)).isZero();
        assertThat(fixture.count("select count(*) from class_offers where id = ? and state = 'PENDING'", offer))
                .isEqualTo(1);
        assertThat(fixture.count("select count(*) from learning_needs where id = ? and status = 'CONFIRMED'",
                need.id())).isEqualTo(1);

        // The client resends with the same key (#83); nothing was kept, so it runs as a new command.
        assertThat(firstApi.accept(tutor.id(), offer, 1, key).id()).isNotNull();
        assertNoOrphans();
    }

    @Test
    void deadlock_is_retried_and_the_command_still_succeeds() throws Exception {
        Need need = fixture.need(fixture.student());
        Tutor tutor = fixture.tutor();
        UUID offer = fixture.offer(need, tutor);

        try (Connection rival = connect()) {
            rival.setAutoCommit(false);
            try (var hold = rival.prepareStatement("select 1 from accounts where id = ? for no key update")) {
                hold.setObject(1, tutor.id());
                hold.execute();
            }
            CompletableFuture<Outcome> accepted = submit(firstApi, tutor, offer, UUID.randomUUID().toString());
            awaitWaiters(1);

            // The command holds the need and waits for the account, the rival now does the reverse.
            CompletableFuture<Void> rivalWaits = Concurrently.run(() -> {
                try (var cycle = rival.prepareStatement("update learning_needs set revision = revision where id = ?")) {
                    cycle.setObject(1, need.id());
                    cycle.execute();
                    rival.commit();
                } catch (SQLException e) {
                    throw new CompletionException(e);
                }
            });

            rivalWaits.get(30, TimeUnit.SECONDS);
            assertThat(accepted.get(30, TimeUnit.SECONDS).created()).isNotNull();
        }
    }
}
