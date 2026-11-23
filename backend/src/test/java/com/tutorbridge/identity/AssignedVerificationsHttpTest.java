package com.tutorbridge.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.tutorbridge.Contract;
import com.tutorbridge.HttpTest;
import com.tutorbridge.platform.api.Actor;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * listAssignedVerifications (AD03) and getAssignedVerification (AD04) of TB-13, as OP-28 shaped them on #98:
 * only an ADMIN with VERIFY, only the records assigned to them, oldest submission first; another
 * verifier's record is 404 as if it did not exist (INV-06), and no email or evidence key is ever sent
 * (AC-UC03-03).
 */
class AssignedVerificationsHttpTest extends HttpTest {

    private static final String LIST = "/api/v1/admin/verifications";
    private static final String DESCRIPTION = "Dạy đại số lớp 8 theo từng bước, có bài tập về nhà mỗi buổi học.";
    private static final OffsetDateTime T0 = OffsetDateTime.of(2026, 11, 20, 2, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime DECIDED = OffsetDateTime.of(2026, 11, 21, 3, 30, 0, 0, ZoneOffset.UTC);

    // ---------------------------------------------------------------- helpers

    private ResultActions call(MockHttpServletRequestBuilder request, Actor actor) throws Exception {
        return mvc.perform(request.with(sessions().as(actor))).andExpect(Contract.matches());
    }

    private Actor verifier() {
        Actor admin = newActor(Actor.Role.ADMIN);
        jdbc.sql("insert into admin_assignments(account_id, capability, granted_by) values (?, 'VERIFY', ?)")
                .params(admin.accountId(), admin.accountId()).update();
        return admin;
    }

    private Actor tutor(String displayName) {
        Actor tutor = newActor(Actor.Role.TUTOR);
        jdbc.sql("update accounts set display_name = ? where id = ?").params(displayName, tutor.accountId()).update();
        return tutor;
    }

    private UUID version(Actor tutor, int version, String status) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                insert into tutor_profiles(id, tutor_id, version, status, description, experience_months, rate_vnd_60,
                                           revision)
                values (?, ?, ?, ?, ?, 14, 200000, 2)
                """).params(id, tutor.accountId(), version, status, DESCRIPTION + " Bản " + version).update();
        return id;
    }

    /** A record as its verifier left it; a reason means a decision, NEEDS_INFO included. */
    private UUID verification(UUID profileId, String status, String reason, Actor assignedTo, OffsetDateTime submitted) {
        UUID id = UUID.randomUUID();
        UUID verifier = assignedTo == null ? null : assignedTo.accountId();
        jdbc.sql("""
                insert into tutor_verifications(id, profile_id, scope, status, evidence_key, reason, assigned_to,
                                                reviewed_by, reviewed_at, submitted_at, revision)
                values (?, ?, 'MATH8_ALGEBRA', ?, ?, ?, ?, ?, ?, ?, 3)
                """).params(id, profileId, status, "0".repeat(64), reason, verifier,
                        reason == null ? null : verifier, reason == null ? null : DECIDED, submitted).update();
        return id;
    }

    private UUID pending(Actor tutor, Actor assignedTo, OffsetDateTime submitted) {
        return verification(version(tutor, 1, "PENDING"), "PENDING", null, assignedTo, submitted);
    }

    private static List<String> ids(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.items[*].id");
    }

    // ---------------------------------------------------------------- listAssignedVerifications

    @Test
    void OP_28_the_queue_has_only_the_callers_records_oldest_submission_first() throws Exception {
        Actor me = verifier();
        Actor other = verifier();
        UUID later = pending(tutor("Cô Lan"), me, T0.plusHours(2));
        UUID earlier = pending(tutor("Thầy Minh"), me, T0);
        pending(tutor("Cô Hoa"), other, T0.plusHours(1));
        pending(tutor("Thầy Nam"), null, T0.plusHours(1));

        ResultActions result = call(get(LIST), me).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(jsonPath("$.items[0].tutorDisplayName").value("Thầy Minh"))
                .andExpect(jsonPath("$.items[0].assignedTo").value(me.accountId().toString()))
                .andExpect(jsonPath("$.items[0].submittedAt").value("2026-11-20T02:00:00Z"))
                .andExpect(jsonPath("$.items[0].reason").isEmpty())
                .andExpect(jsonPath("$.items[0].decidedAt").isEmpty())
                .andExpect(jsonPath("$.items[0].superseded").value(false))
                .andExpect(jsonPath("$.items[0].revision").value(3))
                .andExpect(jsonPath("$.nextCursor").isEmpty());
        assertThat(ids(result)).containsExactly(earlier.toString(), later.toString());
    }

    @Test
    void OP_28_PENDING_has_NEEDS_INFO_but_no_superseded_record_and_no_status_lists_everything() throws Exception {
        Actor me = verifier();
        Actor tutor = tutor("Cô Lan");
        UUID waiting = pending(tutor("Thầy Minh"), me, T0);
        UUID needsInfo = verification(version(tutor, 1, "PENDING"), "PENDING", "Thiếu bằng cấp.", me,
                T0.plusMinutes(1));
        // As submitTutorProfile leaves it (OP-24): asked for more, then replaced by a newer version.
        UUID replaced = version(tutor("Cô Hoa"), 1, "PENDING");
        UUID superseded = verification(replaced, "PENDING", "Ảnh mờ.", me, T0.plusMinutes(2));
        jdbc.sql("update tutor_profiles set status = 'RETIRED' where id = ?").param(replaced).update();
        UUID verified = verification(version(tutor("Thầy Nam"), 1, "APPROVED"), "VERIFIED", "Đủ minh chứng.", me,
                T0.plusMinutes(3));
        UUID rejected = verification(version(tutor("Cô Mai"), 1, "REJECTED"), "REJECTED", "Không đúng môn.", me,
                T0.plusMinutes(4));

        assertThat(ids(call(get(LIST).param("status", "PENDING"), me).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[1].reason").value("Thiếu bằng cấp."))
                .andExpect(jsonPath("$.items[1].decidedAt").value("2026-11-21T03:30:00Z"))))
                .containsExactly(waiting.toString(), needsInfo.toString());
        assertThat(ids(call(get(LIST).param("status", "VERIFIED"), me).andExpect(status().isOk())))
                .containsExactly(verified.toString());
        assertThat(ids(call(get(LIST).param("status", "REJECTED"), me).andExpect(status().isOk())))
                .containsExactly(rejected.toString());
        ResultActions all = call(get(LIST), me).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[2].superseded").value(true))
                .andExpect(jsonPath("$.items[2].status").value("PENDING"));
        assertThat(ids(all)).containsExactly(waiting.toString(), needsInfo.toString(), superseded.toString(),
                verified.toString(), rejected.toString());
    }

    @Test
    void the_queue_pages_by_submission_time_and_id_without_skipping_or_repeating_a_record() throws Exception {
        Actor me = verifier();
        // Three records sent in the same microsecond: only the id tells them apart. PostgreSQL orders uuid
        // by its bytes, which is the order of the lower-case text.
        List<String> want = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            want.add(pending(tutor("Gia sư " + i), me, T0).toString());
        }
        want.sort(null);
        want.add(pending(tutor("Gia sư muộn"), me, T0.plusSeconds(1)).toString());

        List<String> seen = new ArrayList<>();
        String cursor = null;
        int pages = 0;
        do {
            MockHttpServletRequestBuilder request = get(LIST).param("limit", "2");
            if (cursor != null) {
                request = request.param("cursor", cursor);
            }
            String body = call(request, me).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            seen.addAll(JsonPath.read(body, "$.items[*].id"));
            cursor = JsonPath.read(body, "$.nextCursor");
            pages++;
        } while (cursor != null);

        assertThat(pages).isEqualTo(2);
        assertThat(seen).containsExactlyElementsOf(want);
    }

    @Test
    void a_wrong_status_cursor_or_limit_is_422() throws Exception {
        Actor me = verifier();
        call(get(LIST).param("status", "NEEDS_INFO"), me).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        call(get(LIST).param("cursor", "khong-phai-vi-tri"), me).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("CURSOR_INVALID"));
        call(get(LIST).param("limit", "51"), me).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("LIMIT_INVALID"));
    }

    // ---------------------------------------------------------------- getAssignedVerification

    @Test
    void AD04_the_detail_has_the_exact_submitted_version_and_its_evidence_but_no_key_hash_or_email() throws Exception {
        Actor me = verifier();
        Actor tutor = tutor("Cô Lan");
        jdbc.sql("update accounts set email_digest = ?, email_ciphertext = ? where id = ?")
                .params("e".repeat(64), new byte[] {1, 2, 3}, tutor.accountId()).update();
        UUID profile = version(tutor, 2, "DRAFT");
        List<UUID> uploads = new ArrayList<>();
        for (String type : List.of("application/pdf", "image/png")) {
            UUID upload = UUID.randomUUID();
            jdbc.sql("""
                    insert into tutor_evidence_uploads(id, owner_id, object_key, media_type, size_bytes, sha256, state,
                                                       expires_at)
                    values (?, ?, ?, ?, 4096, ?, 'READY', now() + interval '15 minutes')
                    """).params(upload, tutor.accountId(), "evidence/khoa-bi-mat-" + upload, type, "a".repeat(64))
                    .update();
            jdbc.sql("insert into tutor_profile_evidence(profile_id, upload_id) values (?, ?)").params(profile, upload)
                    .update();
            uploads.add(upload);
        }
        jdbc.sql("update tutor_profiles set status = 'PENDING' where id = ?").param(profile).update();
        UUID id = verification(profile, "PENDING", null, me, T0);
        // An evidence file of another version of the same tutor is not part of this one.
        UUID otherVersion = version(tutor, 1, "DRAFT");
        UUID otherUpload = UUID.randomUUID();
        jdbc.sql("""
                insert into tutor_evidence_uploads(id, owner_id, object_key, media_type, size_bytes, sha256, state,
                                                   expires_at)
                values (?, ?, ?, 'image/jpeg', 10, ?, 'READY', now() + interval '15 minutes')
                """).params(otherUpload, tutor.accountId(), "evidence/" + otherUpload, "b".repeat(64)).update();
        jdbc.sql("insert into tutor_profile_evidence(profile_id, upload_id) values (?, ?)")
                .params(otherVersion, otherUpload).update();

        String body = call(get(LIST + "/" + id), me).andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"3\""))
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(jsonPath("$.verification.id").value(id.toString()))
                .andExpect(jsonPath("$.verification.profileVersion").value(2))
                .andExpect(jsonPath("$.profile.id").value(profile.toString()))
                .andExpect(jsonPath("$.profile.version").value(2))
                .andExpect(jsonPath("$.profile.subject").value("MATH8_ALGEBRA"))
                .andExpect(jsonPath("$.profile.description").value(DESCRIPTION + " Bản 2"))
                .andExpect(jsonPath("$.profile.experienceMonths").value(14))
                .andExpect(jsonPath("$.profile.rateVnd60").value(200000))
                .andExpect(jsonPath("$.evidence.length()").value(2))
                .andExpect(jsonPath("$.evidence[0].sizeBytes").value(4096))
                .andReturn().getResponse().getContentAsString();
        List<String> evidence = JsonPath.read(body, "$.evidence[*].id");
        assertThat(evidence).containsExactlyInAnyOrderElementsOf(uploads.stream().map(UUID::toString).toList());
        assertThat(body).doesNotContain("khoa-bi-mat", "a".repeat(64), "e".repeat(64), "evidence/");
    }

    @Test
    void INV_06_a_record_of_another_verifier_is_404_exactly_like_one_that_does_not_exist() throws Exception {
        Actor me = verifier();
        Actor other = verifier();
        UUID theirs = pending(tutor("Cô Lan"), other, T0);
        UUID unassigned = pending(tutor("Thầy Minh"), null, T0);

        String missing = call(get(LIST + "/" + UUID.randomUUID()), me).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("VERIFICATION_NOT_FOUND"))
                .andReturn().getResponse().getContentAsString();
        for (UUID id : List.of(theirs, unassigned)) {
            String body = call(get(LIST + "/" + id), me).andExpect(status().isNotFound())
                    .andReturn().getResponse().getContentAsString();
            for (String field : List.of("$.type", "$.title", "$.status", "$.code")) {
                Object got = JsonPath.read(body, field);
                assertThat(got).as(field).isEqualTo(JsonPath.read(missing, field));
            }
        }
        // The verifier it is assigned to reads it.
        call(get(LIST + "/" + theirs), other).andExpect(status().isOk());
    }

    // ---------------------------------------------------------------- who may read

    @Test
    void a_parent_a_tutor_and_an_admin_without_VERIFY_get_403_before_any_record_is_read() throws Exception {
        Actor owner = verifier();
        UUID id = pending(tutor("Cô Lan"), owner, T0);
        Actor revoked = verifier();
        jdbc.sql("update admin_assignments set revoked_at = now() where account_id = ?").param(revoked.accountId())
                .update();
        Actor support = newActor(Actor.Role.ADMIN);
        jdbc.sql("insert into admin_assignments(account_id, capability, granted_by) values (?, 'SUPPORT', ?)")
                .params(support.accountId(), owner.accountId()).update();

        for (Actor caller : List.of(newActor(Actor.Role.PARENT), newActor(Actor.Role.TUTOR), revoked, support)) {
            call(get(LIST), caller).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
            call(get(LIST + "/" + id), caller).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        }
    }

    @Test
    void without_a_session_both_are_401() throws Exception {
        mvc.perform(get(LIST)).andExpect(status().isUnauthorized()).andExpect(Contract.matches());
        mvc.perform(get(LIST + "/" + UUID.randomUUID())).andExpect(status().isUnauthorized())
                .andExpect(Contract.matches());
    }
}
