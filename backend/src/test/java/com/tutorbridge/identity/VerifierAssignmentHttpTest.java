package com.tutorbridge.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.tutorbridge.Concurrently;
import com.tutorbridge.Contract;
import com.tutorbridge.HttpTest;
import com.tutorbridge.PostgresTestSupport;
import com.tutorbridge.identity.internal.AdminAccounts;
import com.tutorbridge.platform.api.Actor;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/**
 * Who a verification goes to (TB-13): by turns among the ACTIVE ADMINs that hold VERIFY, when a tutor submits
 * (OP-28 (4), #98) and, for those nobody had, when admin-setup grants VERIFY (decision 4, #13). The tutor
 * never sees the verifier (OP-23).
 */
class VerifierAssignmentHttpTest extends HttpTest {

    private static final String SUBMIT = "/api/v1/me/tutor-profile/submit";
    private static final String DESCRIPTION = "Dạy đại số lớp 8 theo từng bước, có bài tập về nhà mỗi buổi học.";
    private static final OffsetDateTime T0 = OffsetDateTime.of(2026, 11, 20, 2, 0, 0, 0, ZoneOffset.UTC);

    @Autowired
    private AdminAccounts admins;

    /** The test database is shared: only the verifiers a test makes may take turns in it. */
    @BeforeEach
    void noOtherVerifier() {
        jdbc.sql("update admin_assignments set revoked_at = now() where capability = 'VERIFY' and revoked_at is null")
                .update();
    }

    // ---------------------------------------------------------------- helpers

    private Actor verifier() {
        Actor admin = newActor(Actor.Role.ADMIN);
        jdbc.sql("insert into admin_assignments(account_id, capability, granted_by) values (?, 'VERIFY', ?)")
                .params(admin.accountId(), admin.accountId()).update();
        return admin;
    }

    private UUID version(Actor tutor, String status) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                insert into tutor_profiles(id, tutor_id, version, status, description, experience_months, rate_vnd_60,
                                           revision)
                values (?, ?, 1, ?, ?, 12, 180000, 1)
                """).params(id, tutor.accountId(), status, DESCRIPTION).update();
        return id;
    }

    /** A tutor with a DRAFT and one READY file, ready to submit. */
    private record Ready(Actor tutor, UUID draft, UUID upload) {}

    private Ready ready() {
        Actor tutor = newActor(Actor.Role.TUTOR);
        UUID upload = UUID.randomUUID();
        jdbc.sql("""
                insert into tutor_evidence_uploads(id, owner_id, object_key, media_type, size_bytes, sha256, state,
                                                   expires_at)
                values (?, ?, ?, 'application/pdf', 100, ?, 'READY', now() + interval '15 minutes')
                """).params(upload, tutor.accountId(), UUID.randomUUID().toString(), "a".repeat(64)).update();
        return new Ready(tutor, version(tutor, "DRAFT"), upload);
    }

    /** Submits through the API and returns who the stored verification went to (null: nobody). */
    private UUID submit(Ready r) throws Exception {
        String json = mvc.perform(post(SUBMIT).with(sessions().as(r.tutor()))
                        .header("Idempotency-Key", UUID.randomUUID().toString()).header("If-Match", "\"1\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"profileVersionId":"%s","evidenceUploadIds":["%s"]}
                                """.formatted(r.draft(), r.upload())))
                .andExpect(Contract.matches())
                .andExpect(status().isCreated())
                // OP-23: the tutor is never told who verifies.
                .andExpect(jsonPath("$.assignedTo").value(Matchers.nullValue()))
                .andReturn().getResponse().getContentAsString();
        return assignee(UUID.fromString(JsonPath.read(json, "$.id")));
    }

    private UUID assignee(UUID verification) {
        return jdbc.sql("select assigned_to from tutor_verifications where id = ?").param(verification)
                .query(UUID.class).optional().orElse(null);
    }

    /** A record sent at {@code submitted}; one already assigned was given out at that same time. */
    private UUID waiting(Actor tutor, String profileStatus, OffsetDateTime submitted, Actor assignedTo) {
        UUID profile = version(tutor, "PENDING");
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                insert into tutor_verifications(id, profile_id, scope, status, evidence_key, assigned_to, submitted_at,
                                                assigned_at)
                values (?, ?, 'MATH8_ALGEBRA', 'PENDING', ?, ?, ?, ?)
                """).params(id, profile, "0".repeat(64), assignedTo == null ? null : assignedTo.accountId(), submitted,
                        assignedTo == null ? null : submitted)
                .update();
        if (!"PENDING".equals(profileStatus)) {
            jdbc.sql("update tutor_profiles set status = ? where id = ?").params(profileStatus, profile).update();
        }
        return id;
    }

    // ---------------------------------------------------------------- on submit

    @Test
    void OP_28_submissions_go_to_the_verifiers_by_turns() throws Exception {
        Actor a = verifier();
        Actor b = verifier();
        UUID first = a.accountId().toString().compareTo(b.accountId().toString()) < 0 ? a.accountId() : b.accountId();
        UUID second = first.equals(a.accountId()) ? b.accountId() : a.accountId();

        List<UUID> assigned = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            assigned.add(submit(ready()));
        }

        // Nobody assigned yet: by id. Then always the one assigned least recently.
        assertThat(assigned).containsExactly(first, second, first, second);
    }

    @Test
    void an_admin_without_VERIFY_or_not_ACTIVE_never_gets_one_and_with_nobody_it_waits_unassigned() throws Exception {
        Actor revoked = verifier();
        jdbc.sql("update admin_assignments set revoked_at = now() where account_id = ?").param(revoked.accountId())
                .update();
        Actor locked = verifier();
        jdbc.sql("update accounts set status = 'LOCKED' where id = ?").param(locked.accountId()).update();
        Actor support = newActor(Actor.Role.ADMIN);
        jdbc.sql("insert into admin_assignments(account_id, capability, granted_by) values (?, 'SUPPORT', ?)")
                .params(support.accountId(), support.accountId()).update();

        assertThat(submit(ready())).as("decision 4: accepted, nobody yet").isNull();

        Actor only = verifier();
        assertThat(List.of(submit(ready()), submit(ready()))).containsOnly(only.accountId());
    }

    @Test
    void two_submissions_at_once_wait_on_the_verifier_rows_and_go_to_different_verifiers() throws Exception {
        Actor a = verifier();
        Actor b = verifier();
        Ready one = ready();
        Ready two = ready();

        List<UUID> assigned;
        try (Connection gate = DriverManager.getConnection(PostgresTestSupport.jdbcUrl(), "tutorbridge_app",
                PostgresTestSupport.APP_PASSWORD)) {
            gate.setAutoCommit(false);
            gate.createStatement().execute("""
                    select 1 from admin_assignments where capability = 'VERIFY' and revoked_at is null for update""");
            CompletableFuture<UUID> first = Concurrently.supply(() -> unchecked(one));
            CompletableFuture<UUID> second = Concurrently.supply(() -> unchecked(two));
            // Both really overlap: each holds its own tutor's locks and waits on the VERIFY rows.
            await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(20)).until(() -> jdbc.sql("""
                    select count(*) from pg_stat_activity
                    where wait_event_type = 'Lock' and query like 'select g.account_id from admin_assignments%'""")
                    .query(Integer.class).single() == 2);
            gate.commit();
            assigned = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
        }

        assertThat(assigned).containsExactlyInAnyOrder(a.accountId(), b.accountId());
    }

    private UUID unchecked(Ready r) {
        try {
            return submit(r);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    // ---------------------------------------------------------------- on admin-setup

    @Test
    void decision_4_admin_setup_shares_the_waiting_verifications_oldest_first_by_turns() throws Exception {
        // Records other test classes left unassigned would take turns too: give them to an account that is
        // not a verifier, so only this test's records wait.
        Actor bystander = newActor(Actor.Role.ADMIN);
        jdbc.sql("""
                update tutor_verifications set assigned_to = ? where status = 'PENDING' and assigned_to is null
                and profile_id in (select id from tutor_profiles where status <> 'RETIRED')""")
                .param(bystander.accountId()).update();
        Actor existing = verifier();
        UUID alreadyHis = waiting(newActor(Actor.Role.TUTOR), "PENDING", T0.plusMinutes(30), existing);
        UUID w1 = waiting(newActor(Actor.Role.TUTOR), "PENDING", T0, null);
        UUID w2 = waiting(newActor(Actor.Role.TUTOR), "PENDING", T0.plusMinutes(1), null);
        UUID w3 = waiting(newActor(Actor.Role.TUTOR), "PENDING", T0.plusMinutes(2), null);
        UUID retired = waiting(newActor(Actor.Role.TUTOR), "RETIRED", T0.plusMinutes(3), null);

        String email = "quan-tri-" + UUID.randomUUID() + "@example.test";
        Path file = evidenceDir().resolve("admin-setup-assign-" + UUID.randomUUID() + ".txt");
        AdminAccounts.Result result = admins.setUp(email, "mat-khau-quan-tri-du-dai", file);

        // The new verifier was never assigned, so has the first turn; then the existing one, and round again.
        assertThat(result.assigned()).isEqualTo(3);
        assertThat(assignee(w1)).isEqualTo(result.accountId());
        assertThat(assignee(w2)).isEqualTo(existing.accountId());
        assertThat(assignee(w3)).isEqualTo(result.accountId());
        assertThat(assignee(alreadyHis)).as("an assigned record keeps its verifier").isEqualTo(existing.accountId());
        assertThat(assignee(retired)).as("a superseded record waits for nothing (OP-24)").isNull();
        // Codex's review of #219: the turn follows the assignments just made, not when the records were
        // sent. The new verifier got the last one, so the next submission goes to the existing one.
        assertThat(submit(ready())).isEqualTo(existing.accountId());
        assertThat(submit(ready())).isEqualTo(result.accountId());
    }
}
