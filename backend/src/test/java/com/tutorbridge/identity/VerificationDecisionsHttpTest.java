package com.tutorbridge.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tutorbridge.Concurrently;
import com.tutorbridge.Contract;
import com.tutorbridge.HttpTest;
import com.tutorbridge.PostgresTestSupport;
import com.tutorbridge.platform.api.Actor;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * decideVerification of TB-13 (UC03; AC-UC03-02): only the assigned verifier with VERIFY, never on their
 * own profile (INV-05), on the revision they read, once per key (INV-20), with every change, the audit row
 * and the receipt in one commit (INV-21). The cases of the transaction-change playbook are here, the
 * concurrent ones with a connection that holds the lock so both commands are seen waiting.
 */
class VerificationDecisionsHttpTest extends HttpTest {

    private static final String DESCRIPTION = "Dạy đại số lớp 8 theo từng bước, có bài tập về nhà mỗi buổi học.";

    // ---------------------------------------------------------------- helpers

    private Actor verifier() {
        Actor admin = newActor(Actor.Role.ADMIN);
        jdbc.sql("insert into admin_assignments(account_id, capability, granted_by) values (?, 'VERIFY', ?)")
                .params(admin.accountId(), admin.accountId()).update();
        return admin;
    }

    private UUID version(Actor owner, int version, String status, boolean current) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                insert into tutor_profiles(id, tutor_id, version, status, description, experience_months, rate_vnd_60,
                                           current, revision)
                values (?, ?, ?, ?, ?, 12, 180000, ?, 2)
                """).params(id, owner.accountId(), version, status, DESCRIPTION, current).update();
        return id;
    }

    /** A PENDING record of {@code profile} assigned to {@code verifier}, at revision 1. */
    private UUID record(UUID profile, Actor verifier) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                insert into tutor_verifications(id, profile_id, scope, status, evidence_key, assigned_to, revision)
                values (?, ?, 'MATH8_ALGEBRA', 'PENDING', ?, ?, 1)
                """).params(id, profile, "0".repeat(64), verifier == null ? null : verifier.accountId()).update();
        return id;
    }

    private static String body(UUID profile, String result) {
        return """
                {"profileVersionId":"%s","result":"%s","scope":"Toán 8, đại số","reason":"Bằng cử nhân sư phạm toán."}
                """.formatted(profile, result);
    }

    private ResultActions decide(Actor actor, UUID record, String key, String ifMatch, String json) throws Exception {
        MockHttpServletRequestBuilder request = post("/api/v1/admin/verifications/" + record + "/decision")
                .with(sessions().as(actor)).contentType(MediaType.APPLICATION_JSON).content(json);
        if (key != null) {
            request = request.header("Idempotency-Key", key);
        }
        if (ifMatch != null) {
            request = request.header("If-Match", ifMatch);
        }
        return mvc.perform(request).andExpect(Contract.matches());
    }

    private ResultActions decide(Actor actor, UUID record, UUID profile, String result) throws Exception {
        return decide(actor, record, UUID.randomUUID().toString(), "\"1\"", body(profile, result));
    }

    private String recordRow(UUID id) {
        return jdbc.sql("""
                select concat_ws('|', status, scope, coalesce(reason, '-'), coalesce(reviewed_by::text, '-'),
                                 (reviewed_at is not null)::text, revision)
                from tutor_verifications where id = ?""").param(id).query(String.class).single();
    }

    private String versionRow(UUID id) {
        return jdbc.sql("select concat_ws('|', status, current::text, revision) from tutor_profiles where id = ?").param(id)
                .query(String.class).single();
    }

    private int audits(UUID record) {
        return jdbc.sql("select count(*) from audit_events where object_id = ? and event_type = 'VERIFICATION_DECIDED'")
                .param(record).query(Integer.class).single();
    }

    private int receipts(Actor actor) {
        return jdbc.sql("select count(*) from command_receipts where actor_id = ? and command_type = 'DECIDE_VERIFICATION'")
                .param(actor.accountId()).query(Integer.class).single();
    }

    private void assertUnchanged(UUID record, UUID profile, Actor verifier) {
        assertThat(recordRow(record)).isEqualTo("PENDING|MATH8_ALGEBRA|-|-|false|1");
        assertThat(versionRow(profile)).isEqualTo("PENDING|false|2");
        assertThat(audits(record)).isZero();
        assertThat(receipts(verifier)).isZero();
    }

    private static Connection gate() throws Exception {
        Connection connection = DriverManager.getConnection(PostgresTestSupport.jdbcUrl(), "tutorbridge_app",
                PostgresTestSupport.APP_PASSWORD);
        connection.setAutoCommit(false);
        return connection;
    }

    private void awaitWaiting(String queryPrefix, int sessions) {
        await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(20)).until(() -> jdbc.sql("""
                select count(*) from pg_stat_activity where wait_event_type = 'Lock' and query like ?""")
                .param(queryPrefix + "%").query(Integer.class).single() == sessions);
    }

    private interface Call {
        ResultActions run() throws Exception;
    }

    private static CompletableFuture<Integer> async(Call call) {
        return Concurrently.supply(() -> {
            try {
                return call.run().andReturn().getResponse().getStatus();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
    }

    // ---------------------------------------------------------------- the three results

    @Test
    void AC_UC03_02_VERIFIED_approves_the_exact_version_and_takes_over_from_the_old_current_one() throws Exception {
        Actor me = verifier();
        Actor tutor = newActor(Actor.Role.TUTOR);
        UUID old = version(tutor, 1, "APPROVED", true);
        UUID submitted = version(tutor, 2, "PENDING", false);
        UUID id = record(submitted, me);

        decide(me, id, submitted, "VERIFIED").andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"2\""))
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.profileVersionId").value(submitted.toString()))
                .andExpect(jsonPath("$.assignedTo").value(me.accountId().toString()))
                .andExpect(jsonPath("$.status").value("VERIFIED"))
                .andExpect(jsonPath("$.scope").value("Toán 8, đại số"))
                .andExpect(jsonPath("$.revision").value(2));

        // Who, when, which version, what scope and why (AC-UC03-02).
        assertThat(recordRow(id)).isEqualTo("VERIFIED|Toán 8, đại số|Bằng cử nhân sư phạm toán.|" + me.accountId()
                + "|true|2");
        assertThat(versionRow(submitted)).isEqualTo("APPROVED|true|3");
        assertThat(versionRow(old)).as("OP-24 (1): no longer shown, kept for the record").isEqualTo("APPROVED|false|3");
        assertThat(audits(id)).isEqualTo(1);
        assertThat(receipts(me)).isEqualTo(1);
    }

    @Test
    void REJECTED_refuses_the_version_and_the_approved_one_stays_public() throws Exception {
        Actor me = verifier();
        Actor tutor = newActor(Actor.Role.TUTOR);
        UUID old = version(tutor, 1, "APPROVED", true);
        UUID submitted = version(tutor, 2, "PENDING", false);
        UUID id = record(submitted, me);

        decide(me, id, submitted, "REJECTED").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"));

        assertThat(recordRow(id)).startsWith("REJECTED|");
        assertThat(versionRow(submitted)).isEqualTo("REJECTED|false|3");
        assertThat(versionRow(old)).isEqualTo("APPROVED|true|2");
    }

    @Test
    void NEEDS_INFO_stays_PENDING_with_its_reason_and_time_and_can_be_decided_again() throws Exception {
        Actor me = verifier();
        Actor tutor = newActor(Actor.Role.TUTOR);
        UUID submitted = version(tutor, 1, "PENDING", false);
        UUID id = record(submitted, me);

        decide(me, id, submitted, "NEEDS_INFO").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING")).andExpect(jsonPath("$.revision").value(2));

        // Huy on #13: reviewed_at is written for NEEDS_INFO too, so the tutor sees when it was asked.
        assertThat(recordRow(id)).isEqualTo("PENDING|Toán 8, đại số|Bằng cử nhân sư phạm toán.|" + me.accountId()
                + "|true|2");
        assertThat(versionRow(submitted)).isEqualTo("PENDING|false|2");
        mvc.perform(get("/api/v1/me/tutor-profile/verification").with(sessions().as(tutor)))
                .andExpect(Contract.matches()).andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("NEEDS_INFO"))
                .andExpect(jsonPath("$.reason").value("Bằng cử nhân sư phạm toán."))
                .andExpect(jsonPath("$.decidedAt").isNotEmpty());

        decide(me, id, UUID.randomUUID().toString(), "\"2\"", body(submitted, "VERIFIED")).andExpect(status().isOk())
                .andExpect(jsonPath("$.revision").value(3));
        assertThat(versionRow(submitted)).isEqualTo("APPROVED|true|3");
    }

    // ---------------------------------------------------------------- idempotency (INV-20)

    @Test
    void the_same_key_and_body_replays_the_first_answer_even_after_a_later_decision_and_writes_nothing() throws Exception {
        Actor me = verifier();
        UUID submitted = version(newActor(Actor.Role.TUTOR), 1, "PENDING", false);
        UUID id = record(submitted, me);
        String key = UUID.randomUUID().toString();
        String first = decide(me, id, key, "\"1\"", body(submitted, "NEEDS_INFO")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        decide(me, id, UUID.randomUUID().toString(), "\"2\"", body(submitted, "VERIFIED")).andExpect(status().isOk());
        String after = recordRow(id);

        String replay = decide(me, id, key, "\"1\"", body(submitted, "NEEDS_INFO")).andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"2\""))
                .andReturn().getResponse().getContentAsString();

        assertThat(replay).isEqualTo(first);
        assertThat(recordRow(id)).isEqualTo(after);
        assertThat(audits(id)).isEqualTo(2);
        assertThat(receipts(me)).isEqualTo(2);
    }

    @Test
    void INV_22_a_replay_after_VERIFY_was_revoked_is_403_and_shows_nothing_of_the_first_answer() throws Exception {
        Actor me = verifier();
        UUID submitted = version(newActor(Actor.Role.TUTOR), 1, "PENDING", false);
        UUID id = record(submitted, me);
        String key = UUID.randomUUID().toString();
        decide(me, id, key, "\"1\"", body(submitted, "NEEDS_INFO")).andExpect(status().isOk());
        String after = recordRow(id);
        jdbc.sql("update admin_assignments set revoked_at = now() where account_id = ?").param(me.accountId())
                .update();

        String replay = decide(me, id, key, "\"1\"", body(submitted, "NEEDS_INFO"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
                .andReturn().getResponse().getContentAsString();

        assertThat(replay).doesNotContain(id.toString(), submitted.toString());
        assertThat(recordRow(id)).isEqualTo(after);
    }

    @Test
    void the_same_key_with_another_body_is_409_and_changes_nothing() throws Exception {
        Actor me = verifier();
        UUID submitted = version(newActor(Actor.Role.TUTOR), 1, "PENDING", false);
        UUID id = record(submitted, me);
        String key = UUID.randomUUID().toString();
        decide(me, id, key, "\"1\"", body(submitted, "NEEDS_INFO")).andExpect(status().isOk());
        String after = recordRow(id);

        decide(me, id, key, "\"1\"", body(submitted, "VERIFIED")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
        assertThat(recordRow(id)).isEqualTo(after);
    }

    // ---------------------------------------------------------------- refusals, each leaving nothing

    @Test
    void a_stale_If_Match_is_412_and_a_missing_one_or_key_is_refused_with_nothing_written() throws Exception {
        Actor me = verifier();
        UUID submitted = version(newActor(Actor.Role.TUTOR), 1, "PENDING", false);
        UUID id = record(submitted, me);

        decide(me, id, UUID.randomUUID().toString(), "\"7\"", body(submitted, "VERIFIED"))
                .andExpect(status().isPreconditionFailed()).andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
        decide(me, id, UUID.randomUUID().toString(), null, body(submitted, "VERIFIED"))
                .andExpect(status().isPreconditionRequired());
        decide(me, id, null, "\"1\"", body(submitted, "VERIFIED")).andExpect(status().is4xxClientError());
        assertUnchanged(id, submitted, me);
    }

    @Test
    void a_superseded_version_another_version_id_or_a_decided_record_is_409() throws Exception {
        Actor me = verifier();
        Actor tutor = newActor(Actor.Role.TUTOR);
        UUID retiredVersion = version(tutor, 1, "PENDING", false);
        UUID retired = record(retiredVersion, me);
        jdbc.sql("update tutor_profiles set status = 'RETIRED' where id = ?").param(retiredVersion).update();
        UUID submitted = version(newActor(Actor.Role.TUTOR), 1, "PENDING", false);
        UUID id = record(submitted, me);

        decide(me, retired, retiredVersion, "VERIFIED").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERIFICATION_SUPERSEDED"));
        decide(me, id, retiredVersion, "VERIFIED").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERIFICATION_SUPERSEDED"));
        assertUnchanged(id, submitted, me);

        decide(me, id, submitted, "REJECTED").andExpect(status().isOk());
        decide(me, id, UUID.randomUUID().toString(), "\"2\"", body(submitted, "VERIFIED"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("VERIFICATION_DECIDED"));
        assertThat(recordRow(id)).startsWith("REJECTED|");
    }

    @Test
    void INV_05_a_verifier_never_decides_a_profile_of_their_own() throws Exception {
        Actor me = verifier();
        UUID own = version(me, 1, "PENDING", false);
        UUID id = record(own, me);

        decide(me, id, own, "VERIFIED").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SELF_REVIEW_FORBIDDEN"))
                .andExpect(jsonPath("$.title").value(
                        "Bạn không thể thẩm định hồ sơ của chính mình. Hồ sơ này cần người xác minh khác."));
        assertUnchanged(id, own, me);
    }

    @Test
    void INV_06_another_verifiers_or_an_unassigned_record_is_404_and_without_VERIFY_it_is_403() throws Exception {
        Actor me = verifier();
        Actor other = verifier();
        UUID theirsVersion = version(newActor(Actor.Role.TUTOR), 1, "PENDING", false);
        UUID theirs = record(theirsVersion, other);
        UUID unassignedVersion = version(newActor(Actor.Role.TUTOR), 1, "PENDING", false);
        UUID unassigned = record(unassignedVersion, null);

        decide(me, UUID.randomUUID(), theirsVersion, "VERIFIED").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("VERIFICATION_NOT_FOUND"));
        decide(me, theirs, theirsVersion, "VERIFIED").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("VERIFICATION_NOT_FOUND"));
        decide(me, unassigned, unassignedVersion, "VERIFIED").andExpect(status().isNotFound());
        assertUnchanged(theirs, theirsVersion, other);

        jdbc.sql("update admin_assignments set revoked_at = now() where account_id = ?").param(other.accountId())
                .update();
        decide(other, theirs, theirsVersion, "REJECTED").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        decide(newActor(Actor.Role.TUTOR), theirs, theirsVersion, "REJECTED").andExpect(status().isForbidden());
        assertUnchanged(theirs, theirsVersion, other);
    }

    @Test
    void a_wrong_result_or_an_empty_or_too_long_scope_or_reason_is_422() throws Exception {
        Actor me = verifier();
        UUID submitted = version(newActor(Actor.Role.TUTOR), 1, "PENDING", false);
        UUID id = record(submitted, me);
        for (String json : List.of(body(submitted, "APPROVED"),
                body(submitted, "VERIFIED").replace("Toán 8, đại số", ""),
                body(submitted, "VERIFIED").replace("Toán 8, đại số", "x".repeat(201)),
                body(submitted, "VERIFIED").replace("Bằng cử nhân sư phạm toán.", "y".repeat(2001)))) {
            decide(me, id, UUID.randomUUID().toString(), "\"1\"", json).andExpect(status().isUnprocessableContent());
        }
        assertUnchanged(id, submitted, me);
    }

    // ---------------------------------------------------------------- at the same time

    @Test
    void two_decisions_at_once_on_one_record_both_wait_and_only_one_is_recorded() throws Exception {
        Actor me = verifier();
        Actor tutor = newActor(Actor.Role.TUTOR);
        UUID submitted = version(tutor, 1, "PENDING", false);
        UUID id = record(submitted, me);

        List<Integer> codes;
        try (Connection gate = gate()) {
            gate.createStatement().execute("select 1 from tutor_profiles where id = '" + submitted + "' for update");
            CompletableFuture<Integer> one = async(() -> decide(me, id, submitted, "VERIFIED"));
            CompletableFuture<Integer> two = async(() -> decide(me, id, submitted, "REJECTED"));
            awaitWaiting("select id, version, status, current from tutor_profiles", 2);
            gate.commit();
            codes = List.of(one.get(10, TimeUnit.SECONDS), two.get(10, TimeUnit.SECONDS));
        }

        assertThat(codes).containsExactlyInAnyOrder(200, 412);
        assertThat(audits(id)).isEqualTo(1);
        assertThat(receipts(me)).as("the refused one rolled its receipt back").isEqualTo(1);
        String stored = recordRow(id).split("\\|")[0];
        assertThat(versionRow(submitted)).isEqualTo(stored.equals("VERIFIED") ? "APPROVED|true|3" : "REJECTED|false|3");
    }

    @Test
    void a_decision_racing_the_submit_that_replaces_its_version_either_lands_first_or_is_409() throws Exception {
        Actor me = verifier();
        Actor tutor = newActor(Actor.Role.TUTOR);
        UUID asked = version(tutor, 1, "PENDING", false);
        UUID id = record(asked, me);
        jdbc.sql("update tutor_verifications set reason = 'Thiếu bằng cấp.', reviewed_by = ?, reviewed_at = now() where id = ?")
                .params(me.accountId(), id).update();
        UUID draft = version(tutor, 2, "DRAFT", false);
        UUID upload = UUID.randomUUID();
        jdbc.sql("""
                insert into tutor_evidence_uploads(id, owner_id, object_key, media_type, size_bytes, sha256, state,
                                                   expires_at)
                values (?, ?, ?, 'application/pdf', 100, ?, 'READY', now() + interval '15 minutes')
                """).params(upload, tutor.accountId(), UUID.randomUUID().toString(), "a".repeat(64)).update();

        List<Integer> codes;
        try (Connection gate = gate()) {
            gate.createStatement().execute("select 1 from tutor_profiles where id = '" + asked + "' for update");
            CompletableFuture<Integer> decision = async(() -> decide(me, id, asked, "VERIFIED"));
            CompletableFuture<Integer> submit = async(() -> mvc.perform(post("/api/v1/me/tutor-profile/submit")
                            .with(sessions().as(tutor)).header("Idempotency-Key", UUID.randomUUID().toString())
                            .header("If-Match", "\"2\"").contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"profileVersionId":"%s","evidenceUploadIds":["%s"]}
                                    """.formatted(draft, upload)))
                    .andExpect(Contract.matches()));
            awaitWaiting("select id, version, status, current from tutor_profiles", 1);
            awaitWaiting("select id from tutor_profiles where tutor_id = ", 1);
            gate.commit();
            codes = List.of(decision.get(10, TimeUnit.SECONDS), submit.get(10, TimeUnit.SECONDS));
        }

        if (codes.get(0) == 200) {
            // The decision went first: the version is approved, and the submit had nothing left to retire.
            assertThat(versionRow(asked)).isEqualTo("APPROVED|true|3");
            assertThat(codes.get(1)).isEqualTo(201);
        } else {
            // The submit went first (OP-24): the version is RETIRED and the record stays as it was left.
            assertThat(codes).containsExactly(409, 201);
            assertThat(versionRow(asked)).startsWith("RETIRED|");
            assertThat(recordRow(id)).startsWith("PENDING|MATH8_ALGEBRA|Thiếu bằng cấp.|");
            assertThat(audits(id)).isZero();
        }
    }

    @Test
    void VERIFY_revoked_while_the_decision_waits_is_403_and_nothing_is_written() throws Exception {
        Actor me = verifier();
        UUID submitted = version(newActor(Actor.Role.TUTOR), 1, "PENDING", false);
        UUID id = record(submitted, me);

        int code;
        try (Connection gate = gate()) {
            gate.createStatement().execute("select 1 from tutor_profiles where id = '" + submitted + "' for update");
            CompletableFuture<Integer> decision = async(() -> decide(me, id, submitted, "VERIFIED"));
            awaitWaiting("select id, version, status, current from tutor_profiles", 1);
            jdbc.sql("update admin_assignments set revoked_at = now() where account_id = ?").param(me.accountId())
                    .update();
            gate.commit();
            code = decision.get(10, TimeUnit.SECONDS);
        }

        assertThat(code).isEqualTo(403);
        assertUnchanged(id, submitted, me);
    }
}
