package com.tutorbridge.platform.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tutorbridge.Contract;
import com.tutorbridge.HttpTest;
import com.tutorbridge.platform.api.Actor;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * No read answers 304: the contract declares none, and every answer is private, no-store (Huy, 25/11/2026,
 * after Codex's review of #222). A GET with the ETag it was given still gets the full answer. Writes keep
 * their validators: {@code If-None-Match: *} on a create is checked in TutorCalendarHttpTest
 * ({@code inv_23_a_stale_or_wrong_version_is_412_and_changes_nothing}).
 */
class ConditionalGetHttpTest extends HttpTest {

    @Test
    void a_get_whose_if_none_match_or_if_modified_since_matches_still_answers_in_full() throws Exception {
        Actor parent = newActor(Actor.Role.PARENT);
        UUID student = UUID.randomUUID();
        jdbc.sql("insert into students(id, parent_id, display_name) values (?, ?, 'Minh')")
                .params(student, parent.accountId()).update();
        String path = "/api/v1/students/" + student;
        mvc.perform(get(path).with(sessions().as(parent))).andExpect(Contract.matches())
                .andExpect(status().isOk()).andExpect(header().string("ETag", "\"1\""));

        mvc.perform(get(path).header("If-None-Match", "\"1\"").with(sessions().as(parent)))
                .andExpect(Contract.matches())
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"1\""))
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(jsonPath("$.displayName").value("Minh"));
        mvc.perform(get(path).header("If-None-Match", "*").with(sessions().as(parent)))
                .andExpect(Contract.matches()).andExpect(status().isOk());
        mvc.perform(get(path).header("If-Modified-Since", "Wed, 01 Jan 2099 00:00:00 GMT")
                        .with(sessions().as(parent)))
                .andExpect(Contract.matches()).andExpect(status().isOk());
    }
}
