package com.tutorbridge.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tutorbridge.Contract;
import com.tutorbridge.HttpTest;
import com.tutorbridge.platform.api.Actor;
import com.tutorbridge.platform.api.IdempotencyKey;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * getCommandStatus and findCommandStatus (TX16, FR-37, TB-22). Decisions of Huy, 24/11/2026: a result the
 * actor can no longer see is left out of a 200; a receipt past its expiry is 404; a result type no module
 * answers for, CLASS until TB-20, is left out too.
 */
@ExtendWith(OutputCaptureExtension.class)
class CommandStatusHttpTest extends HttpTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String GOAL = "Nắm chắc phương trình bậc nhất một ẩn trước kỳ kiểm tra giữa kỳ.";
    private static final String DESCRIPTION = "Dạy đại số lớp 8 theo từng bước, có bài tập về nhà mỗi buổi học.";

    @Autowired
    TransactionTemplate tx;

    // ---------------------------------------------------------------- after real commands

    @Test
    void FR_37_after_a_lost_response_the_key_finds_the_command_and_its_id_reads_the_same() throws Exception {
        Actor parent = newActor(Actor.Role.PARENT);
        String key = newKey();
        String studentId = body(mvc.perform(post("/api/v1/students").header(IdempotencyKey.HEADER, key)
                        .with(sessions().as(parent)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Minh\",\"grade\":8,\"shortGoal\":\"\"}"))
                .andExpect(status().isCreated())).get("id").asString();

        JsonNode found = body(find(parent, key, "CREATE_STUDENT").andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"2\""))
                .andExpect(header().string("Cache-Control", "private, no-store")));

        assertThat(found.get("commandType").asString()).isEqualTo("CREATE_STUDENT");
        assertThat(found.get("state").asString()).isEqualTo("SUCCEEDED");
        assertThat(found.get("resultType").asString()).isEqualTo("STUDENT");
        assertThat(found.get("resultId").asString()).isEqualTo(studentId);
        assertThat(found.get("errorCode").isNull()).isTrue();
        assertThat(body(read(parent, UUID.fromString(found.get("operationId").asString()))
                .andExpect(status().isOk()))).isEqualTo(found);
    }

    @Test
    void FR_37_a_need_draft_of_the_classes_module_is_found_with_its_result() throws Exception {
        Actor parent = newActor(Actor.Role.PARENT);
        UUID student = studentOf(parent);
        String key = newKey();
        String needId = body(mvc.perform(post("/api/v1/students/" + student + "/needs")
                        .header(IdempotencyKey.HEADER, key).with(sessions().as(parent))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"goal\":\"" + GOAL + "\"}"))
                .andExpect(status().isCreated())).get("id").asString();

        JsonNode found = body(find(parent, key, "CREATE_NEED_DRAFT").andExpect(status().isOk()));

        assertThat(found.get("resultType").asString()).isEqualTo("NEED");
        assertThat(found.get("resultId").asString()).isEqualTo(needId);
    }

    // ---------------------------------------------------------------- each result type, for its owner

    @Test
    void INV_22_each_result_type_is_shown_to_the_owner_who_can_still_read_it() throws Exception {
        Actor parent = newActor(Actor.Role.PARENT);
        UUID student = studentOf(parent);
        UUID studentCalendar = calendar("STUDENT", student);
        UUID need = need(student, studentCalendar);
        UUID run = run(need);
        Actor tutor = newActor(Actor.Role.TUTOR);
        UUID profile = profile(tutor);

        assertShown(parent, "CREATE_STUDENT", "STUDENT", student);
        assertShown(parent, "CREATE_NEED_DRAFT", "NEED", need);
        assertShown(parent, "CREATE_MATCHING_RUN", "MATCHING_RUN", run);
        assertShown(parent, "REPLACE_STUDENT_CALENDAR", "CALENDAR", studentCalendar);
        assertShown(tutor, "REPLACE_TUTOR_CALENDAR", "CALENDAR", calendar("TUTOR", tutor.accountId()));
        assertShown(tutor, "SAVE_TUTOR_PROFILE", "TUTOR_PROFILE", profile);
        assertShown(tutor, "SUBMIT_TUTOR_PROFILE", "TUTOR_VERIFICATION", verification(profile));
        assertShown(tutor, "CREATE_EVIDENCE_UPLOAD", "EVIDENCE_UPLOAD", upload(tutor));
    }

    @Test
    void INV_22_after_the_parent_loses_the_child_the_status_stays_but_the_result_is_left_out() throws Exception {
        Actor parent = newActor(Actor.Role.PARENT);
        UUID student = studentOf(parent);
        UUID studentCalendar = calendar("STUDENT", student);
        UUID need = need(student, studentCalendar);
        UUID run = run(need);
        UUID[] receipts = {
                receipt(parent, "CREATE_STUDENT", "SUCCEEDED", "STUDENT", student, null),
                receipt(parent, "CREATE_NEED_DRAFT", "SUCCEEDED", "NEED", need, null),
                receipt(parent, "CREATE_MATCHING_RUN", "SUCCEEDED", "MATCHING_RUN", run, null),
                receipt(parent, "REPLACE_STUDENT_CALENDAR", "SUCCEEDED", "CALENDAR", studentCalendar, null)};
        for (UUID receipt : receipts) {
            assertThat(body(read(parent, receipt).andExpect(status().isOk())).get("resultId").isNull()).isFalse();
        }

        Actor other = newActor(Actor.Role.PARENT);
        jdbc.sql("update students set parent_id = ? where id = ?").params(other.accountId(), student).update();

        for (UUID receipt : receipts) {
            assertHidden(body(read(parent, receipt).andExpect(status().isOk())), "SUCCEEDED");
        }
    }

    @Test
    void INV_22_a_status_whose_result_is_now_left_out_has_another_etag_so_no_304_keeps_the_old_one()
            throws Exception {
        Actor parent = newActor(Actor.Role.PARENT);
        UUID student = studentOf(parent);
        String key = newKey();
        UUID receipt = receipt(parent, "CREATE_STUDENT", key, "SUCCEEDED", "STUDENT", student, null);
        read(parent, receipt).andExpect(status().isOk()).andExpect(header().string("ETag", "\"2\""));

        Actor other = newActor(Actor.Role.PARENT);
        jdbc.sql("update students set parent_id = ? where id = ?").params(other.accountId(), student).update();

        // By id and by key: a full answer without the result, not a 304 for the "2" that named it.
        JsonNode byId = body(mvc.perform(get("/api/v1/operations/" + receipt).header("If-None-Match", "\"2\"")
                        .with(sessions().as(parent))).andExpect(Contract.matches())
                .andExpect(status().isOk()).andExpect(header().string("ETag", "\"3\"")));
        assertHidden(byId, "SUCCEEDED");
        JsonNode byKey = body(mvc.perform(get("/api/v1/operations").param("idempotencyKey", key)
                        .param("commandType", "CREATE_STUDENT").header("If-None-Match", "\"2\"")
                        .with(sessions().as(parent))).andExpect(Contract.matches())
                .andExpect(status().isOk()).andExpect(header().string("ETag", "\"3\"")));
        assertHidden(byKey, "SUCCEEDED");

        // The child back with the parent: the "2" answer again.
        jdbc.sql("update students set parent_id = ? where id = ?").params(parent.accountId(), student).update();
        read(parent, receipt).andExpect(status().isOk()).andExpect(header().string("ETag", "\"2\""))
                .andExpect(jsonPath("$.resultId").value(student.toString()));
    }

    @Test
    void INV_22_a_tutor_never_sees_a_result_of_a_parent_kind_even_under_its_own_receipt() throws Exception {
        Actor tutor = newActor(Actor.Role.TUTOR);
        Actor parent = newActor(Actor.Role.PARENT);
        UUID student = studentOf(parent);
        UUID receipt = receipt(tutor, "CREATE_STUDENT", "SUCCEEDED", "STUDENT", student, null);
        UUID calendarReceipt = receipt(tutor, "REPLACE_STUDENT_CALENDAR", "SUCCEEDED", "CALENDAR",
                calendar("STUDENT", student), null);

        assertHidden(body(read(tutor, receipt).andExpect(status().isOk())), "SUCCEEDED");
        assertHidden(body(read(tutor, calendarReceipt).andExpect(status().isOk())), "SUCCEEDED");
    }

    @Test
    void INV_22_an_offer_is_shown_as_getOffer_reads_it_to_its_tutor_and_the_parent_of_its_child()
            throws Exception {
        // TB-18 commands keep OFFER as their result: sendOffer (parent), rejectOffer (tutor), cancelOffer (parent).
        com.tutorbridge.classes.OfferFixture fixture = new com.tutorbridge.classes.OfferFixture(jdbc, tx);
        com.tutorbridge.classes.OfferFixture.Student child = fixture.student();
        com.tutorbridge.classes.OfferFixture.Tutor to = fixture.tutor();
        UUID offer = fixture.offer(fixture.need(child), to);
        Actor parent = new Actor(child.parentId(), Actor.Role.PARENT, UUID.randomUUID());
        Actor tutor = new Actor(to.id(), Actor.Role.TUTOR, UUID.randomUUID());

        assertShown(parent, "SEND_OFFER", "OFFER", offer);
        assertShown(parent, "CANCEL_OFFER", "OFFER", offer);
        assertShown(tutor, "REJECT_OFFER", "OFFER", offer);

        // Another tutor or another family under their own receipt: the status, never the result.
        Actor otherTutor = newActor(Actor.Role.TUTOR);
        Actor otherParent = newActor(Actor.Role.PARENT);
        assertHidden(body(read(otherTutor, receipt(otherTutor, "REJECT_OFFER", "SUCCEEDED", "OFFER", offer, null))
                .andExpect(status().isOk())), "SUCCEEDED");
        assertHidden(body(read(otherParent, receipt(otherParent, "SEND_OFFER", "SUCCEEDED", "OFFER", offer, null))
                .andExpect(status().isOk())), "SUCCEEDED");

        // The parent who loses the child loses the result too (INV-22).
        jdbc.sql("update students set parent_id = ? where id = ?").params(otherParent.accountId(), child.id()).update();
        assertHidden(body(read(parent, receipt(parent, "SEND_OFFER", "SUCCEEDED", "OFFER", offer, null))
                .andExpect(status().isOk())), "SUCCEEDED");
    }

    @Test
    void INV_22_a_decided_verification_is_shown_to_its_verifier_while_they_may_still_verify() throws Exception {
        // decideVerification (TB-13) keeps TUTOR_VERIFICATION as its result, with the ADMIN as the actor.
        Actor verifier = newActor(Actor.Role.ADMIN);
        jdbc.sql("insert into admin_assignments(account_id, capability, granted_by) values (?, 'VERIFY', ?)")
                .params(verifier.accountId(), verifier.accountId()).update();
        UUID verification = UUID.randomUUID();
        jdbc.sql("""
                insert into tutor_verifications(id, profile_id, scope, status, evidence_key, assigned_to, reviewed_by,
                                                reviewed_at)
                values (?, ?, 'MATH8_ALGEBRA', 'VERIFIED', ?, ?, ?, now())
                """).params(verification, profile(newActor(Actor.Role.TUTOR)), "0".repeat(64), verifier.accountId(),
                        verifier.accountId()).update();

        assertShown(verifier, "DECIDE_VERIFICATION", "TUTOR_VERIFICATION", verification);

        // Another verifier, to whom it is not assigned: the status, not the result.
        Actor other = newActor(Actor.Role.ADMIN);
        jdbc.sql("insert into admin_assignments(account_id, capability, granted_by) values (?, 'VERIFY', ?)")
                .params(other.accountId(), other.accountId()).update();
        assertHidden(body(read(other, receipt(other, "DECIDE_VERIFICATION", "SUCCEEDED", "TUTOR_VERIFICATION",
                verification, null)).andExpect(status().isOk())), "SUCCEEDED");

        // The verifier whose VERIFY is revoked no longer reads it (INV-22).
        jdbc.sql("update admin_assignments set revoked_at = now() where account_id = ?").param(verifier.accountId())
                .update();
        assertHidden(body(read(verifier, receipt(verifier, "DECIDE_VERIFICATION", "SUCCEEDED", "TUTOR_VERIFICATION",
                verification, null)).andExpect(status().isOk())), "SUCCEEDED");
    }

    @Test
    void a_class_until_TB_20_an_unknown_type_and_a_result_that_is_gone_are_left_out() throws Exception {
        Actor tutor = newActor(Actor.Role.TUTOR);

        for (UUID receipt : new UUID[] {
                receipt(tutor, "ACCEPT_OFFER", "SUCCEEDED", "CLASS", UUID.randomUUID(), null),
                receipt(tutor, "SOMETHING_ELSE", "SUCCEEDED", "SOMETHING", UUID.randomUUID(), null),
                receipt(tutor, "SAVE_TUTOR_PROFILE", "SUCCEEDED", "TUTOR_PROFILE", UUID.randomUUID(), null)}) {
            assertHidden(body(read(tutor, receipt).andExpect(status().isOk())), "SUCCEEDED");
        }
    }

    @Test
    void a_failed_command_answers_its_error_and_a_processing_one_its_first_etag() throws Exception {
        Actor parent = newActor(Actor.Role.PARENT);
        UUID failed = receipt(parent, "CREATE_MATCHING_RUN", "FAILED", null, null, "AI_RANKING_UNAVAILABLE");
        UUID processing = receipt(parent, "CREATE_STUDENT", "PROCESSING", null, null, null);

        JsonNode failure = body(read(parent, failed).andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"2\"")));
        assertHidden(failure, "FAILED");
        assertThat(failure.get("errorCode").asString()).isEqualTo("AI_RANKING_UNAVAILABLE");
        assertHidden(body(read(parent, processing).andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"1\""))), "PROCESSING");
    }

    // ---------------------------------------------------------------- not found, input, session

    @Test
    void INV_06_a_receipt_of_someone_else_a_missing_one_and_an_expired_one_get_the_same_404() throws Exception {
        Actor parent = newActor(Actor.Role.PARENT);
        Actor stranger = newActor(Actor.Role.PARENT);
        String key = newKey();
        UUID theirs = receipt(parent, "CREATE_STUDENT", key, "SUCCEEDED", "STUDENT", studentOf(parent), null);
        String expiredKey = newKey();
        UUID expired = receipt(stranger, "CREATE_STUDENT", expiredKey, "SUCCEEDED", "STUDENT", studentOf(stranger),
                null);
        jdbc.sql("update command_receipts set created_at = now() - interval '8 days', "
                + "expires_at = now() - interval '1 day' where id = ?").param(expired).update();

        JsonNode missing = problem(read(stranger, UUID.randomUUID()));
        for (ResultActions answer : new ResultActions[] {
                read(stranger, theirs), find(stranger, key, "CREATE_STUDENT"),
                read(stranger, expired), find(stranger, expiredKey, "CREATE_STUDENT"),
                find(parent, key, "CREATE_NEED_DRAFT"), find(stranger, newKey(), "CREATE_STUDENT")}) {
            assertThat(problem(answer)).isEqualTo(missing);
        }
        assertThat(missing.get("code").asString()).isEqualTo("OPERATION_NOT_FOUND");
        read(parent, theirs).andExpect(status().isOk());
    }

    @Test
    void a_short_or_long_key_is_422_a_missing_type_or_a_malformed_id_is_rejected_and_no_session_is_401() throws Exception {
        Actor parent = newActor(Actor.Role.PARENT);

        find(parent, "short", "CREATE_STUDENT").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        find(parent, "k".repeat(129), "CREATE_STUDENT").andExpect(status().isUnprocessableContent());
        mvc.perform(get("/api/v1/operations").param("idempotencyKey", newKey()).with(sessions().as(parent)))
                .andExpect(status().isBadRequest()).andExpect(Contract.matches());
        mvc.perform(get("/api/v1/operations/not-a-uuid").with(sessions().as(parent)))
                .andExpect(status().isBadRequest()).andExpect(Contract.matches());
        mvc.perform(get("/api/v1/operations/" + UUID.randomUUID())).andExpect(status().isUnauthorized())
                .andExpect(Contract.matches());
        mvc.perform(get("/api/v1/operations").param("idempotencyKey", newKey()).param("commandType", "CREATE_STUDENT"))
                .andExpect(status().isUnauthorized()).andExpect(Contract.matches());
    }

    @Test
    void the_key_sent_in_the_query_never_reaches_the_log(CapturedOutput output) throws Exception {
        Actor parent = newActor(Actor.Role.PARENT);
        String key = newKey();
        receipt(parent, "CREATE_STUDENT", key, "SUCCEEDED", "STUDENT", studentOf(parent), null);

        find(parent, key, "CREATE_STUDENT").andExpect(status().isOk());
        find(parent, newKey() + "-missing", "CREATE_STUDENT").andExpect(status().isNotFound());
        find(parent, "short-" + key.substring(0, 4), "CREATE_STUDENT").andExpect(status().isUnprocessableContent());

        assertThat(output.getAll()).doesNotContain(key).doesNotContain("short-" + key.substring(0, 4));
    }

    // ---------------------------------------------------------------- helpers

    private void assertShown(Actor actor, String command, String type, UUID result) throws Exception {
        JsonNode status = body(read(actor, receipt(actor, command, "SUCCEEDED", type, result, null))
                .andExpect(status().isOk()));
        assertThat(status.get("resultType").asString()).as(type).isEqualTo(type);
        assertThat(status.get("resultId").asString()).as(type).isEqualTo(result.toString());
    }

    private static void assertHidden(JsonNode status, String state) {
        assertThat(status.get("state").asString()).isEqualTo(state);
        assertThat(status.get("resultType").isNull()).isTrue();
        assertThat(status.get("resultId").isNull()).isTrue();
    }

    private ResultActions read(Actor actor, UUID operationId) throws Exception {
        return mvc.perform(get("/api/v1/operations/" + operationId).with(sessions().as(actor)))
                .andExpect(Contract.matches());
    }

    private ResultActions find(Actor actor, String key, String commandType) throws Exception {
        return mvc.perform(get("/api/v1/operations").param("idempotencyKey", key).param("commandType", commandType)
                .with(sessions().as(actor))).andExpect(Contract.matches());
    }

    private static JsonNode body(ResultActions result) throws Exception {
        return JSON.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private static JsonNode problem(ResultActions result) throws Exception {
        JsonNode problem = body(result.andExpect(status().isNotFound()));
        return ((tools.jackson.databind.node.ObjectNode) problem).without("traceId");
    }

    private static String newKey() {
        return UUID.randomUUID().toString();
    }

    private UUID receipt(Actor actor, String command, String state, String type, UUID result, String error) {
        return receipt(actor, command, newKey(), state, type, result, error);
    }

    private UUID receipt(Actor actor, String command, String key, String state, String type, UUID result,
            String error) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                insert into command_receipts(id, actor_id, command_type, idempotency_key, payload_sha256, state,
                                             result_type, result_id, error_code, expires_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, now() + interval '7 days')
                """).params(id, actor.accountId(), command, key, "0".repeat(64), state, type, result, error).update();
        return id;
    }

    private UUID studentOf(Actor parent) {
        UUID id = UUID.randomUUID();
        jdbc.sql("insert into students(id, parent_id, display_name) values (?, ?, 'Hoc sinh thu')")
                .params(id, parent.accountId()).update();
        return id;
    }

    private UUID calendar(String ownerKind, UUID ownerId) {
        UUID id = UUID.randomUUID();
        jdbc.sql("insert into calendars(id, owner_kind, owner_id, version, effective) values (?, ?, ?, 1, true)")
                .params(id, ownerKind, ownerId).update();
        return id;
    }

    /** A CONFIRMED need with one version, as confirmNeed leaves it. */
    private UUID need(UUID student, UUID calendar) {
        return tx.execute(s -> {
            UUID need = UUID.randomUUID();
            UUID version = UUID.randomUUID();
            jdbc.sql("insert into learning_needs(id, student_id, status, revision) values (?, ?, 'CONFIRMED', 2)")
                    .params(need, student).update();
            jdbc.sql("""
                    insert into need_versions(id, need_id, version, goal, budget_vnd_session, duration_minutes,
                                              frequency, start_date, confirmed_at, student_calendar_id)
                    values (?, ?, 1, ?, 200000, 90, 2, ?, now(), ?)
                    """).params(version, need, GOAL, LocalDate.now().plusDays(30), calendar).update();
            jdbc.sql("update learning_needs set current_version_id = ? where id = ?").params(version, need).update();
            return need;
        });
    }

    private UUID run(UUID need) {
        UUID run = UUID.randomUUID();
        jdbc.sql("""
                insert into matching_runs(id, need_version_id, mode, algorithm_version, config_version, status,
                                          failure_code, expires_at)
                select ?, current_version_id, 'S', 'S-v1', 'none', 'FAILED', 'AI_RANKING_UNAVAILABLE',
                       now() + interval '24 hours'
                from learning_needs where id = ?
                """).params(run, need).update();
        return run;
    }

    private UUID profile(Actor tutor) {
        UUID profile = UUID.randomUUID();
        jdbc.sql("""
                insert into tutor_profiles(id, tutor_id, version, status, description, experience_months, rate_vnd_60,
                                           current)
                values (?, ?, 1, 'APPROVED', ?, 24, 200000, true)
                """).params(profile, tutor.accountId(), DESCRIPTION).update();
        return profile;
    }

    private UUID verification(UUID profile) {
        Actor admin = newActor(Actor.Role.ADMIN);
        jdbc.sql("insert into admin_assignments(account_id, capability, granted_by) values (?, 'VERIFY', ?)")
                .params(admin.accountId(), admin.accountId()).update();
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                insert into tutor_verifications(id, profile_id, scope, status, evidence_key, assigned_to, reviewed_by,
                                                reviewed_at)
                values (?, ?, 'MATH8_ALGEBRA', 'VERIFIED', ?, ?, ?, now())
                """).params(id, profile, "0".repeat(64), admin.accountId(), admin.accountId()).update();
        return id;
    }

    private UUID upload(Actor tutor) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                insert into tutor_evidence_uploads(id, owner_id, object_key, media_type, size_bytes, sha256, state,
                                                   expires_at)
                values (?, ?, ?, 'application/pdf', 10, ?, 'UPLOADING', now() + interval '15 minutes')
                """).params(id, tutor.accountId(), UUID.randomUUID().toString(), "0".repeat(64)).update();
        return id;
    }
}
