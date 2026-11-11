package com.tutorbridge;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tutorbridge.platform.api.Actor;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Shows the test kit end to end on a real endpoint: {@code listStudents} (TB-11) reads the
 * database, and the responses pass the contract check. Full cases are in StudentProfilesHttpTest.
 */
class HttpTestExampleTest extends HttpTest {

    @Test
    void owner_sees_own_children_and_the_response_follows_the_contract() throws Exception {
        Actor parent = newActor(Actor.Role.PARENT);
        UUID child = UUID.randomUUID();
        jdbc.sql("insert into students(id, parent_id, display_name) values (?, ?, 'Minh')")
                .params(child, parent.accountId()).update();
        Actor otherParent = newActor(Actor.Role.PARENT);
        jdbc.sql("insert into students(id, parent_id, display_name) values (?, ?, 'Lan')")
                .params(UUID.randomUUID(), otherParent.accountId()).update();

        mvc.perform(get("/api/v1/students").with(sessions().as(parent)))
                .andExpect(status().isOk())
                .andExpect(Contract.matches())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(child.toString()))
                .andExpect(jsonPath("$.items[0].displayName").value("Minh"));
    }

    @Test
    void request_without_session_is_a_401_that_follows_the_contract() throws Exception {
        mvc.perform(get("/api/v1/students"))
                .andExpect(status().isUnauthorized())
                .andExpect(Contract.matches());
    }
}
