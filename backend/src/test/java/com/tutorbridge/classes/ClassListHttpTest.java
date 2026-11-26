package com.tutorbridge.classes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.jayway.jsonpath.JsonPath;
import com.tutorbridge.Contract;
import com.tutorbridge.HttpTest;
import com.tutorbridge.classes.OfferFixture.Need;
import com.tutorbridge.classes.OfferFixture.Student;
import com.tutorbridge.classes.OfferFixture.Tutor;
import com.tutorbridge.classes.TutorCalendarReplayHttpTest.MovableClock;
import com.tutorbridge.classes.api.AcceptOffer;
import com.tutorbridge.platform.api.Actor;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * listClasses and the student's name on a class (#97; OP-27, OP-25; Đính 27/11/2026). A parent lists the
 * classes of their children, one child with studentId; a tutor lists their own; each row carries the next
 * session that has not ended. Classes are made by the TX05 service, as in ClassHttpTest. Real PostgreSQL,
 * every answer checked against the contract; only the clock is moved. Fake accounts and names only.
 */
@Import(TutorCalendarReplayHttpTest.MovableClockConfig.class)
class ClassListHttpTest extends HttpTest {

    @Autowired
    TransactionTemplate tx;

    @Autowired
    AcceptOffer acceptOffer;

    @Autowired
    MovableClock clock;

    private OfferFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new OfferFixture(jdbc, tx);
    }

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    /** One class made by TX05 for this child and tutor, weekly from 18:00; its id. */
    private UUID classFor(Student student, Tutor tutor) {
        return classFor(student, tutor, 18);
    }

    /** As above from {@code hour}:00, so one tutor's classes do not overlap. */
    private UUID classFor(Student student, Tutor tutor, int hour) {
        Need need = fixture.need(student);
        UUID offer = fixture.offer(need, tutor, fixture.sessionStart(hour, 0));
        return acceptOffer.accept(tutor.id(), offer, 1, UUID.randomUUID().toString()).id();
    }

    /** A second child of the same parent, named, with a free calendar as the fixture gives the first. */
    private Student sibling(Student first, String name) {
        UUID id = UUID.randomUUID();
        jdbc.sql("insert into students(id, parent_id, display_name) values (?, ?, ?)")
                .params(id, first.parentId(), name).update();
        UUID calendar = UUID.randomUUID();
        jdbc.sql("insert into calendars(id, owner_kind, owner_id, version, effective) values (?, 'STUDENT', ?, 1, true)")
                .params(calendar, id).update();
        jdbc.sql("""
                insert into availability_windows(id, calendar_id, during, kind)
                values (?, ?, tstzrange(now(), now() + interval '60 days', '[)'), 'AVAILABLE')
                """).params(UUID.randomUUID(), calendar).update();
        return new Student(id, first.parentId());
    }

    private void rename(Student student, String name) {
        jdbc.sql("update students set display_name = ? where id = ?").params(name, student.id()).update();
    }

    private static Actor parentOf(Student student) {
        return new Actor(student.parentId(), Actor.Role.PARENT, UUID.randomUUID());
    }

    private static Actor tutorActor(Tutor tutor) {
        return new Actor(tutor.id(), Actor.Role.TUTOR, UUID.randomUUID());
    }

    private MockHttpServletResponse list(Actor actor, String query) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/v1/me/classes" + query);
        if (actor != null) {
            request.with(sessions().as(actor));
        }
        return mvc.perform(request).andExpect(Contract.matches()).andReturn().getResponse();
    }

    private static List<String> classIds(MockHttpServletResponse answer) throws Exception {
        assertThat(answer.getStatus()).as(answer.getContentAsString()).isEqualTo(200);
        return JsonPath.read(answer.getContentAsString(), "$.items[*].class.id");
    }

    private static String problem(MockHttpServletResponse answer) throws Exception {
        return answer.getStatus() + " " + answer.getContentAsString().replaceAll("\"traceId\":\"[^\"]*\",?", "");
    }

    // ---------------------------------------------------------------- who sees which classes

    @Test
    void a_parent_lists_the_classes_of_every_child_newest_first_and_one_child_with_studentId() throws Exception {
        Student first = fixture.student();
        rename(first, "An Thu");
        Student second = sibling(first, "Binh Thu");
        UUID older = classFor(first, fixture.tutor());
        UUID newer = classFor(second, fixture.tutor());
        classFor(fixture.student(), fixture.tutor());

        String body = list(parentOf(first), "").getContentAsString();
        assertThat(classIds(list(parentOf(first), ""))).containsExactly(newer.toString(), older.toString());
        // OP-25: the class names its student, to the parent too.
        List<String> names = JsonPath.read(body, "$.items[*].class.studentDisplayName");
        assertThat(names).containsExactly("Binh Thu", "An Thu");

        assertThat(classIds(list(parentOf(first), "?studentId=" + first.id()))).containsExactly(older.toString());
        assertThat(classIds(list(parentOf(first), "?studentId=" + second.id()))).containsExactly(newer.toString());
    }

    @Test
    void a_tutor_lists_only_their_own_classes_with_the_students_name() throws Exception {
        Tutor tutor = fixture.tutor();
        Student student = fixture.student();
        rename(student, "Chi Thu");
        UUID own = classFor(student, tutor);
        classFor(fixture.student(), fixture.tutor());

        MockHttpServletResponse answer = list(tutorActor(tutor), "");
        assertThat(classIds(answer)).containsExactly(own.toString());
        assertThat((String) JsonPath.read(answer.getContentAsString(), "$.items[0].class.studentDisplayName"))
                .isEqualTo("Chi Thu");
        assertThat((String) JsonPath.read(answer.getContentAsString(), "$.items[0].class.goal")).isNotBlank();
    }

    @Test
    void another_familys_child_answers_as_an_unknown_one_and_their_classes_never_show() throws Exception {
        Student mine = fixture.student();
        Student theirs = fixture.student();
        UUID theirClass = classFor(theirs, fixture.tutor());
        Actor parent = parentOf(mine);

        MockHttpServletResponse other = list(parent, "?studentId=" + theirs.id());
        MockHttpServletResponse unknown = list(parent, "?studentId=" + UUID.randomUUID());
        assertThat(other.getStatus()).as(other.getContentAsString()).isEqualTo(404);
        assertThat(problem(other)).isEqualTo(problem(unknown));
        assertThat(other.getContentAsString()).doesNotContain(theirClass.toString());

        assertThat(classIds(list(parent, ""))).isEmpty();
        assertThat(list(parent, "").getContentAsString()).doesNotContain(theirClass.toString());
    }

    @Test
    void a_tutor_cannot_filter_by_student_and_other_roles_and_strangers_are_refused() throws Exception {
        Tutor tutor = fixture.tutor();
        Student student = fixture.student();
        classFor(student, tutor);

        MockHttpServletResponse filtered = list(tutorActor(tutor), "?studentId=" + student.id());
        assertThat(filtered.getStatus()).as(filtered.getContentAsString()).isEqualTo(422);

        assertThat(list(newActor(Actor.Role.STUDENT), "").getStatus()).isEqualTo(403);
        assertThat(list(newActor(Actor.Role.ADMIN), "").getStatus()).isEqualTo(403);
        assertThat(list(null, "").getStatus()).isEqualTo(401);
    }

    @Test
    void pages_follow_the_cursor_without_repeating_or_skipping_a_class() throws Exception {
        Tutor tutor = fixture.tutor();
        List<String> made = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            made.add(0, classFor(fixture.student(), tutor, 8 + 2 * i).toString());
        }

        List<String> seen = new ArrayList<>();
        String cursor = null;
        do {
            MockHttpServletResponse page = list(tutorActor(tutor), "?limit=2" + (cursor == null ? "" : "&cursor=" + cursor));
            seen.addAll(classIds(page));
            cursor = JsonPath.read(page.getContentAsString(), "$.nextCursor");
        } while (cursor != null);
        assertThat(seen).isEqualTo(made);
    }

    // ---------------------------------------------------------------- the next session

    @Test
    void the_next_session_is_the_earliest_scheduled_one_that_has_not_ended_and_null_after_the_last() throws Exception {
        Tutor tutor = fixture.tutor();
        UUID classId = classFor(fixture.student(), tutor);
        List<Map<String, Object>> sessions = jdbc.sql("""
                select id, ordinal, lower(during) as start, upper(during) as "end" from lesson_sessions
                where class_id = ? order by ordinal""").param(classId).query().listOfRows();
        assertThat(sessions).as("the fixture's weekly sessions").hasSizeGreaterThanOrEqualTo(3);

        assertThat(next(tutor)).isEqualTo(sessions.get(0).get("id").toString());

        // A cancelled session is not the next one.
        jdbc.sql("update lesson_sessions set state = 'CANCELLED' where id = ?").param(sessions.get(0).get("id")).update();
        assertThat(next(tutor)).isEqualTo(sessions.get(1).get("id").toString());

        // A session still going on is next; one that has ended is not.
        Instant secondStart = ((OffsetDateTime) sessions.get(1).get("start")).toInstant();
        Instant secondEnd = ((OffsetDateTime) sessions.get(1).get("end")).toInstant();
        clock.set(secondStart.plusSeconds(60));
        assertThat(next(tutor)).isEqualTo(sessions.get(1).get("id").toString());
        clock.set(secondEnd);
        assertThat(next(tutor)).isEqualTo(sessions.get(2).get("id").toString());

        Instant lastEnd = ((OffsetDateTime) sessions.getLast().get("end")).toInstant();
        clock.set(lastEnd.plusSeconds(1));
        MockHttpServletResponse answer = list(tutorActor(tutor), "");
        Object none = JsonPath.read(answer.getContentAsString(), "$.items[0].nextSession");
        assertThat(none).isNull();
    }

    private String next(Tutor tutor) throws Exception {
        MockHttpServletResponse answer = list(tutorActor(tutor), "");
        assertThat(answer.getStatus()).as(answer.getContentAsString()).isEqualTo(200);
        return JsonPath.read(answer.getContentAsString(), "$.items[0].nextSession.id");
    }

    // ---------------------------------------------------------------- getClass names the student too

    @Test
    void getClass_names_the_student_to_both_parties() throws Exception {
        Tutor tutor = fixture.tutor();
        Student student = fixture.student();
        rename(student, "Dung Thu");
        UUID classId = classFor(student, tutor);

        for (Actor reader : List.of(parentOf(student), tutorActor(tutor))) {
            MockHttpServletResponse answer = mvc.perform(get("/api/v1/classes/" + classId)
                    .with(sessions().as(reader))).andExpect(Contract.matches()).andReturn().getResponse();
            assertThat(answer.getStatus()).isEqualTo(200);
            assertThat((String) JsonPath.read(answer.getContentAsString(), "$.studentDisplayName"))
                    .isEqualTo("Dung Thu");
        }
    }
}
