package com.tutorbridge.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tutorbridge.Contract;
import com.tutorbridge.HttpTest;
import com.tutorbridge.WebDebugLogging;
import com.tutorbridge.platform.api.Actor;
import com.tutorbridge.platform.api.IdempotencyKey;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** UC02 for the parent: listStudents, createStudent, getStudentProfile, patchStudent (TB-11). */
@ExtendWith(OutputCaptureExtension.class)
class StudentProfilesHttpTest extends HttpTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String STUDENTS = "/api/v1/students";

    // createStudent

    @Test
    void UC02_parent_creates_a_child_owned_by_the_session_with_a_first_goal_row() throws Exception {
        Actor parent = newActor(Actor.Role.PARENT);

        JsonNode body = read(create(parent, key(), profile("Minh", "Vững phương trình bậc nhất"))
                .andExpect(status().isCreated())
                .andExpect(header().string("ETag", "\"1\""))
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(jsonPath("$.displayName").value("Minh"))
                .andExpect(jsonPath("$.grade").value(8))
                .andExpect(jsonPath("$.revision").value(1))
                .andExpect(jsonPath("$.shortGoal").value("Vững phương trình bậc nhất")));

        UUID id = UUID.fromString(body.get("id").asString());
        assertThat(ownerOf(id)).isEqualTo(parent.accountId());
        assertThat(goalHistory(id)).containsExactly("1:Vững phương trình bậc nhất");
    }

    @Test
    void AC_UC02_02_an_owner_sent_by_the_app_is_rejected_not_used() throws Exception {
        Actor parent = newActor(Actor.Role.PARENT);
        Actor other = newActor(Actor.Role.PARENT);
        Map<String, Object> withOwner = new java.util.HashMap<>(profile("Minh", ""));
        withOwner.put("parentId", other.accountId().toString());

        create(parent, key(), withOwner)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        assertThat(childrenOf(parent.accountId()) + childrenOf(other.accountId())).isZero();
    }

    @Test
    void UC02_display_name_is_trimmed_and_lengths_count_code_points() throws Exception {
        Actor parent = newActor(Actor.Role.PARENT);
        String hundredEmoji = "😀".repeat(100);

        create(parent, key(), profile("  Minh  ", ""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.displayName").value("Minh"));
        create(parent, key(), profile(hundredEmoji, ""))
                .andExpect(status().isCreated());
        create(parent, key(), profile(hundredEmoji + "a", ""))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.fields[0].path").value("displayName"));
    }

    @Test
    void INV_30_only_grade_8_and_a_non_blank_name_are_accepted() throws Exception {
        Actor parent = newActor(Actor.Role.PARENT);

        create(parent, key(), Map.of("displayName", "Minh", "grade", 7, "shortGoal", ""))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.fields[0].path").value("grade"));
        create(parent, key(), profile("   ", ""))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.fields[0].path").value("displayName"));
        create(parent, key(), Map.of("displayName", "Minh", "grade", 8))
                .andExpect(status().isUnprocessableContent());
        create(parent, key(), profile("Minh", "x".repeat(2001)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.fields[0].path").value("shortGoal"));
        assertThat(childrenOf(parent.accountId())).isZero();
    }

    @Test
    void INV_20_the_same_key_and_body_replays_one_profile_and_a_new_body_is_a_conflict() throws Exception {
        Actor parent = newActor(Actor.Role.PARENT);
        String key = key();

        String first = read(create(parent, key, profile("Minh", "")).andExpect(status().isCreated()))
                .get("id").asString();
        String again = read(create(parent, key, profile("Minh", "")).andExpect(status().isCreated()))
                .get("id").asString();
        create(parent, key, profile("Lan", ""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));

        assertThat(again).isEqualTo(first);
        assertThat(childrenOf(parent.accountId())).isEqualTo(1);
    }

    @Test
    void INV_20_create_without_a_key_is_400() throws Exception {
        Actor parent = newActor(Actor.Role.PARENT);

        perform(post(STUDENTS).with(sessions().as(parent)), profile("Minh", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));
    }

    @Test
    void UC02_a_locked_parent_cannot_create() throws Exception {
        Actor parent = newActor(Actor.Role.PARENT);
        jdbc.sql("update accounts set status = 'LOCKED' where id = ?").param(parent.accountId()).update();

        create(parent, key(), profile("Minh", ""))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_UNAVAILABLE"));
    }

    // listStudents

    @Test
    void AC_UC02_01_each_parent_lists_only_own_children_in_the_order_added_across_pages() throws Exception {
        Actor parent = newActor(Actor.Role.PARENT);
        Actor other = newActor(Actor.Role.PARENT);
        String minh = createdId(parent, "Minh");
        String lan = createdId(parent, "Lan");
        createdId(other, "Hoa");

        JsonNode firstPage = read(perform(get(STUDENTS).param("limit", "1").with(sessions().as(parent)), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(minh)));
        String cursor = firstPage.get("nextCursor").asString();
        perform(get(STUDENTS).param("limit", "1").param("cursor", cursor).with(sessions().as(parent)), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(lan))
                .andExpect(jsonPath("$.nextCursor").isEmpty());
        perform(get(STUDENTS).with(sessions().as(other)), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].displayName").value("Hoa"));
    }

    @Test
    void INV_81_list_rejects_a_bad_cursor_and_a_limit_over_50() throws Exception {
        Actor parent = newActor(Actor.Role.PARENT);

        perform(get(STUDENTS).param("cursor", "not-a-cursor").with(sessions().as(parent)), null)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("CURSOR_INVALID"));
        perform(get(STUDENTS).param("limit", "51").with(sessions().as(parent)), null)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("LIMIT_INVALID"));
    }

    // getStudentProfile

    @Test
    void UC02_owner_reads_the_profile_with_its_version() throws Exception {
        Actor parent = newActor(Actor.Role.PARENT);
        String id = createdId(parent, "Minh");

        perform(get(STUDENTS + "/" + id).with(sessions().as(parent)), null)
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"1\""))
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.displayName").value("Minh"));
    }

    @Test
    void AC_UC02_02_another_parent_gets_the_same_404_as_an_unknown_id() throws Exception {
        Actor parent = newActor(Actor.Role.PARENT);
        Actor other = newActor(Actor.Role.PARENT);
        String id = createdId(parent, "Minh");

        String foreign = perform(get(STUDENTS + "/" + id).with(sessions().as(other)), null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STUDENT_NOT_FOUND"))
                .andReturn().getResponse().getContentAsString();
        String unknown = perform(get(STUDENTS + "/" + UUID.randomUUID()).with(sessions().as(other)), null)
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();

        assertThat(withoutTrace(foreign)).isEqualTo(withoutTrace(unknown));
        assertThat(foreign).doesNotContain("Minh");
    }

    // patchStudent

    @Test
    void UC02_editing_the_goal_keeps_the_old_goal_in_history() throws Exception {
        Actor parent = newActor(Actor.Role.PARENT);
        String id = read(create(parent, key(), profile("Minh", "Mục tiêu đầu"))).get("id").asString();

        edit(parent, id, key(), "\"1\"", Map.of("shortGoal", "Mục tiêu mới"))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"2\""))
                .andExpect(jsonPath("$.revision").value(2))
                .andExpect(jsonPath("$.shortGoal").value("Mục tiêu mới"))
                .andExpect(jsonPath("$.displayName").value("Minh"));
        edit(parent, id, key(), "\"2\"", Map.of("displayName", "Minh Anh"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revision").value(3))
                .andExpect(jsonPath("$.shortGoal").value("Mục tiêu mới"));

        assertThat(goalHistory(UUID.fromString(id))).containsExactly("1:Mục tiêu đầu", "2:Mục tiêu mới");
    }

    @Test
    void INV_23_patch_needs_the_current_version() throws Exception {
        Actor parent = newActor(Actor.Role.PARENT);
        String id = createdId(parent, "Minh");

        perform(patch(STUDENTS + "/" + id).header(IdempotencyKey.HEADER, key()).with(sessions().as(parent)),
                Map.of("displayName", "Lan"))
                .andExpect(status().isPreconditionRequired())
                .andExpect(jsonPath("$.code").value("PRECONDITION_REQUIRED"));
        edit(parent, id, key(), "\"7\"", Map.of("displayName", "Lan"))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
        perform(get(STUDENTS + "/" + id).with(sessions().as(parent)), null)
                .andExpect(jsonPath("$.displayName").value("Minh"))
                .andExpect(jsonPath("$.revision").value(1));
    }

    @Test
    void INV_20_a_replayed_patch_does_not_edit_twice() throws Exception {
        Actor parent = newActor(Actor.Role.PARENT);
        String id = createdId(parent, "Minh");
        String key = key();

        edit(parent, id, key, "\"1\"", Map.of("shortGoal", "Mục tiêu mới")).andExpect(status().isOk());
        edit(parent, id, key, "\"1\"", Map.of("shortGoal", "Mục tiêu mới"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revision").value(2));

        assertThat(goalHistory(UUID.fromString(id))).hasSize(2);
    }

    @Test
    void UC02_patch_rejects_an_empty_body_explicit_nulls_and_other_grades() throws Exception {
        Actor parent = newActor(Actor.Role.PARENT);
        String id = createdId(parent, "Minh");

        edit(parent, id, key(), "\"1\"", Map.of()).andExpect(status().isUnprocessableContent());
        perform(patch(STUDENTS + "/" + id).header(IdempotencyKey.HEADER, key()).header("If-Match", "\"1\"")
                        .with(sessions().as(parent)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":null}"), null)
                .andExpect(status().isUnprocessableContent());
        edit(parent, id, key(), "\"1\"", Map.of("grade", 9))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.fields[0].path").value("grade"));
        edit(parent, id, key(), "\"1\"", Map.of("parentId", UUID.randomUUID().toString()))
                .andExpect(status().isUnprocessableContent());

        perform(get(STUDENTS + "/" + id).with(sessions().as(parent)), null)
                .andExpect(jsonPath("$.revision").value(1));
    }

    @Test
    void AC_UC02_02_another_parent_cannot_edit_and_learns_nothing() throws Exception {
        Actor parent = newActor(Actor.Role.PARENT);
        Actor other = newActor(Actor.Role.PARENT);
        String id = createdId(parent, "Minh");

        edit(other, id, key(), "\"1\"", Map.of("displayName", "Lan"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STUDENT_NOT_FOUND"));
        assertThat(ownerOf(UUID.fromString(id))).isEqualTo(parent.accountId());
        perform(get(STUDENTS + "/" + id).with(sessions().as(parent)), null)
                .andExpect(jsonPath("$.displayName").value("Minh"));
    }

    // Roles and sessions

    @Test
    void INV_01_other_roles_cannot_list_or_create_and_get_404_for_a_profile() throws Exception {
        Actor parent = newActor(Actor.Role.PARENT);
        String id = createdId(parent, "Minh");

        for (Actor.Role role : List.of(Actor.Role.TUTOR, Actor.Role.ADMIN, Actor.Role.STUDENT)) {
            Actor caller = newActor(role);
            perform(get(STUDENTS).with(sessions().as(caller)), null)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESS_UNAVAILABLE"));
            create(caller, key(), profile("Minh", ""))
                    .andExpect(status().isForbidden());
            perform(get(STUDENTS + "/" + id).with(sessions().as(caller)), null)
                    .andExpect(status().isNotFound());
            edit(caller, id, key(), "\"1\"", Map.of("displayName", "Lan"))
                    .andExpect(status().isNotFound());
        }
        assertThat(childrenOf(parent.accountId())).isEqualTo(1);
    }

    @Test
    void INV_01_every_operation_needs_a_session() throws Exception {
        String id = UUID.randomUUID().toString();

        perform(get(STUDENTS), null).andExpect(status().isUnauthorized());
        perform(post(STUDENTS).header(IdempotencyKey.HEADER, key()), profile("Minh", ""))
                .andExpect(status().isUnauthorized());
        perform(get(STUDENTS + "/" + id), null).andExpect(status().isUnauthorized());
        perform(patch(STUDENTS + "/" + id).header(IdempotencyKey.HEADER, key()).header("If-Match", "\"1\""),
                Map.of("displayName", "Lan"))
                .andExpect(status().isUnauthorized());
    }

    // INV-63

    @Test
    @ExtendWith(WebDebugLogging.class)
    void INV_63_with_web_debug_on_no_child_name_or_goal_reaches_the_log(CapturedOutput output) throws Exception {
        Actor parent = newActor(Actor.Role.PARENT);
        // ASCII markers: a console that cannot print Vietnamese turns letters into '?', and a marker with
        // diacritics would then never match, so the test would pass while the name leaks.
        String name = "Zqnamelog";
        String goal = "Zqgoallog first goal";
        String newGoal = "Zqnewgoallog second goal";

        String id = read(create(parent, key(), profile(name, goal)).andExpect(status().isCreated()))
                .get("id").asString();
        perform(get(STUDENTS).with(sessions().as(parent)), null).andExpect(status().isOk());
        perform(get(STUDENTS + "/" + id).with(sessions().as(parent)), null).andExpect(status().isOk());
        edit(parent, id, key(), "\"1\"", Map.of("shortGoal", newGoal)).andExpect(status().isOk());

        assertThat(output.getAll()).doesNotContain(name).doesNotContain(goal).doesNotContain(newGoal);
    }

    private ResultActions create(Actor actor, String key, Map<String, ?> body) throws Exception {
        return perform(post(STUDENTS).header(IdempotencyKey.HEADER, key).with(sessions().as(actor)), body);
    }

    private ResultActions edit(Actor actor, String id, String key, String ifMatch, Map<String, ?> body)
            throws Exception {
        return perform(patch(STUDENTS + "/" + id).header(IdempotencyKey.HEADER, key).header("If-Match", ifMatch)
                .with(sessions().as(actor)), body);
    }

    /** Every response is checked against the contract. */
    private ResultActions perform(MockHttpServletRequestBuilder request, Map<String, ?> body) throws Exception {
        if (body != null) {
            request = request.contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(body));
        }
        return mvc.perform(request).andExpect(Contract.matches());
    }

    private String createdId(Actor parent, String name) throws Exception {
        return read(create(parent, key(), profile(name, "")).andExpect(status().isCreated())).get("id").asString();
    }

    private static Map<String, Object> profile(String name, String goal) {
        return Map.of("displayName", name, "grade", 8, "shortGoal", goal);
    }

    private static String key() {
        return "student-" + UUID.randomUUID();
    }

    private static JsonNode read(ResultActions actions) throws Exception {
        MvcResult result = actions.andReturn();
        return JSON.readTree(result.getResponse().getContentAsString());
    }

    private static String withoutTrace(String problem) {
        return problem.replaceAll("\"traceId\"\\s*:\\s*\"[^\"]*\"", "");
    }

    private UUID ownerOf(UUID student) {
        return jdbc.sql("select parent_id from students where id = ?").param(student).query(UUID.class).single();
    }

    private int childrenOf(UUID parent) {
        return jdbc.sql("select count(*) from students where parent_id = ?").param(parent).query(Integer.class).single();
    }

    private List<String> goalHistory(UUID student) {
        return jdbc.sql("""
                select revision || ':' || short_goal from student_goal_versions
                where student_id = ? order by revision
                """).param(student).query(String.class).list();
    }
}
