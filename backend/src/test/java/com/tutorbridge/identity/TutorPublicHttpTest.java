package com.tutorbridge.identity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tutorbridge.Contract;
import com.tutorbridge.HttpTest;
import com.tutorbridge.platform.api.Actor;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

/**
 * getTutorPublic (UC03, UC05; TB-17 PR 2): what a parent sees of a tutor before an agreement. Real
 * PostgreSQL, every answer checked against the contract. Fake accounts and texts only.
 */
class TutorPublicHttpTest extends HttpTest {

    private static final String DESCRIPTION = "Dạy đại số lớp 8 theo từng bước, có bài tập về nhà mỗi buổi học.";

    // ---------------------------------------------------------------- helpers

    private ResultActions read(Actor actor, UUID tutorId) throws Exception {
        return mvc.perform(get("/api/v1/tutors/{tutorId}/public-profile", tutorId).with(sessions().as(actor)))
                .andExpect(Contract.matches());
    }

    private Actor tutor(String displayName) {
        Actor tutor = newActor(Actor.Role.TUTOR);
        jdbc.sql("update accounts set display_name = ? where id = ?").params(displayName, tutor.accountId()).update();
        return tutor;
    }

    private UUID version(Actor tutor, int version, String status, boolean current, int rate) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                insert into tutor_profiles(id, tutor_id, version, status, description, experience_months, rate_vnd_60,
                                           current, revision)
                values (?, ?, ?, ?, ?, 0, ?, ?, 3)
                """).params(id, tutor.accountId(), version, status, DESCRIPTION, rate, current).update();
        return id;
    }

    private void verification(UUID profileId, String scope, String status) {
        Actor admin = newActor(Actor.Role.ADMIN);
        jdbc.sql("insert into admin_assignments(account_id, capability, granted_by) values (?, 'VERIFY', ?)")
                .params(admin.accountId(), admin.accountId()).update();
        jdbc.sql("""
                insert into tutor_verifications(id, profile_id, scope, status, evidence_key, reason, assigned_to,
                                                reviewed_by, reviewed_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, now())
                """).params(UUID.randomUUID(), profileId, scope, status, "0".repeat(64),
                        status.equals("VERIFIED") ? null : "Ảnh minh chứng bị mờ.", admin.accountId(),
                        admin.accountId()).update();
    }

    private void expectNotFound(Actor parent, UUID tutorId) throws Exception {
        read(parent, tutorId).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("TUTOR_NOT_FOUND"));
    }

    // ---------------------------------------------------------------- reading

    @Test
    void AC_UC03_03_a_parent_reads_the_current_approved_version_without_private_data() throws Exception {
        Actor parent = newActor(Actor.Role.PARENT);
        Actor tutor = tutor("Cô Lan Thử");
        UUID approved = version(tutor, 1, "APPROVED", true, 250000);
        verification(approved, "MATH8_ALGEBRA", "VERIFIED");

        read(parent, tutor.accountId())
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"3\""))
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(jsonPath("$.tutorId").value(tutor.accountId().toString()))
                .andExpect(jsonPath("$.profileVersionId").value(approved.toString()))
                .andExpect(jsonPath("$.displayName").value("Cô Lan Thử"))
                .andExpect(jsonPath("$.description").value(DESCRIPTION))
                .andExpect(jsonPath("$.rateVnd60").value(250000))
                // AC-UC03-01: zero months is valid, and experience is always self-declared.
                .andExpect(jsonPath("$.experienceMonths").value(0))
                .andExpect(jsonPath("$.experienceLabel").value("SELF_DECLARED"))
                .andExpect(jsonPath("$.verificationScopes[0]").value("MATH8_ALGEBRA"))
                .andExpect(jsonPath("$.verificationScopes.length()").value(1))
                // BR07, INV-60: no contact or evidence; the contract also forbids any other property.
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("0000000000"))));
    }

    @Test
    void only_verified_decisions_of_that_version_count_as_verified_scopes() throws Exception {
        Actor parent = newActor(Actor.Role.PARENT);
        Actor tutor = tutor("Thầy Minh Thử");
        UUID approved = version(tutor, 2, "APPROVED", true, 200000);
        UUID older = version(tutor, 1, "APPROVED", false, 180000);
        verification(approved, "MATH8_ALGEBRA", "VERIFIED");
        verification(approved, "BANG_SU_PHAM", "REJECTED");
        // An older version's decision, written before that version retired (V11 closes it after).
        verification(older, "CHUNG_CHI_CU", "VERIFIED");
        jdbc.sql("update tutor_profiles set status = 'RETIRED' where id = ?").param(older).update();

        read(parent, tutor.accountId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificationScopes.length()").value(1))
                .andExpect(jsonPath("$.verificationScopes[0]").value("MATH8_ALGEBRA"));
    }

    @Test
    void OP_24_while_an_edit_waits_parents_still_see_the_approved_version() throws Exception {
        Actor parent = newActor(Actor.Role.PARENT);
        Actor tutor = tutor("Cô Hoa Thử");
        UUID approved = version(tutor, 1, "APPROVED", true, 200000);
        version(tutor, 2, "PENDING", false, 300000);

        read(parent, tutor.accountId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileVersionId").value(approved.toString()))
                .andExpect(jsonPath("$.rateVnd60").value(200000))
                .andExpect(jsonPath("$.verificationScopes.length()").value(0));
    }

    // ---------------------------------------------------------------- one answer for everything else (INV-06)

    @Test
    void INV_06_no_approved_version_a_locked_tutor_a_parent_id_and_an_unknown_id_all_read_as_not_found() throws Exception {
        Actor parent = newActor(Actor.Role.PARENT);

        Actor waiting = tutor("Cô Mai Thử");
        version(waiting, 1, "PENDING", false, 200000);
        expectNotFound(parent, waiting.accountId());

        Actor locked = tutor("Thầy Nam Thử");
        version(locked, 1, "APPROVED", true, 200000);
        jdbc.sql("update accounts set status = 'LOCKED' where id = ?").param(locked.accountId()).update();
        expectNotFound(parent, locked.accountId());

        // BR01: missing data is not a pass; an account without a display name is never shown.
        Actor nameless = newActor(Actor.Role.TUTOR);
        version(nameless, 1, "APPROVED", true, 200000);
        expectNotFound(parent, nameless.accountId());

        expectNotFound(parent, newActor(Actor.Role.PARENT).accountId());
        expectNotFound(parent, UUID.randomUUID());
    }

    @Test
    void only_a_parent_may_read_it() throws Exception {
        Actor tutor = tutor("Cô Lan Thử");
        version(tutor, 1, "APPROVED", true, 250000);

        for (Actor.Role role : new Actor.Role[] {Actor.Role.TUTOR, Actor.Role.ADMIN}) {
            read(newActor(role), tutor.accountId())
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESS_UNAVAILABLE"));
        }
        // The tutor themself is not a parent either.
        read(tutor, tutor.accountId()).andExpect(status().isForbidden());
    }

    @Test
    void without_a_session_it_is_401() throws Exception {
        mvc.perform(get("/api/v1/tutors/{tutorId}/public-profile", UUID.randomUUID()))
                .andExpect(Contract.matches())
                .andExpect(status().isUnauthorized());
    }
}
