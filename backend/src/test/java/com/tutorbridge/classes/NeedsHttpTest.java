package com.tutorbridge.classes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * UC04 learning needs over HTTP (TB-16 part 1): listNeeds, createNeedDraft, getNeed, updateNeedDraft,
 * with Đính's answers on #16 (Q1–Q4). Real PostgreSQL, every answer checked against the contract.
 * Fake accounts and texts only.
 */
class NeedsHttpTest extends HttpTest {

    private static final String GOAL = "Nắm chắc phương trình bậc nhất một ẩn trước kỳ kiểm tra giữa kỳ.";
    private static final String FULL = """
            {"goal":"%s","budgetVndSession":200000,"durationMinutes":90,"frequencyPer7Days":2,
             "cycleStartDate":"2026-12-01"}""".formatted(GOAL);

    private UUID studentOf(Actor parent) {
        UUID id = UUID.randomUUID();
        jdbc.sql("insert into students(id, parent_id, display_name) values (?, ?, 'Hoc sinh thu')")
                .params(id, parent.accountId()).update();
        return id;
    }

    private static String newKey() {
        return UUID.randomUUID().toString();
    }

    private ResultActions create(Actor actor, UUID studentId, String key, String json) throws Exception {
        MockHttpServletRequestBuilder request = post("/api/v1/students/" + studentId + "/needs")
                .with(sessions().as(actor)).contentType(MediaType.APPLICATION_JSON).content(json);
        if (key != null) {
            request.header("Idempotency-Key", key);
        }
        return mvc.perform(request).andExpect(Contract.matches());
    }

    private ResultActions create(Actor actor, UUID studentId, String json) throws Exception {
        return create(actor, studentId, newKey(), json);
    }

    private ResultActions update(Actor actor, UUID needId, String key, String ifMatch, String json) throws Exception {
        MockHttpServletRequestBuilder request = patch("/api/v1/needs/" + needId)
                .with(sessions().as(actor)).contentType(MediaType.APPLICATION_JSON).content(json);
        if (key != null) {
            request.header("Idempotency-Key", key);
        }
        if (ifMatch != null) {
            request.header("If-Match", ifMatch);
        }
        return mvc.perform(request).andExpect(Contract.matches());
    }

    private ResultActions read(Actor actor, UUID needId) throws Exception {
        return mvc.perform(get("/api/v1/needs/" + needId).with(sessions().as(actor))).andExpect(Contract.matches());
    }

    private ResultActions list(Actor actor, UUID studentId, String query) throws Exception {
        return mvc.perform(get("/api/v1/students/" + studentId + "/needs" + query).with(sessions().as(actor)))
                .andExpect(Contract.matches());
    }

    private static UUID idOf(ResultActions result) throws Exception {
        return UUID.fromString(JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id"));
    }

    private static String versionOf(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.versionId");
    }

    private int needs(UUID studentId) {
        return jdbc.sql("select count(*) from learning_needs where student_id = ?").param(studentId)
                .query(Integer.class).single();
    }

    private int versions(UUID needId) {
        return jdbc.sql("select count(*) from need_versions where need_id = ?").param(needId)
                .query(Integer.class).single();
    }

    /** confirmNeed is part 2: build a confirmed version the way it will, with the child's calendar. */
    private void confirm(UUID needId, UUID studentId) {
        UUID calendar = UUID.randomUUID();
        jdbc.sql("insert into calendars(id, owner_kind, owner_id, version, effective) values (?, 'STUDENT', ?, 1, true)")
                .params(calendar, studentId).update();
        jdbc.sql("""
                update need_versions set confirmed_at = now(), student_calendar_id = ?
                where id = (select current_version_id from learning_needs where id = ?)
                """).params(calendar, needId).update();
        jdbc.sql("update learning_needs set status = 'CONFIRMED', revision = revision + 1 where id = ?")
                .param(needId).update();
    }

    // ---------------------------------------------------------------- create, read, list

    @Test
    void UC04_the_parent_saves_a_draft_for_their_child_and_reads_it_back() throws Exception {
        Actor parent = newActor(Actor.Role.PARENT);
        UUID student = studentOf(parent);

        ResultActions created = create(parent, student, FULL)
                .andExpect(status().isCreated())
                .andExpect(header().string("ETag", "\"1\""))
                .andExpect(jsonPath("$.studentId").value(student.toString()))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.revision").value(1))
                .andExpect(jsonPath("$.studentCalendarId").isEmpty())
                .andExpect(jsonPath("$.draft.goal").value(GOAL))
                .andExpect(jsonPath("$.draft.cycleStartDate").value("2026-12-01"));
        UUID need = idOf(created);

        read(parent, need)
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"1\""))
                .andExpect(jsonPath("$.versionId").value(versionOf(created)))
                .andExpect(jsonPath("$.draft.budgetVndSession").value(200000))
                .andExpect(jsonPath("$.draft.durationMinutes").value(90))
                .andExpect(jsonPath("$.draft.frequencyPer7Days").value(2));
        list(parent, student, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(need.toString()))
                .andExpect(jsonPath("$.nextCursor").isEmpty());
    }

    @Test
    void Q3_an_empty_draft_is_a_valid_draft_and_its_fields_are_left_out() throws Exception {
        Actor parent = newActor(Actor.Role.PARENT);
        UUID student = studentOf(parent);

        create(parent, student, "{}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.draft").isMap())
                .andExpect(jsonPath("$.draft.goal").doesNotExist())
                .andExpect(jsonPath("$.draft.budgetVndSession").doesNotExist());
    }

    @Test
    void INV_06_another_parents_child_or_need_is_the_same_404_as_none() throws Exception {
        Actor parent = newActor(Actor.Role.PARENT);
        Actor other = newActor(Actor.Role.PARENT);
        UUID student = studentOf(parent);
        UUID need = idOf(create(parent, student, FULL));

        for (UUID unknownStudent : List.of(student, UUID.randomUUID())) {
            create(other, unknownStudent, FULL).andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("STUDENT_NOT_FOUND"));
            list(other, unknownStudent, "").andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("STUDENT_NOT_FOUND"));
        }
        for (UUID unknownNeed : List.of(need, UUID.randomUUID())) {
            read(other, unknownNeed).andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("NEED_NOT_FOUND"));
            update(other, unknownNeed, newKey(), "\"1\"", "{}").andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("NEED_NOT_FOUND"));
        }
        read(parent, need).andExpect(status().isOk()).andExpect(jsonPath("$.draft.goal").value(GOAL));
        assertThat(needs(student)).isEqualTo(1);
    }

    @Test
    void UC04_a_tutor_has_no_needs() throws Exception {
        Actor parent = newActor(Actor.Role.PARENT);
        Actor tutor = newActor(Actor.Role.TUTOR);
        UUID student = studentOf(parent);
        UUID need = idOf(create(parent, student, FULL));

        create(tutor, student, FULL).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_UNAVAILABLE"));
        list(tutor, student, "").andExpect(status().isForbidden());
        read(tutor, need).andExpect(status().isForbidden());
        update(tutor, need, newKey(), "\"1\"", "{}").andExpect(status().isForbidden());
    }

    @Test
    void without_a_session_every_operation_is_401() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(get("/api/v1/needs/" + id)).andExpect(status().isUnauthorized()).andExpect(Contract.matches());
        mvc.perform(get("/api/v1/students/" + id + "/needs")).andExpect(status().isUnauthorized())
                .andExpect(Contract.matches());
    }

    // ---------------------------------------------------------------- Q4: one open need per child

    @Test
    void Q4_a_child_with_an_open_need_gets_no_second_one_until_it_is_closed() throws Exception {
        Actor parent = newActor(Actor.Role.PARENT);
        UUID student = studentOf(parent);
        UUID sibling = studentOf(parent);
        UUID first = idOf(create(parent, student, FULL));

        create(parent, student, "{}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NEED_ALREADY_OPEN"));
        confirm(first, student);
        create(parent, student, "{}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NEED_ALREADY_OPEN"));
        create(parent, sibling, "{}").andExpect(status().isCreated());

        jdbc.sql("update learning_needs set status = 'CLOSED' where id = ?").param(first).update();
        UUID second = idOf(create(parent, student, "{}").andExpect(status().isCreated()));
        list(parent, student, "")
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].id").value(second.toString()))
                .andExpect(jsonPath("$.items[1].status").value("CLOSED"));
    }

    @Test
    void Q4_two_creates_at_once_for_one_child_make_one_need_and_the_other_is_409() throws Exception {
        Actor parent = newActor(Actor.Role.PARENT);
        UUID student = studentOf(parent);
        CompletableFuture<Integer> first;
        CompletableFuture<Integer> second;
        try (Connection gate = DriverManager.getConnection(
                PostgresTestSupport.jdbcUrl(), "tutorbridge_app", PostgresTestSupport.APP_PASSWORD)) {
            gate.setAutoCommit(false);
            try (var hold = gate.prepareStatement("select pg_advisory_xact_lock(hashtextextended(?, 0))")) {
                hold.setString(1, "STUDENT:" + student);
                hold.execute();
            }
            first = Concurrently.supply(() -> statusOf(() -> create(parent, student, FULL)));
            second = Concurrently.supply(() -> statusOf(() -> create(parent, student, "{}")));
            // Both wait on the child's lock with their receipt taken and nothing checked yet.
            await().atMost(Duration.ofSeconds(4)).pollInterval(Duration.ofMillis(20)).until(() -> jdbc.sql("""
                    select count(*) from pg_stat_activity
                    where wait_event_type = 'Lock' and wait_event = 'advisory'
                      and query like 'select pg_advisory_xact_lock%'
                    """).query(Integer.class).single() == 2);
            gate.commit();
        }

        assertThat(List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS)))
                .containsExactlyInAnyOrder(201, 409);
        assertThat(needs(student)).isEqualTo(1);
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

    // ---------------------------------------------------------------- validation (NeedDraftInput, Q1)

    @Test
    void the_draft_limits_are_422_and_leave_nothing() throws Exception {
        Actor parent = newActor(Actor.Role.PARENT);
        UUID student = studentOf(parent);
        String window = "{\"start\":\"2026-12-01T12:00:00Z\",\"end\":\"2026-12-01T13:00:00Z\",\"kind\":\"AVAILABLE\"}";

        Map<String, String> refused = Map.of(
                "{\"goal\":\"" + "ạ".repeat(2001) + "\"}", "goal",
                "{\"budgetVndSession\":0}", "budgetVndSession",
                "{\"durationMinutes\":45}", "durationMinutes",
                "{\"frequencyPer7Days\":4}", "frequencyPer7Days",
                "{\"availableWindows\":[" + window + "]}", "availableWindows",
                "{\"preferredWindows\":[" + window + "]}", "availableWindows");
        for (var entry : refused.entrySet()) {
            create(parent, student, entry.getKey())
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.fields[0].path").value(entry.getValue()));
        }
        // A fraction in an integer field is refused, not cut (#168).
        for (String json : List.of("{\"goal\":null}", "{\"cycleStartDate\":\"01/12/2026\"}", "{\"studentId\":\"x\"}",
                "{\"budgetVndSession\":1.5}")) {
            create(parent, student, json).andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }
        assertThat(needs(student)).isZero();

        // 2000 code points is the limit, even when each is two UTF-16 units; empty window arrays are fine.
        create(parent, student, "{\"goal\":\"" + "𝑥".repeat(2000) + "\",\"availableWindows\":[],\"preferredWindows\":[]}")
                .andExpect(status().isCreated());
    }

    // ---------------------------------------------------------------- idempotency (INV-20)

    @Test
    void INV_20_the_same_key_replays_one_need_another_body_is_409_and_no_key_is_400() throws Exception {
        Actor parent = newActor(Actor.Role.PARENT);
        UUID student = studentOf(parent);
        UUID sibling = studentOf(parent);
        String key = newKey();

        UUID need = idOf(create(parent, student, key, FULL).andExpect(status().isCreated()));
        create(parent, student, key, FULL).andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(need.toString()));
        create(parent, student, key, "{}").andExpect(status().isConflict());
        create(parent, sibling, key, FULL).andExpect(status().isConflict());
        create(parent, student, null, FULL).andExpect(status().isBadRequest());
        assertThat(needs(student)).isEqualTo(1);
        assertThat(needs(sibling)).isZero();

        String updateKey = newKey();
        update(parent, need, updateKey, "\"1\"", "{\"goal\":\"" + GOAL + "\"}").andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"2\""));
        // The replay answers the need as it is now, not a second edit (the If-Match of the first send is stale).
        update(parent, need, updateKey, "\"1\"", "{\"goal\":\"" + GOAL + "\"}").andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"2\""));
        update(parent, need, updateKey, "\"1\"", "{}").andExpect(status().isConflict());
        update(parent, need, null, "\"2\"", "{}").andExpect(status().isBadRequest());
        read(parent, need).andExpect(header().string("ETag", "\"2\""));
    }

    // ---------------------------------------------------------------- update (Q2, Q3, INV-23)

    @Test
    void Q2_Q3_editing_a_draft_replaces_it_in_place_and_raises_the_revision() throws Exception {
        Actor parent = newActor(Actor.Role.PARENT);
        UUID student = studentOf(parent);
        ResultActions created = create(parent, student, FULL);
        UUID need = idOf(created);

        update(parent, need, newKey(), "\"1\"", "{\"durationMinutes\":60}")
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"2\""))
                .andExpect(jsonPath("$.versionId").value(versionOf(created)))
                .andExpect(jsonPath("$.status").value("DRAFT"));
        read(parent, need)
                .andExpect(jsonPath("$.revision").value(2))
                .andExpect(jsonPath("$.draft.durationMinutes").value(60))
                .andExpect(jsonPath("$.draft.goal").doesNotExist())
                .andExpect(jsonPath("$.draft.cycleStartDate").doesNotExist());
        assertThat(versions(need)).isEqualTo(1);
    }

    @Test
    void INV_23_a_stale_or_missing_if_match_changes_nothing() throws Exception {
        Actor parent = newActor(Actor.Role.PARENT);
        UUID student = studentOf(parent);
        UUID need = idOf(create(parent, student, FULL));
        update(parent, need, newKey(), "\"1\"", FULL).andExpect(status().isOk());

        update(parent, need, newKey(), "\"1\"", "{}").andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
        update(parent, need, newKey(), null, "{}").andExpect(status().isPreconditionRequired());
        read(parent, need).andExpect(header().string("ETag", "\"2\"")).andExpect(jsonPath("$.draft.goal").value(GOAL));
    }

    @Test
    void UC04_editing_a_confirmed_need_makes_a_new_unconfirmed_version_and_the_confirmed_one_stays() throws Exception {
        Actor parent = newActor(Actor.Role.PARENT);
        UUID student = studentOf(parent);
        ResultActions created = create(parent, student, FULL);
        UUID need = idOf(created);
        String confirmedVersion = versionOf(created);
        confirm(need, student);
        read(parent, need).andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.studentCalendarId").isNotEmpty());

        ResultActions edited = update(parent, need, newKey(), "\"2\"", "{\"goal\":\"" + GOAL + " Thêm hình học.\"}")
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"3\""))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.studentCalendarId").isEmpty());
        assertThat(versionOf(edited)).isNotEqualTo(confirmedVersion);
        read(parent, need).andExpect(jsonPath("$.versionId").value(versionOf(edited)))
                .andExpect(jsonPath("$.draft.budgetVndSession").doesNotExist());

        assertThat(versions(need)).isEqualTo(2);
        assertThat(jdbc.sql("""
                select count(*) from need_versions
                where id = ? and version = 1 and confirmed_at is not null and goal = ? and budget_vnd_session = 200000
                """).params(UUID.fromString(confirmedVersion), GOAL).query(Integer.class).single()).isEqualTo(1);

        // The new version is a draft again: edited in place from now on (Q2).
        update(parent, need, newKey(), "\"3\"", "{}").andExpect(status().isOk())
                .andExpect(jsonPath("$.versionId").value(versionOf(edited)));
        assertThat(versions(need)).isEqualTo(2);
    }

    @Test
    void UC04_a_closed_need_is_not_edited() throws Exception {
        Actor parent = newActor(Actor.Role.PARENT);
        UUID student = studentOf(parent);
        UUID need = idOf(create(parent, student, FULL));
        jdbc.sql("update learning_needs set status = 'CLOSED' where id = ?").param(need).update();

        update(parent, need, newKey(), "\"1\"", "{}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NEED_CLOSED"));
        read(parent, need).andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.draft.goal").value(GOAL));
    }

    // ---------------------------------------------------------------- list

    @Test
    void the_list_pages_newest_first_and_keeps_to_one_child() throws Exception {
        Actor parent = newActor(Actor.Role.PARENT);
        UUID student = studentOf(parent);
        UUID sibling = studentOf(parent);
        UUID older = idOf(create(parent, student, "{}"));
        jdbc.sql("update learning_needs set status = 'CLOSED' where id = ?").param(older).update();
        UUID newer = idOf(create(parent, student, "{}"));
        create(parent, sibling, "{}");

        String page = list(parent, student, "?limit=1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(newer.toString()))
                .andExpect(jsonPath("$.nextCursor").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String cursor = JsonPath.read(page, "$.nextCursor");
        list(parent, student, "?limit=1&cursor=" + cursor)
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(older.toString()))
                .andExpect(jsonPath("$.nextCursor").isEmpty());
        list(parent, student, "?limit=51").andExpect(status().isUnprocessableContent());
        list(parent, student, "?cursor=abc").andExpect(status().isUnprocessableContent());
    }
}
