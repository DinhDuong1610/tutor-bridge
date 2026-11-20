package com.tutorbridge.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.tutorbridge.Concurrently;
import com.tutorbridge.Contract;
import com.tutorbridge.HttpTest;
import com.tutorbridge.PostgresTestSupport;
import com.tutorbridge.platform.api.Actor;
import com.tutorbridge.platform.api.CanonicalJson;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * UC03 for the tutor (TB-12c PR 2a): submitTutorProfile. Real PostgreSQL, every answer checked against
 * the contract. Fake accounts and texts only.
 */
class TutorProfileSubmissionHttpTest extends HttpTest {

    private static final String PATH = "/api/v1/me/tutor-profile/submit";
    private static final String DESCRIPTION = "Dạy đại số lớp 8 theo từng bước, có bài tập về nhà mỗi buổi học.";
    private static final String UNUSABLE = "Minh chứng này không dùng được. Vui lòng chọn lại.";

    // ---------------------------------------------------------------- helpers

    private ResultActions submit(Actor actor, String key, String ifMatch, String json) throws Exception {
        MockHttpServletRequestBuilder request = post(PATH).with(sessions().as(actor))
                .contentType(MediaType.APPLICATION_JSON).content(json);
        if (key != null) {
            request.header("Idempotency-Key", key);
        }
        if (ifMatch != null) {
            request.header("If-Match", ifMatch);
        }
        return mvc.perform(request).andExpect(Contract.matches());
    }

    private ResultActions submit(Actor actor, long revision, UUID version, List<UUID> evidence) throws Exception {
        return submit(actor, newKey(), etag(revision), body(version, evidence));
    }

    private static String body(UUID version, List<UUID> evidence) {
        return """
                {"profileVersionId":"%s","evidenceUploadIds":[%s]}
                """.formatted(version, evidence.stream().map(id -> "\"" + id + "\"").collect(Collectors.joining(",")));
    }

    private static String etag(long revision) {
        return "\"" + revision + "\"";
    }

    private static String newKey() {
        return UUID.randomUUID().toString();
    }

    private UUID version(Actor tutor, int version, long revision, String status, boolean current) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                insert into tutor_profiles(id, tutor_id, version, status, description, experience_months, rate_vnd_60,
                                           current, revision)
                values (?, ?, ?, ?, ?, 12, 180000, ?, ?)
                """).params(id, tutor.accountId(), version, status, DESCRIPTION, current, revision).update();
        return id;
    }

    private UUID draft(Actor tutor, int version, long revision) {
        return version(tutor, version, revision, "DRAFT", false);
    }

    private UUID verification(UUID profileId, String status, String reason) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                insert into tutor_verifications(id, profile_id, scope, status, evidence_key, reason)
                values (?, ?, 'MATH8_ALGEBRA', ?, ?, ?)
                """).params(id, profileId, status, "0".repeat(64), reason).update();
        return id;
    }

    private UUID upload(Actor owner, String state) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                insert into tutor_evidence_uploads(id, owner_id, object_key, media_type, size_bytes, sha256, state,
                                                   expires_at)
                values (?, ?, ?, 'application/pdf', 100, ?, ?, now() + interval '15 minutes')
                """).params(id, owner.accountId(), UUID.randomUUID().toString(), "a".repeat(64), state).update();
        return id;
    }

    private List<UUID> ready(Actor owner, int count) {
        return IntStream.range(0, count).mapToObj(i -> upload(owner, "READY")).toList();
    }

    /** A whole stored version, to prove it did not change at all. */
    private String profileRow(UUID id) {
        return jdbc.sql("""
                select concat_ws('|', version, status, revision, description, experience_months, rate_vnd_60, subject,
                                 current)
                from tutor_profiles where id = ?
                """).param(id).query(String.class).single();
    }

    /** A whole stored verification, to prove it stayed as the verifier left it. */
    private String verificationRow(UUID id) {
        return jdbc.sql("""
                select concat_ws('|', profile_id, scope, status, evidence_key, coalesce(reason, '-'),
                                 coalesce(reviewed_by::text, '-'), coalesce(reviewed_at::text, '-'),
                                 coalesce(assigned_to::text, '-'), revision)
                from tutor_verifications where id = ?
                """).param(id).query(String.class).single();
    }

    private String statusOf(UUID profileId) {
        return jdbc.sql("select status from tutor_profiles where id = ?").param(profileId).query(String.class).single();
    }

    private int count(String sql, Object... params) {
        return jdbc.sql(sql).params(params).query(Integer.class).single();
    }

    private int verificationsOf(UUID profileId) {
        return count("select count(*) from tutor_verifications where profile_id = ?", profileId);
    }

    private int linksOf(UUID profileId) {
        return count("select count(*) from tutor_profile_evidence where profile_id = ?", profileId);
    }

    private int receiptsOf(Actor actor) {
        return count("select count(*) from command_receipts where actor_id = ?", actor.accountId());
    }

    private int auditsOf(Actor actor) {
        return count("select count(*) from audit_events where actor_id = ?", actor.accountId());
    }

    /** Nothing at all was written for this tutor: no version moved, no verification, link, receipt or audit. */
    private void assertNothingWritten(Actor tutor, UUID draft, String draftBefore) {
        assertThat(profileRow(draft)).isEqualTo(draftBefore);
        assertThat(verificationsOf(draft)).isZero();
        assertThat(linksOf(draft)).isZero();
        assertThat(receiptsOf(tutor)).isZero();
        assertThat(auditsOf(tutor)).isZero();
    }

    private static String evidenceKey(List<UUID> evidence) {
        return CanonicalJson.sha256(evidence.stream().map(UUID::toString).sorted().toList());
    }

    private interface Call {
        ResultActions run() throws Exception;
    }

    private static int statusCode(Call call) {
        try {
            return call.run().andReturn().getResponse().getStatus();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static Connection gate() throws SQLException {
        Connection connection = DriverManager.getConnection(
                PostgresTestSupport.jdbcUrl(), "tutorbridge_app", PostgresTestSupport.APP_PASSWORD);
        connection.setAutoCommit(false);
        return connection;
    }

    private void awaitWaiting(String queryPrefix, int sessions) {
        await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(20)).until(() -> count("""
                select count(*) from pg_stat_activity where wait_event_type = 'Lock' and query like ?
                """, queryPrefix + "%") == sessions);
    }

    // ---------------------------------------------------------------- submit

    @Test
    void UC03_submit_freezes_the_draft_links_the_evidence_and_opens_a_waiting_verification() throws Exception {
        Actor tutor = newActor(Actor.Role.TUTOR);
        UUID draft = draft(tutor, 1, 3);
        List<UUID> evidence = ready(tutor, 1);

        String json = submit(tutor, 3, draft, evidence)
                .andExpect(status().isCreated())
                .andExpect(header().string("ETag", "\"1\""))
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(jsonPath("$.profileVersionId").value(draft.toString()))
                .andExpect(jsonPath("$.assignedTo").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.scope").value("MATH8_ALGEBRA"))
                .andExpect(jsonPath("$.revision").value(1))
                .andReturn().getResponse().getContentAsString();
        UUID verification = UUID.fromString(JsonPath.read(json, "$.id"));

        assertThat(jdbc.sql("select concat_ws('|', status, revision) from tutor_profiles where id = ?").param(draft)
                .query(String.class).single()).isEqualTo("PENDING|4");
        assertThat(verificationRow(verification))
                .isEqualTo(draft + "|MATH8_ALGEBRA|PENDING|" + evidenceKey(evidence) + "|-|-|-|-|1");
        assertThat(jdbc.sql("select upload_id from tutor_profile_evidence where profile_id = ?").param(draft)
                .query(UUID.class).list()).containsExactlyElementsOf(evidence);
        assertThat(receiptsOf(tutor)).isEqualTo(1);
        // No version needed information, so nothing was superseded.
        assertThat(auditsOf(tutor)).isZero();
    }

    @Test
    void UC03_ten_evidence_files_are_taken_and_the_key_is_the_sorted_set() throws Exception {
        Actor tutor = newActor(Actor.Role.TUTOR);
        UUID draft = draft(tutor, 1, 1);
        List<UUID> evidence = ready(tutor, 10);

        submit(tutor, 1, draft, evidence).andExpect(status().isCreated());

        assertThat(linksOf(draft)).isEqualTo(10);
        assertThat(jdbc.sql("select evidence_key from tutor_verifications where profile_id = ?").param(draft)
                .query(String.class).single()).isEqualTo(evidenceKey(evidence.reversed()));
    }

    @Test
    void G3_D07_04_no_evidence_or_eleven_files_are_422_and_nothing_is_written() throws Exception {
        Actor tutor = newActor(Actor.Role.TUTOR);
        UUID draft = draft(tutor, 1, 1);
        String before = profileRow(draft);

        submit(tutor, 1, draft, List.of()).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields[0].path").value("evidenceUploadIds"));
        submit(tutor, 1, draft, ready(tutor, 11)).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.fields[0].path").value("evidenceUploadIds"));

        assertNothingWritten(tutor, draft, before);
    }

    @Test
    void INV_06_unusable_evidence_is_one_422_for_every_reason_and_nothing_is_written() throws Exception {
        Actor tutor = newActor(Actor.Role.TUTOR);
        Actor other = newActor(Actor.Role.TUTOR);
        UUID draft = draft(tutor, 1, 1);
        String before = profileRow(draft);
        UUID mine = upload(tutor, "READY");
        UUID othersReady = upload(other, "READY");
        UUID unknown = UUID.randomUUID();
        UUID quarantined = upload(tutor, "QUARANTINED");
        UUID failed = upload(tutor, "FAILED");

        submit(tutor, 1, draft, List.of(mine, othersReady, unknown, quarantined, failed))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields.length()").value(4))
                .andExpect(jsonPath("$.fields[*].path").value(Matchers.contains("evidenceUploadIds[1]",
                        "evidenceUploadIds[2]", "evidenceUploadIds[3]", "evidenceUploadIds[4]")))
                .andExpect(jsonPath("$.fields[*].message").value(Matchers.everyItem(Matchers.is(UNUSABLE))));

        assertNothingWritten(tutor, draft, before);
    }

    @Test
    void a_null_or_missing_evidence_list_is_422_not_a_server_error() throws Exception {
        Actor tutor = newActor(Actor.Role.TUTOR);
        UUID draft = draft(tutor, 1, 1);
        String before = profileRow(draft);

        submit(tutor, newKey(), etag(1), """
                {"profileVersionId":"%s","evidenceUploadIds":[null]}
                """.formatted(draft)).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        submit(tutor, newKey(), etag(1), """
                {"profileVersionId":"%s"}
                """.formatted(draft)).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        assertNothingWritten(tutor, draft, before);
    }

    @Test
    void the_same_evidence_twice_is_422_on_the_repeat() throws Exception {
        Actor tutor = newActor(Actor.Role.TUTOR);
        UUID draft = draft(tutor, 1, 1);
        String before = profileRow(draft);
        UUID evidence = upload(tutor, "READY");

        submit(tutor, 1, draft, List.of(evidence, evidence)).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.fields.length()").value(1))
                .andExpect(jsonPath("$.fields[0].path").value("evidenceUploadIds[1]"))
                .andExpect(jsonPath("$.fields[0].message").value(UNUSABLE));

        assertNothingWritten(tutor, draft, before);
    }

    @Test
    void UC03_evidence_already_used_by_an_earlier_version_is_taken_again() throws Exception {
        Actor tutor = newActor(Actor.Role.TUTOR);
        UUID evidence = upload(tutor, "READY");
        UUID rejected = draft(tutor, 1, 1);
        jdbc.sql("insert into tutor_profile_evidence(profile_id, upload_id) values (?, ?)").params(rejected, evidence)
                .update();
        jdbc.sql("update tutor_profiles set status = 'REJECTED' where id = ?").param(rejected).update();
        UUID draft = draft(tutor, 2, 2);

        submit(tutor, 2, draft, List.of(evidence)).andExpect(status().isCreated());

        assertThat(linksOf(rejected)).isEqualTo(1);
        assertThat(linksOf(draft)).isEqualTo(1);
    }

    // ---------------------------------------------------------------- versions (Huy, 20/11/2026)

    @Test
    void INV_23_a_stale_if_match_is_412_and_a_missing_one_428() throws Exception {
        Actor tutor = newActor(Actor.Role.TUTOR);
        UUID draft = draft(tutor, 1, 2);
        String before = profileRow(draft);
        List<UUID> evidence = ready(tutor, 1);

        submit(tutor, 1, draft, evidence).andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
        submit(tutor, newKey(), null, body(draft, evidence)).andExpect(status().isPreconditionRequired());

        assertNothingWritten(tutor, draft, before);
    }

    @Test
    void a_version_that_is_not_the_newest_is_409() throws Exception {
        Actor tutor = newActor(Actor.Role.TUTOR);
        UUID older = version(tutor, 1, 1, "REJECTED", false);
        UUID draft = draft(tutor, 2, 2);
        String before = profileRow(draft);

        submit(tutor, 2, older, ready(tutor, 1)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TUTOR_PROFILE_NOT_DRAFT"));

        assertNothingWritten(tutor, draft, before);
        assertThat(statusOf(older)).isEqualTo("REJECTED");
    }

    @Test
    void a_version_already_submitted_is_409() throws Exception {
        Actor tutor = newActor(Actor.Role.TUTOR);
        UUID draft = draft(tutor, 1, 1);
        List<UUID> evidence = ready(tutor, 1);
        submit(tutor, 1, draft, evidence).andExpect(status().isCreated());

        // The If-Match is the version's current revision, so only its status is wrong.
        submit(tutor, 2, draft, evidence).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TUTOR_PROFILE_NOT_DRAFT"));

        assertThat(verificationsOf(draft)).isEqualTo(1);
    }

    @Test
    void a_tutor_without_a_profile_is_404() throws Exception {
        Actor tutor = newActor(Actor.Role.TUTOR);

        submit(tutor, 1, UUID.randomUUID(), ready(tutor, 1)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TUTOR_PROFILE_NOT_FOUND"));

        assertThat(receiptsOf(tutor)).isZero();
    }

    // ---------------------------------------------------------------- OP-24 (Đính, 17/11/2026)

    @Test
    void OP_24_submitting_over_a_version_that_needs_information_retires_it_and_keeps_its_verification()
            throws Exception {
        Actor tutor = newActor(Actor.Role.TUTOR);
        UUID needsInfo = version(tutor, 1, 1, "PENDING", false);
        UUID oldVerification = verification(needsInfo, "PENDING", "Cần thêm bằng cấp.");
        String oldBefore = verificationRow(oldVerification);
        UUID draft = draft(tutor, 2, 2);

        submit(tutor, 2, draft, ready(tutor, 1)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.profileVersionId").value(draft.toString()));

        assertThat(statusOf(needsInfo)).isEqualTo("RETIRED");
        assertThat(verificationRow(oldVerification)).isEqualTo(oldBefore);
        assertThat(statusOf(draft)).isEqualTo("PENDING");
        assertThat(jdbc.sql("select reason is null from tutor_verifications where profile_id = ?").param(draft)
                .query(Boolean.class).single()).isTrue();
        UUID receipt = jdbc.sql("select id from command_receipts where actor_id = ?").param(tutor.accountId())
                .query(UUID.class).single();
        assertThat(jdbc.sql("""
                select concat_ws('|', command_id, event_type, object_type, object_id, outcome, (length(trace_id) > 0)::text)
                from audit_events where actor_id = ?
                """).param(tutor.accountId()).query(String.class).single())
                .isEqualTo(receipt + "|VERIFICATION_SUPERSEDED|TUTOR_VERIFICATION|" + oldVerification
                        + "|SUPERSEDED|true");
    }

    @Test
    void OP_24_a_version_still_waiting_for_its_verifier_blocks_the_submission_with_409() throws Exception {
        Actor tutor = newActor(Actor.Role.TUTOR);
        UUID waiting = version(tutor, 1, 1, "PENDING", false);
        UUID waitingVerification = verification(waiting, "PENDING", null);
        String waitingBefore = profileRow(waiting);
        String verificationBefore = verificationRow(waitingVerification);
        UUID draft = draft(tutor, 2, 2);
        String before = profileRow(draft);

        submit(tutor, 2, draft, ready(tutor, 1)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TUTOR_PROFILE_UNDER_REVIEW"));

        assertNothingWritten(tutor, draft, before);
        assertThat(profileRow(waiting)).isEqualTo(waitingBefore);
        assertThat(verificationRow(waitingVerification)).isEqualTo(verificationBefore);
    }

    @Test
    void OP_24_a_rejected_version_and_the_approved_current_one_are_left_alone() throws Exception {
        Actor tutor = newActor(Actor.Role.TUTOR);
        UUID approved = version(tutor, 1, 1, "APPROVED", true);
        UUID rejected = version(tutor, 2, 2, "REJECTED", false);
        UUID rejectedVerification = verification(rejected, "REJECTED", "Chưa đủ minh chứng.");
        String approvedBefore = profileRow(approved);
        String rejectedBefore = profileRow(rejected);
        String verificationBefore = verificationRow(rejectedVerification);
        UUID draft = draft(tutor, 3, 3);

        submit(tutor, 3, draft, ready(tutor, 1)).andExpect(status().isCreated());

        // OP-24 (1): parents keep seeing the approved version until the new one is approved.
        assertThat(profileRow(approved)).isEqualTo(approvedBefore);
        assertThat(profileRow(rejected)).isEqualTo(rejectedBefore);
        assertThat(verificationRow(rejectedVerification)).isEqualTo(verificationBefore);
        assertThat(auditsOf(tutor)).isZero();
    }

    // ---------------------------------------------------------------- idempotency

    @Test
    void INV_20_the_same_key_and_body_replay_the_first_answer_without_a_second_verification() throws Exception {
        Actor tutor = newActor(Actor.Role.TUTOR);
        UUID draft = draft(tutor, 1, 1);
        String key = newKey();
        String json = body(draft, ready(tutor, 2));

        String first = submit(tutor, key, etag(1), json).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        submit(tutor, key, etag(1), json).andExpect(status().isCreated())
                .andExpect(header().string("ETag", "\"1\""))
                .andExpect(jsonPath("$.id").value(JsonPath.<String>read(first, "$.id")));

        assertThat(verificationsOf(draft)).isEqualTo(1);
        assertThat(receiptsOf(tutor)).isEqualTo(1);
    }

    @Test
    void INV_06_a_replay_after_assignment_does_not_name_the_verifier() throws Exception {
        Actor tutor = newActor(Actor.Role.TUTOR);
        Actor verifier = newActor(Actor.Role.ADMIN);
        UUID draft = draft(tutor, 1, 1);
        String key = newKey();
        String json = body(draft, ready(tutor, 1));
        submit(tutor, key, etag(1), json).andExpect(status().isCreated());
        jdbc.sql("update tutor_verifications set assigned_to = ?, revision = 2 where profile_id = ?")
                .params(verifier.accountId(), draft).update();

        submit(tutor, key, etag(1), json).andExpect(status().isCreated())
                .andExpect(jsonPath("$.assignedTo").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.revision").value(1));
    }

    @Test
    void INV_20_a_replay_keeps_the_first_scope_when_the_verification_scope_changes() throws Exception {
        Actor tutor = newActor(Actor.Role.TUTOR);
        UUID draft = draft(tutor, 1, 1);
        String key = newKey();
        String json = body(draft, ready(tutor, 1));
        String first = submit(tutor, key, etag(1), json).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String scope = JsonPath.read(first, "$.scope");
        // A verifier may later narrow or widen what the verification covers; the first answer stays (Codex, #190).
        jdbc.sql("update tutor_verifications set scope = ?, revision = 2 where profile_id = ?")
                .params(scope + "_CHANGED", draft).update();

        submit(tutor, key, etag(1), json).andExpect(status().isCreated())
                .andExpect(jsonPath("$.scope").value(scope))
                .andExpect(jsonPath("$.revision").value(1));
    }

    @Test
    void INV_20_the_same_key_with_another_body_is_409() throws Exception {
        Actor tutor = newActor(Actor.Role.TUTOR);
        UUID draft = draft(tutor, 1, 1);
        String key = newKey();
        submit(tutor, key, etag(1), body(draft, ready(tutor, 1))).andExpect(status().isCreated());

        submit(tutor, key, etag(1), body(draft, ready(tutor, 1))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));

        assertThat(verificationsOf(draft)).isEqualTo(1);
    }

    @Test
    void INV_20_a_missing_key_is_400() throws Exception {
        Actor tutor = newActor(Actor.Role.TUTOR);
        UUID draft = draft(tutor, 1, 1);
        String before = profileRow(draft);

        submit(tutor, null, etag(1), body(draft, ready(tutor, 1))).andExpect(status().isBadRequest());

        assertNothingWritten(tutor, draft, before);
    }

    // ---------------------------------------------------------------- concurrency (transaction-change)

    @Test
    void two_submits_of_one_draft_at_once_open_one_verification_and_the_other_is_412() throws Exception {
        Actor tutor = newActor(Actor.Role.TUTOR);
        UUID draft = draft(tutor, 1, 1);
        CompletableFuture<Integer> first;
        CompletableFuture<Integer> second;
        try (Connection gate = gate()) {
            try (var hold = gate.prepareStatement("select 1 from tutor_profiles where id = ? for update")) {
                hold.setObject(1, draft);
                hold.execute();
            }
            first = Concurrently.supply(() -> statusCode(() -> submit(tutor, 1, draft, ready(tutor, 1))));
            second = Concurrently.supply(() -> statusCode(() -> submit(tutor, 1, draft, ready(tutor, 1))));
            awaitWaiting("select id, version, revision", 2);
            gate.commit();
        }

        assertThat(List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS)))
                .containsExactlyInAnyOrder(201, 412);
        assertThat(verificationsOf(draft)).isEqualTo(1);
        assertThat(linksOf(draft)).isEqualTo(1);
        assertThat(receiptsOf(tutor)).isEqualTo(1);
    }

    @Test
    void OP_24_a_decision_racing_the_submit_that_retires_its_version_is_refused_after_the_submit() throws Exception {
        Actor tutor = newActor(Actor.Role.TUTOR);
        UUID needsInfo = version(tutor, 1, 1, "PENDING", false);
        UUID oldVerification = verification(needsInfo, "PENDING", "Cần thêm bằng cấp.");
        String oldBefore = verificationRow(oldVerification);
        UUID draft = draft(tutor, 2, 2);
        UUID evidence = upload(tutor, "READY");
        CompletableFuture<Integer> submitted;
        CompletableFuture<Void> decision;
        try (Connection uploadGate = gate(); Connection verifier = gate()) {
            // The submit locks both versions and the old verification, then waits here on the upload.
            try (var hold = uploadGate.prepareStatement("select 1 from tutor_evidence_uploads where id = ? for update")) {
                hold.setObject(1, evidence);
                hold.execute();
            }
            submitted = Concurrently.supply(() -> statusCode(() -> submit(tutor, 2, draft, List.of(evidence))));
            awaitWaiting("select id, owner_id, state", 1);
            // A verifier's late decision now waits on the submit's lock of the old version.
            decision = Concurrently.run(() -> {
                try (var update = verifier.prepareStatement(
                        "update tutor_verifications set status = 'REJECTED', reason = 'Muộn.' where id = ?")) {
                    update.setObject(1, oldVerification);
                    update.executeUpdate();
                    verifier.commit();
                } catch (SQLException e) {
                    throw new IllegalStateException(e);
                }
            });
            awaitWaiting("update tutor_verifications", 1);
            uploadGate.commit();

            assertThat(submitted.get(30, TimeUnit.SECONDS)).isEqualTo(201);
            assertThatThrownBy(() -> decision.get(30, TimeUnit.SECONDS)).rootCause()
                    .hasMessageContaining("the verification of a retired profile version is closed");
            verifier.rollback();
        }

        assertThat(statusOf(needsInfo)).isEqualTo("RETIRED");
        assertThat(verificationRow(oldVerification)).isEqualTo(oldBefore);
    }

    @Test
    void OP_24_a_decision_that_lands_first_is_kept_and_the_submit_then_leaves_that_version_alone()
            throws Exception {
        Actor tutor = newActor(Actor.Role.TUTOR);
        UUID needsInfo = version(tutor, 1, 1, "PENDING", false);
        UUID oldVerification = verification(needsInfo, "PENDING", "Cần thêm bằng cấp.");
        UUID draft = draft(tutor, 2, 2);
        CompletableFuture<Integer> submitted;
        try (Connection verifier = gate()) {
            // A verifier rejects the old version first, locking the version before its verification (INV-16).
            try (var lock = verifier.prepareStatement("select 1 from tutor_profiles where id = ? for update");
                    var version = verifier.prepareStatement(
                            "update tutor_profiles set status = 'REJECTED' where id = ?");
                    var decide = verifier.prepareStatement(
                            "update tutor_verifications set status = 'REJECTED', reason = 'Thiếu bằng cấp.' where id = ?")) {
                lock.setObject(1, needsInfo);
                lock.execute();
                version.setObject(1, needsInfo);
                version.executeUpdate();
                decide.setObject(1, oldVerification);
                decide.executeUpdate();
            }
            submitted = Concurrently.supply(() -> statusCode(() -> submit(tutor, 2, draft, ready(tutor, 1))));
            awaitWaiting("select id from tutor_profiles", 1);
            verifier.commit();
        }

        assertThat(submitted.get(30, TimeUnit.SECONDS)).isEqualTo(201);
        assertThat(statusOf(needsInfo)).isEqualTo("REJECTED");
        assertThat(jdbc.sql("select status from tutor_verifications where id = ?").param(oldVerification)
                .query(String.class).single()).isEqualTo("REJECTED");
        assertThat(auditsOf(tutor)).isZero();
    }

    // ---------------------------------------------------------------- access

    @Test
    void INV_06_another_tutors_version_is_the_same_409_and_nothing_of_theirs_changes() throws Exception {
        Actor tutor = newActor(Actor.Role.TUTOR);
        Actor other = newActor(Actor.Role.TUTOR);
        UUID theirs = draft(other, 1, 1);
        String theirsBefore = profileRow(theirs);
        draft(tutor, 1, 1);

        submit(tutor, 1, theirs, ready(tutor, 1)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TUTOR_PROFILE_NOT_DRAFT"));

        assertThat(profileRow(theirs)).isEqualTo(theirsBefore);
        assertThat(verificationsOf(theirs)).isZero();
    }

    @Test
    void UC03_a_parent_cannot_submit() throws Exception {
        Actor parent = newActor(Actor.Role.PARENT);

        submit(parent, 1, UUID.randomUUID(), List.of(UUID.randomUUID())).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_UNAVAILABLE"));

        assertThat(receiptsOf(parent)).isZero();
    }

    @Test
    void UC03_a_locked_tutor_cannot_submit() throws Exception {
        Actor tutor = newActor(Actor.Role.TUTOR);
        UUID draft = draft(tutor, 1, 1);
        String before = profileRow(draft);
        List<UUID> evidence = ready(tutor, 1);
        jdbc.sql("update accounts set status = 'LOCKED' where id = ?").param(tutor.accountId()).update();

        submit(tutor, 1, draft, evidence).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_UNAVAILABLE"));

        assertNothingWritten(tutor, draft, before);
    }

    @Test
    void without_a_session_submit_is_401() throws Exception {
        mvc.perform(post(PATH).header("Idempotency-Key", newKey()).header("If-Match", etag(1))
                        .contentType(MediaType.APPLICATION_JSON).content(body(UUID.randomUUID(),
                                new ArrayList<>(List.of(UUID.randomUUID())))))
                .andExpect(status().isUnauthorized()).andExpect(Contract.matches());
    }
}
