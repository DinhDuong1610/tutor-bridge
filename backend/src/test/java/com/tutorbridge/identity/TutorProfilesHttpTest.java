package com.tutorbridge.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
 * UC03 for the tutor (TB-12a): saveTutorProfile, getOwnTutorProfile. Real PostgreSQL, every answer
 * checked against the contract. Fake accounts and texts only.
 */
class TutorProfilesHttpTest extends HttpTest {

    private static final String PATH = "/api/v1/me/tutor-profile";
    private static final String DESCRIPTION = "Dạy đại số lớp 8 theo từng bước, có bài tập về nhà mỗi buổi học.";
    private static final String OTHER_DESCRIPTION = "Ôn phương trình bậc nhất qua ví dụ gần gũi, kiểm tra ngắn cuối buổi.";

    private static String body(String description, Object experienceMonths, Object rateVnd60, String subject) {
        return """
                {"description":"%s","experienceMonths":%s,"rateVnd60":%s,"subject":"%s"}
                """.formatted(description, experienceMonths, rateVnd60, subject);
    }

    private static String body(String description, int experienceMonths, int rateVnd60) {
        return body(description, experienceMonths, rateVnd60, "MATH8_ALGEBRA");
    }

    private static final String VALID = body(DESCRIPTION, 24, 200_000);

    private ResultActions save(Actor actor, String key, String ifMatch, String ifNoneMatch, String json)
            throws Exception {
        MockHttpServletRequestBuilder request = put(PATH).with(sessions().as(actor))
                .contentType(MediaType.APPLICATION_JSON).content(json);
        if (key != null) {
            request.header("Idempotency-Key", key);
        }
        if (ifMatch != null) {
            request.header("If-Match", ifMatch);
        }
        if (ifNoneMatch != null) {
            request.header("If-None-Match", ifNoneMatch);
        }
        return mvc.perform(request).andExpect(Contract.matches());
    }

    private ResultActions create(Actor actor, String json) throws Exception {
        return save(actor, newKey(), null, "*", json);
    }

    private ResultActions update(Actor actor, long revision, String json) throws Exception {
        return save(actor, newKey(), "\"" + revision + "\"", null, json);
    }

    private ResultActions read(Actor actor) throws Exception {
        return mvc.perform(get(PATH).with(sessions().as(actor))).andExpect(Contract.matches());
    }

    private static String newKey() {
        return UUID.randomUUID().toString();
    }

    private int versions(Actor tutor) {
        return jdbc.sql("select count(*) from tutor_profiles where tutor_id = ?").param(tutor.accountId())
                .query(Integer.class).single();
    }

    /** A whole stored row, to prove an older version did not change at all. */
    private String row(UUID id) {
        return jdbc.sql("""
                select concat_ws('|', version, status, revision, description, experience_months, rate_vnd_60, subject,
                                 current)
                from tutor_profiles where id = ?
                """).param(id).query(String.class).single();
    }

    /** A version already sent for verification, as TB-12c and the verifier will leave it. */
    private UUID storedVersion(Actor tutor, int version, long revision, String status, boolean current) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                insert into tutor_profiles(id, tutor_id, version, status, description, experience_months, rate_vnd_60,
                                           current, revision)
                values (?, ?, ?, ?, ?, 12, 180000, ?, ?)
                """).params(id, tutor.accountId(), version, status, DESCRIPTION, current, revision).update();
        return id;
    }

    private void verification(UUID profileId, String status, String reason) {
        jdbc.sql("""
                insert into tutor_verifications(id, profile_id, scope, status, evidence_key, reason)
                values (?, ?, 'MATH8_ALGEBRA', ?, ?, ?)
                """).params(UUID.randomUUID(), profileId, status, "0".repeat(64), reason).update();
    }

    // ---------------------------------------------------------------- create, read, edit the draft

    @Test
    void UC03_the_first_save_creates_a_draft_owned_by_the_session_and_it_reads_back() throws Exception {
        Actor tutor = newActor(Actor.Role.TUTOR);

        create(tutor, VALID)
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"1\""))
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.revision").value(1))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.description").value(DESCRIPTION))
                .andExpect(jsonPath("$.experienceMonths").value(24))
                .andExpect(jsonPath("$.rateVnd60").value(200_000));

        read(tutor)
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"1\""))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.description").value(DESCRIPTION));
        assertThat(jdbc.sql("select tutor_id from tutor_profiles where tutor_id = ?").param(tutor.accountId())
                .query(UUID.class).single()).isEqualTo(tutor.accountId());
    }

    @Test
    void UC03_editing_the_draft_keeps_one_version_and_raises_the_revision() throws Exception {
        Actor tutor = newActor(Actor.Role.TUTOR);
        String id = create(tutor, VALID).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        update(tutor, 1, body(OTHER_DESCRIPTION, 30, 220_000))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"2\""))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.revision").value(2))
                .andExpect(jsonPath("$.description").value(OTHER_DESCRIPTION))
                .andExpect(jsonPath("$.rateVnd60").value(220_000));

        assertThat(versions(tutor)).isEqualTo(1);
        assertThat(id).contains(jdbc.sql("select id::text from tutor_profiles where tutor_id = ?")
                .param(tutor.accountId()).query(String.class).single());
    }

    @Test
    void UC03_no_profile_yet_is_404() throws Exception {
        Actor tutor = newActor(Actor.Role.TUTOR);
        read(tutor).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TUTOR_PROFILE_NOT_FOUND"));
    }

    // ---------------------------------------------------------------- AC-UC03-01 fields

    @Test
    void AC_UC03_01_a_new_tutor_with_zero_months_of_experience_is_valid() throws Exception {
        Actor tutor = newActor(Actor.Role.TUTOR);
        create(tutor, body(DESCRIPTION, 0, 150_000))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.experienceMonths").value(0));
    }

    @Test
    void AC_UC03_01_a_price_or_an_experience_with_a_fraction_is_refused_not_cut() throws Exception {
        Actor tutor = newActor(Actor.Role.TUTOR);

        // Found by Codex on #167: Jackson used to store 150000.9 as 150000 and -0.5 as 0 (#168).
        create(tutor, body(DESCRIPTION, 12, "150000.9", "MATH8_ALGEBRA"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        create(tutor, body(DESCRIPTION, "-0.5", 150_000, "MATH8_ALGEBRA"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        assertThat(versions(tutor)).isZero();
    }

    @Test
    void AC_UC03_01_negative_or_too_long_experience_and_a_price_not_positive_are_refused() throws Exception {
        Actor tutor = newActor(Actor.Role.TUTOR);

        create(tutor, body(DESCRIPTION, -1, 150_000)).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields[0].path").value("experienceMonths"));
        create(tutor, body(DESCRIPTION, 601, 150_000)).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.fields[0].path").value("experienceMonths"));
        create(tutor, body(DESCRIPTION, 600, 0)).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.fields[0].path").value("rateVnd60"));
        create(tutor, body(DESCRIPTION, 600, -100_000)).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.fields[0].path").value("rateVnd60"));
        assertThat(versions(tutor)).isZero();
    }

    @Test
    void AC_UC03_01_description_length_counts_code_points_and_subject_is_math8_algebra() throws Exception {
        Actor tutor = newActor(Actor.Role.TUTOR);

        create(tutor, body("😀".repeat(49), 0, 150_000)).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.fields[0].path").value("description"));
        create(tutor, body("a".repeat(3001), 0, 150_000)).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.fields[0].path").value("description"));
        create(tutor, body(DESCRIPTION, 0, 150_000, "MATH9_GEOMETRY")).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.fields[0].path").value("subject"));
        assertThat(versions(tutor)).isZero();

        create(tutor, body("😀".repeat(50), 0, 150_000)).andExpect(status().isOk());
        update(tutor, 1, body("a".repeat(3000), 0, 150_000)).andExpect(status().isOk());
    }

    @Test
    void AC_UC03_01_a_missing_field_or_an_owner_in_the_body_is_refused() throws Exception {
        Actor tutor = newActor(Actor.Role.TUTOR);
        Actor other = newActor(Actor.Role.TUTOR);

        create(tutor, """
                {"description":"%s","experienceMonths":12,"subject":"MATH8_ALGEBRA"}
                """.formatted(DESCRIPTION)).andExpect(status().isUnprocessableContent());
        create(tutor, """
                {"description":"%s","experienceMonths":12,"rateVnd60":150000,"subject":"MATH8_ALGEBRA","tutorId":"%s"}
                """.formatted(DESCRIPTION, other.accountId())).andExpect(status().isUnprocessableContent());

        assertThat(versions(tutor) + versions(other)).isZero();
    }

    // ---------------------------------------------------------------- INV-23 versions

    @Test
    void INV_23_a_save_without_a_version_condition_is_428() throws Exception {
        Actor tutor = newActor(Actor.Role.TUTOR);
        save(tutor, newKey(), null, null, VALID).andExpect(status().isPreconditionRequired());
        assertThat(versions(tutor)).isZero();
    }

    @Test
    void INV_23_a_stale_revision_or_a_second_first_save_is_412_and_changes_nothing() throws Exception {
        Actor tutor = newActor(Actor.Role.TUTOR);
        update(tutor, 1, VALID).andExpect(status().isPreconditionFailed());
        create(tutor, VALID).andExpect(status().isOk());
        create(tutor, VALID).andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
        update(tutor, 1, body(OTHER_DESCRIPTION, 30, 220_000)).andExpect(status().isOk());

        update(tutor, 1, VALID).andExpect(status().isPreconditionFailed());

        read(tutor).andExpect(header().string("ETag", "\"2\""))
                .andExpect(jsonPath("$.description").value(OTHER_DESCRIPTION));
        assertThat(versions(tutor)).isEqualTo(1);
    }

    @Test
    void INV_23_two_first_saves_at_once_create_one_draft_and_the_other_is_412() throws Exception {
        Actor tutor = newActor(Actor.Role.TUTOR);
        CompletableFuture<Integer> first;
        CompletableFuture<Integer> second;
        try (Connection gate = DriverManager.getConnection(
                PostgresTestSupport.jdbcUrl(), "tutorbridge_app", PostgresTestSupport.APP_PASSWORD)) {
            gate.setAutoCommit(false);
            // SHARE lets both find no version under FOR UPDATE, then holds both inserts until released.
            try (var hold = gate.prepareStatement("lock table tutor_profiles in share mode")) {
                hold.execute();
            }
            first = Concurrently.supply(() -> statusOf(() -> create(tutor, VALID)));
            second = Concurrently.supply(() -> statusOf(() -> create(tutor, body(OTHER_DESCRIPTION, 1, 1))));
            await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(20)).until(() -> jdbc.sql("""
                    select count(*) from pg_stat_activity
                    where wait_event_type = 'Lock' and query like 'insert into tutor_profiles%'
                    """).query(Integer.class).single() == 2);
            gate.commit();
        }

        assertThat(List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS)))
                .containsExactlyInAnyOrder(200, 412);
        assertThat(versions(tutor)).isEqualTo(1);
        assertThat(jdbc.sql("select count(*) from command_receipts where actor_id = ?").param(tutor.accountId())
                .query(Integer.class).single()).isEqualTo(1);
    }

    private interface Call {
        ResultActions run() throws Exception;
    }

    private static int statusOf(Call call) {
        try {
            return call.run().andReturn().getResponse().getStatus();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    // ---------------------------------------------------------------- INV-20 idempotency

    @Test
    void INV_20_a_save_without_a_key_is_400() throws Exception {
        Actor tutor = newActor(Actor.Role.TUTOR);
        save(tutor, null, null, "*", VALID).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));
        assertThat(versions(tutor)).isZero();
    }

    @Test
    void INV_20_the_same_key_and_body_replays_one_draft_and_another_body_is_409() throws Exception {
        Actor tutor = newActor(Actor.Role.TUTOR);
        String key = newKey();

        String first = save(tutor, key, null, "*", VALID).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String again = save(tutor, key, null, "*", VALID).andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"1\""))
                .andReturn().getResponse().getContentAsString();
        save(tutor, key, null, "*", body(OTHER_DESCRIPTION, 1, 1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));

        assertThat(again).isEqualTo(first);
        assertThat(versions(tutor)).isEqualTo(1);
    }

    // ---------------------------------------------------------------- INV-54, OP-24: versions sent for verification

    @Test
    void INV_54_editing_an_approved_profile_makes_a_new_draft_and_the_approved_one_stays_as_it_was() throws Exception {
        Actor tutor = newActor(Actor.Role.TUTOR);
        UUID approved = storedVersion(tutor, 1, 3, "APPROVED", true);
        String before = row(approved);

        read(tutor).andExpect(header().string("ETag", "\"3\"")).andExpect(jsonPath("$.status").value("APPROVED"));
        update(tutor, 3, body(OTHER_DESCRIPTION, 30, 250_000))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"4\""))
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.revision").value(4))
                .andExpect(jsonPath("$.status").value("DRAFT"));

        assertThat(row(approved)).isEqualTo(before);
        read(tutor).andExpect(jsonPath("$.version").value(2)).andExpect(jsonPath("$.status").value("DRAFT"));
        // The revision runs on across versions, so the ETag of the approved version is now stale.
        update(tutor, 3, VALID).andExpect(status().isPreconditionFailed());
    }

    /**
     * Two devices holding the ETag of the approved version save at once. The second waits on FOR UPDATE of
     * version 1 and still reads version 1 after the first commits (version 2 is not in its snapshot), so the
     * revision check passes; the unique (tutor_id, version) key is what turns it into 412 (Đính's review of #167).
     */
    @Test
    void INV_23_two_edits_of_an_approved_profile_at_once_create_one_new_version_and_the_other_is_412()
            throws Exception {
        Actor tutor = newActor(Actor.Role.TUTOR);
        UUID approved = storedVersion(tutor, 1, 3, "APPROVED", true);
        String before = row(approved);
        CompletableFuture<Integer> first;
        CompletableFuture<Integer> second;
        try (Connection gate = DriverManager.getConnection(
                PostgresTestSupport.jdbcUrl(), "tutorbridge_app", PostgresTestSupport.APP_PASSWORD)) {
            gate.setAutoCommit(false);
            try (var hold = gate.prepareStatement("select id from tutor_profiles where id = ? for update")) {
                hold.setObject(1, approved);
                hold.execute();
            }
            first = Concurrently.supply(() -> statusOf(() -> update(tutor, 3, VALID)));
            second = Concurrently.supply(() -> statusOf(() -> update(tutor, 3, body(OTHER_DESCRIPTION, 1, 1))));
            await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(20)).until(() -> jdbc.sql("""
                    select count(*) from pg_stat_activity
                    where wait_event_type = 'Lock' and query like '%order by version desc limit 1 for update'
                    """).query(Integer.class).single() == 2);
            gate.commit();
        }

        assertThat(List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS)))
                .containsExactlyInAnyOrder(200, 412);
        assertThat(versions(tutor)).isEqualTo(2);
        assertThat(row(approved)).isEqualTo(before);
        assertThat(jdbc.sql("select count(*) from command_receipts where actor_id = ?").param(tutor.accountId())
                .query(Integer.class).single()).isEqualTo(1);
        read(tutor).andExpect(header().string("ETag", "\"4\"")).andExpect(jsonPath("$.version").value(2));
    }

    @Test
    void OP_24_a_rejected_profile_is_edited_into_a_new_version() throws Exception {
        Actor tutor = newActor(Actor.Role.TUTOR);
        UUID rejected = storedVersion(tutor, 1, 1, "REJECTED", false);
        verification(rejected, "REJECTED", "Mô tả chưa nêu cách dạy.");
        String before = row(rejected);

        update(tutor, 1, VALID).andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.status").value("DRAFT"));

        assertThat(row(rejected)).isEqualTo(before);
        assertThat(versions(tutor)).isEqualTo(2);
    }

    @Test
    void OP_24_a_profile_that_needs_more_information_is_edited_into_a_new_version() throws Exception {
        Actor tutor = newActor(Actor.Role.TUTOR);
        storedVersion(tutor, 1, 2, "APPROVED", true);
        UUID needsInfo = storedVersion(tutor, 2, 3, "PENDING", false);
        verification(needsInfo, "PENDING", "Bổ sung bằng tốt nghiệp.");
        String before = row(needsInfo);

        update(tutor, 3, VALID).andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(3))
                .andExpect(jsonPath("$.revision").value(4))
                .andExpect(jsonPath("$.status").value("DRAFT"));

        // Retiring the version that needed information belongs to the next submission (TB-12c).
        assertThat(row(needsInfo)).isEqualTo(before);
        assertThat(versions(tutor)).isEqualTo(3);
    }

    @Test
    void OP_24_a_profile_waiting_for_verification_is_locked_with_409() throws Exception {
        Actor tutor = newActor(Actor.Role.TUTOR);
        UUID waiting = storedVersion(tutor, 1, 1, "PENDING", false);
        verification(waiting, "PENDING", null);
        String before = row(waiting);

        update(tutor, 1, VALID).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TUTOR_PROFILE_UNDER_REVIEW"));

        assertThat(row(waiting)).isEqualTo(before);
        assertThat(versions(tutor)).isEqualTo(1);
    }

    // ---------------------------------------------------------------- access

    @Test
    void INV_06_each_tutor_reads_and_edits_only_their_own_profile() throws Exception {
        Actor tutor = newActor(Actor.Role.TUTOR);
        Actor other = newActor(Actor.Role.TUTOR);
        create(tutor, VALID).andExpect(status().isOk());

        read(other).andExpect(status().isNotFound());
        // The other tutor's If-Match only ever reaches their own profile, which does not exist.
        update(other, 1, body(OTHER_DESCRIPTION, 1, 1)).andExpect(status().isPreconditionFailed());

        read(tutor).andExpect(jsonPath("$.description").value(DESCRIPTION));
        assertThat(versions(other)).isZero();
    }

    @Test
    void UC03_a_parent_has_no_tutor_profile() throws Exception {
        Actor parent = newActor(Actor.Role.PARENT);
        read(parent).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_UNAVAILABLE"));
        create(parent, VALID).andExpect(status().isForbidden());
        assertThat(versions(parent)).isZero();
    }

    @Test
    void UC03_a_locked_tutor_cannot_save() throws Exception {
        Actor tutor = newActor(Actor.Role.TUTOR);
        jdbc.sql("update accounts set status = 'LOCKED' where id = ?").param(tutor.accountId()).update();
        create(tutor, VALID).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_UNAVAILABLE"));
        assertThat(versions(tutor)).isZero();
    }

    @Test
    void without_a_session_both_operations_are_401() throws Exception {
        mvc.perform(get(PATH)).andExpect(status().isUnauthorized()).andExpect(Contract.matches());
        mvc.perform(put(PATH).header("Idempotency-Key", newKey()).header("If-None-Match", "*")
                        .contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isUnauthorized()).andExpect(Contract.matches());
    }
}
