package com.tutorbridge;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tutorbridge.platform.api.Actor;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Shows the test kit end to end, and proves it works: a probe serves {@code listStudents} from the
 * database, and the responses pass the contract check. The real endpoint belongs to TB-11.
 */
@Import(HttpTestExampleTest.StudentsProbe.class)
class HttpTestExampleTest extends HttpTest {

    @RestController
    static class StudentsProbe {

        private final JdbcClient jdbc;

        StudentsProbe(JdbcClient jdbc) {
            this.jdbc = jdbc;
        }

        @GetMapping("/api/v1/students")
        Map<String, Object> list(@AuthenticationPrincipal Actor actor) {
            List<Map<String, Object>> items = jdbc.sql("""
                    select id, display_name as "displayName", grade, revision
                    from students where parent_id = ? order by id
                    """).param(actor.accountId()).query().listOfRows();
            var page = new java.util.HashMap<String, Object>();
            page.put("items", items);
            page.put("nextCursor", null);
            return page;
        }
    }

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
