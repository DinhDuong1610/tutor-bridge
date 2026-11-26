package com.tutorbridge.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.tutorbridge.Contract;
import com.tutorbridge.HttpTest;
import com.tutorbridge.platform.api.Actor;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * UC03 for the tutor (TB-12c PR 2b): getOwnTutorVerification. Real PostgreSQL, every answer checked
 * against the contract. Fake accounts and texts only.
 */
class TutorVerificationHttpTest extends HttpTest {

    private static final String PATH = "/api/v1/me/tutor-profile/verification";
    private static final String DESCRIPTION = "Dạy đại số lớp 8 theo từng bước, có bài tập về nhà mỗi buổi học.";
    private static final OffsetDateTime DECIDED = OffsetDateTime.of(2026, 11, 18, 3, 30, 0, 0, ZoneOffset.UTC);

    // ---------------------------------------------------------------- helpers

    private ResultActions read(Actor actor) throws Exception {
        return mvc.perform(get(PATH).with(sessions().as(actor))).andExpect(Contract.matches());
    }

    private UUID version(Actor tutor, int version, String status, boolean current) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                insert into tutor_profiles(id, tutor_id, version, status, description, experience_months, rate_vnd_60,
                                           current, revision)
                values (?, ?, ?, ?, ?, 12, 180000, ?, ?)
                """).params(id, tutor.accountId(), version, status, DESCRIPTION, current, version).update();
        return id;
    }

    /** A verification as a verifier left it; {@code verifier} null means not assigned yet. */
    private UUID verification(UUID profileId, String status, String reason, Actor verifier) {
        UUID id = UUID.randomUUID();
        UUID verifierId = verifier == null ? null : verifier.accountId();
        jdbc.sql("""
                insert into tutor_verifications(id, profile_id, scope, status, evidence_key, reason, assigned_to,
                                                reviewed_by, reviewed_at)
                values (?, ?, 'MATH8_ALGEBRA', ?, ?, ?, ?, ?, ?)
                """).params(id, profileId, status, "0".repeat(64), reason, verifierId,
                        reason == null ? null : verifierId, reason == null ? null : DECIDED).update();
        return id;
    }

    /** An admin allowed to verify, as the V5 guard requires for a VERIFIED decision. */
    private Actor verifier() {
        Actor admin = newActor(Actor.Role.ADMIN);
        jdbc.sql("insert into admin_assignments(account_id, capability, granted_by) values (?, 'VERIFY', ?)")
                .params(admin.accountId(), admin.accountId()).update();
        return admin;
    }

    private void retire(UUID profileId) {
        jdbc.sql("update tutor_profiles set status = 'RETIRED' where id = ?").param(profileId).update();
    }

    // ---------------------------------------------------------------- the four states

    @Test
    void OP_23_a_version_waiting_for_its_verifier_is_WAITING_with_no_reason_and_no_decision_time() throws Exception {
        Actor tutor = newActor(Actor.Role.TUTOR);
        Actor verifier = verifier();
        UUID version = version(tutor, 1, "PENDING", false);
        UUID id = verification(version, "PENDING", null, verifier);

        read(tutor).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.profileVersionId").value(version.toString()))
                .andExpect(jsonPath("$.profileVersion").value(1))
                .andExpect(jsonPath("$.state").value("WAITING"))
                .andExpect(jsonPath("$.scope").value("MATH8_ALGEBRA"))
                .andExpect(jsonPath("$.reason").isEmpty())
                .andExpect(jsonPath("$.decidedAt").isEmpty())
                .andExpect(jsonPath("$.approvedVersion").isEmpty());
    }

    @Test
    void OP_23_a_pending_verification_with_a_reason_is_NEEDS_INFO_with_the_reason() throws Exception {
        Actor tutor = newActor(Actor.Role.TUTOR);
        UUID version = version(tutor, 1, "PENDING", false);
        verification(version, "PENDING", "Cần thêm bảng điểm năm gần nhất.", verifier());

        read(tutor).andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("NEEDS_INFO"))
                .andExpect(jsonPath("$.reason").value("Cần thêm bảng điểm năm gần nhất."))
                .andExpect(jsonPath("$.decidedAt").value("2026-11-18T03:30:00Z"));
    }

    @Test
    void OP_23_a_rejected_version_is_REJECTED_with_the_reason() throws Exception {
        Actor tutor = newActor(Actor.Role.TUTOR);
        UUID version = version(tutor, 1, "REJECTED", false);
        verification(version, "REJECTED", "Minh chứng không khớp với mô tả.", verifier());

        read(tutor).andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("REJECTED"))
                .andExpect(jsonPath("$.reason").value("Minh chứng không khớp với mô tả."))
                .andExpect(jsonPath("$.decidedAt").value("2026-11-18T03:30:00Z"))
                .andExpect(jsonPath("$.approvedVersion").isEmpty());
    }

    @Test
    void OP_23_a_verified_version_is_VERIFIED_hides_the_verifiers_note_and_is_the_approved_version()
            throws Exception {
        Actor tutor = newActor(Actor.Role.TUTOR);
        UUID version = version(tutor, 1, "APPROVED", true);
        verification(version, "VERIFIED", "Ghi chú nội bộ của người xác minh.", verifier());

        read(tutor).andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("VERIFIED"))
                .andExpect(jsonPath("$.reason").isEmpty())
                .andExpect(jsonPath("$.decidedAt").value("2026-11-18T03:30:00Z"))
                .andExpect(jsonPath("$.profileVersion").value(1))
                .andExpect(jsonPath("$.approvedVersion").value(1));
    }

    @Test
    void INV_06_the_answer_never_names_the_verifier_or_the_evidence() throws Exception {
        Actor tutor = newActor(Actor.Role.TUTOR);
        Actor verifier = verifier();
        UUID version = version(tutor, 1, "REJECTED", false);
        verification(version, "REJECTED", "Minh chứng không khớp với mô tả.", verifier);

        String body = read(tutor).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain(verifier.accountId().toString()).doesNotContain("0".repeat(64));
    }

    // ---------------------------------------------------------------- which verification

    @Test
    void OP_24_after_a_resubmission_the_new_versions_verification_is_read_not_the_retired_one() throws Exception {
        Actor tutor = newActor(Actor.Role.TUTOR);
        Actor verifier = verifier();
        UUID old = version(tutor, 1, "PENDING", false);
        verification(old, "PENDING", "Cần thêm bảng điểm năm gần nhất.", verifier);
        retire(old);
        UUID resubmitted = version(tutor, 2, "PENDING", false);
        UUID id = verification(resubmitted, "PENDING", null, null);

        read(tutor).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.profileVersion").value(2))
                .andExpect(jsonPath("$.state").value("WAITING"))
                .andExpect(jsonPath("$.reason").isEmpty());
    }

    @Test
    void a_newer_draft_does_not_hide_the_last_submitted_versions_verification() throws Exception {
        Actor tutor = newActor(Actor.Role.TUTOR);
        UUID submitted = version(tutor, 1, "REJECTED", false);
        UUID id = verification(submitted, "REJECTED", "Minh chứng không khớp với mô tả.", verifier());
        version(tutor, 2, "DRAFT", false);

        read(tutor).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.profileVersion").value(1))
                .andExpect(jsonPath("$.state").value("REJECTED"));
    }

    @Test
    void OP_24_an_edit_waiting_while_the_older_version_stays_public_reports_that_approved_version()
            throws Exception {
        Actor tutor = newActor(Actor.Role.TUTOR);
        Actor verifier = verifier();
        UUID approved = version(tutor, 1, "APPROVED", true);
        verification(approved, "VERIFIED", "Đủ minh chứng.", verifier);
        UUID edit = version(tutor, 2, "PENDING", false);
        verification(edit, "PENDING", null, null);

        read(tutor).andExpect(status().isOk())
                .andExpect(jsonPath("$.profileVersion").value(2))
                .andExpect(jsonPath("$.state").value("WAITING"))
                .andExpect(jsonPath("$.approvedVersion").value(1));
    }

    @Test
    void OP_24_a_refused_edit_still_reports_the_approved_version_parents_see() throws Exception {
        Actor tutor = newActor(Actor.Role.TUTOR);
        Actor verifier = verifier();
        UUID approved = version(tutor, 1, "APPROVED", true);
        verification(approved, "VERIFIED", "Đủ minh chứng.", verifier);
        UUID edit = version(tutor, 2, "REJECTED", false);
        verification(edit, "REJECTED", "Mô tả mới không khớp minh chứng.", verifier);

        read(tutor).andExpect(status().isOk())
                .andExpect(jsonPath("$.profileVersion").value(2))
                .andExpect(jsonPath("$.state").value("REJECTED"))
                .andExpect(jsonPath("$.approvedVersion").value(1));
    }

    // ---------------------------------------------------------------- not submitted

    @Test
    void a_tutor_with_no_profile_gets_404() throws Exception {
        Actor tutor = newActor(Actor.Role.TUTOR);

        read(tutor).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TUTOR_VERIFICATION_NOT_FOUND"));
    }

    @Test
    void a_tutor_with_only_a_draft_gets_404() throws Exception {
        Actor tutor = newActor(Actor.Role.TUTOR);
        version(tutor, 1, "DRAFT", false);

        read(tutor).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TUTOR_VERIFICATION_NOT_FOUND"));
    }

    // ---------------------------------------------------------------- access

    @Test
    void INV_06_another_tutors_verification_is_never_read() throws Exception {
        Actor tutor = newActor(Actor.Role.TUTOR);
        Actor other = newActor(Actor.Role.TUTOR);
        UUID theirs = version(other, 1, "PENDING", false);
        verification(theirs, "PENDING", "Cần thêm bảng điểm năm gần nhất.", verifier());
        version(tutor, 1, "DRAFT", false);

        read(tutor).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TUTOR_VERIFICATION_NOT_FOUND"));
    }

    @Test
    void UC03_a_parent_cannot_read_a_tutor_verification() throws Exception {
        Actor parent = newActor(Actor.Role.PARENT);

        read(parent).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_UNAVAILABLE"));
    }

    @Test
    void UC03_an_admin_cannot_read_through_the_tutors_own_endpoint() throws Exception {
        read(verifier()).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_UNAVAILABLE"));
    }

    @Test
    void without_a_session_the_verification_is_401() throws Exception {
        mvc.perform(get(PATH)).andExpect(status().isUnauthorized()).andExpect(Contract.matches());
    }

    // ---------------------------------------------------------------- through the real submit

    @Test
    void UC03_right_after_submitting_through_the_api_the_tutor_reads_WAITING() throws Exception {
        Actor tutor = newActor(Actor.Role.TUTOR);
        UUID draft = version(tutor, 1, "DRAFT", false);
        UUID upload = UUID.randomUUID();
        jdbc.sql("""
                insert into tutor_evidence_uploads(id, owner_id, object_key, media_type, size_bytes, sha256, state,
                                                   expires_at)
                values (?, ?, ?, 'application/pdf', 100, ?, 'READY', now() + interval '15 minutes')
                """).params(upload, tutor.accountId(), UUID.randomUUID().toString(), "a".repeat(64)).update();
        String submitted = mvc.perform(post("/api/v1/me/tutor-profile/submit").with(sessions().as(tutor))
                        .header("Idempotency-Key", UUID.randomUUID().toString()).header("If-Match", "\"1\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"profileVersionId":"%s","evidenceUploadIds":["%s"]}
                                """.formatted(draft, upload)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String verificationId = JsonPath.read(submitted, "$.id");

        read(tutor).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(verificationId))
                .andExpect(jsonPath("$.profileVersionId").value(draft.toString()))
                .andExpect(jsonPath("$.state").value("WAITING"))
                .andExpect(jsonPath("$.approvedVersion").isEmpty());
    }
}
