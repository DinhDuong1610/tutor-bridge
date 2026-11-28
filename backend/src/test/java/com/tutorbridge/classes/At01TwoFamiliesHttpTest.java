package com.tutorbridge.classes;

import static com.tutorbridge.classes.OfferHttpFixture.id;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.tutorbridge.Contract;
import com.tutorbridge.HttpTest;
import com.tutorbridge.classes.OfferHttpFixture.Candidate;
import com.tutorbridge.classes.OfferHttpFixture.Need;
import com.tutorbridge.classes.OfferHttpFixture.Scenario;
import com.tutorbridge.classes.OfferHttpFixture.Tutor;
import com.tutorbridge.platform.api.Actor;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * AT01 (`08-nghiem-thu-backlog.md`, UC02–UC07): the Toán 8 online flow, for two families side by side.
 * Each parent adds a grade 8 child, saves the child's free time, writes and confirms a need, runs the
 * matching, sends an offer to the tutor it found, and the tutor accepts. Every step goes through the real
 * API, interleaved between the two families. Then:
 * <ul>
 * <li>each class carries its own family's goal, as version 1 {@code EFFECTIVE} (Q1 of #19), and the
 * sessions the parent sent, inside both calendars;
 * <li>neither family, nor either tutor, can read the other side's child, need or offer: the same 404 as an
 * unknown id, and lists that hold only their own (AC-UC02-02, INV-06).
 * </ul>
 * The tutors' approved profiles are seeded in SQL: approving one is UC03, covered over HTTP by
 * {@code identity.VerificationDecisionsHttpTest} and {@code identity.AdminAccessMatrixHttpTest}. The class
 * read API is #240; until it merges, the class is checked through the acceptOffer answer and the tables.
 * Lessons sit early in the morning, so the tutors of other tests in the same database stay out of the top
 * 5 of these runs. Real PostgreSQL, every answer checked against the contract. Fake accounts only.
 */
class At01TwoFamiliesHttpTest extends HttpTest {

    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final LocalTime FROM = LocalTime.of(5, 30);
    private static final LocalTime TO = LocalTime.of(8, 0);
    private static final int DURATION = 90;

    @Autowired
    TransactionTemplate tx;

    private OfferHttpFixture fx;

    @BeforeEach
    void fixture() {
        fx = new OfferHttpFixture(mvc, jdbc, tx, a -> sessions().as(a), this::newActor);
    }

    /** One family as the flow goes: filled in step by step. */
    private static final class Family {
        final String name;
        final String goal;
        final Actor parent;
        UUID student;
        Tutor tutor;
        Need need;
        Candidate candidate;
        UUID offer;
        String accepted;

        Family(String name, String goal, Actor parent) {
            this.name = name;
            this.goal = goal;
            this.parent = parent;
        }
    }

    @Test
    void AT01_two_families_each_get_a_class_with_their_own_goal_and_sessions_and_see_nothing_of_the_other()
            throws Exception {
        LocalDate start = LocalDate.now(VIETNAM).plusDays(1);
        List<Family> families = List.of(
                new Family("A", "Toán 8: nắm chắc phương trình bậc nhất một ẩn trước kỳ kiểm tra giữa kỳ.",
                        newActor(Actor.Role.PARENT)),
                new Family("B", "Toán 8: vẽ hình và chứng minh tam giác bằng nhau, làm đủ bài tập về nhà.",
                        newActor(Actor.Role.PARENT)));

        // UC02: a grade 8 child, and the child's free mornings for the 28 days of the cycle.
        for (Family f : families) {
            f.student = id(write(post("/api/v1/students"), f.parent,
                    "{\"displayName\":\"Hoc sinh " + f.name + "\",\"grade\":8,\"shortGoal\":\"\"}")
                    .andExpect(status().isCreated()));
            write(put("/api/v1/students/" + f.student + "/calendar").header("If-None-Match", "*"), f.parent,
                    mornings(start)).andExpect(status().is2xxSuccessful());
        }
        // The tutors: an approved profile each (seeded, see above) and free mornings saved over the API.
        for (Family f : families) {
            f.tutor = tutor("Cô " + f.name + " Thử", start);
        }
        // UC02, TX02: the need, written as a draft then confirmed against the child's calendar.
        for (Family f : families) {
            UUID need = id(write(post("/api/v1/students/" + f.student + "/needs"), f.parent, """
                    {"goal":"%s","budgetVndSession":200000,"durationMinutes":%d,"frequencyPer7Days":2,
                     "cycleStartDate":"%s"}""".formatted(f.goal, DURATION, start)).andExpect(status().isCreated()));
            String confirmed = OfferHttpFixture.body(write(post("/api/v1/needs/" + need + "/confirm")
                    .header("If-Match", "\"1\""), f.parent, "{\"confirmed\":true}").andExpect(status().isOk()));
            assertThat((String) JsonPath.read(confirmed, "$.status")).isEqualTo("CONFIRMED");
            UUID version = jdbc.sql("select current_version_id from learning_needs where id = ?").param(need)
                    .query(UUID.class).single();
            f.need = new Need(need, version, f.student,
                    UUID.fromString(JsonPath.read(confirmed, "$.studentCalendarId")), start);
        }
        // UC04: the matching run finds the family's tutor with a common schedule.
        for (Family f : families) {
            f.candidate = fx.run(f.parent, f.need, f.tutor);
            assertThat(f.candidate.schedule()).as(f.name + ": two sessions a week for 4 weeks").hasSize(8);
        }
        // UC05, TX04: the offer.
        for (Family f : families) {
            f.offer = id(fx.send(new Scenario(f.parent, f.need, f.candidate)).andExpect(status().isCreated()));
        }
        // UC06, TX05: the tutor accepts and the class is made.
        for (Family f : families) {
            f.accepted = OfferHttpFixture.body(write(post("/api/v1/offers/" + f.offer + "/accept")
                    .header("If-Match", "\"1\""), f.tutor.actor(), "{\"acceptUnchangedTerms\":true}")
                    .andExpect(status().isCreated()));
        }

        for (Family f : families) {
            assertTheClassCarriesTheFamilysGoalAndSessions(f);
        }
        assertNeitherSideSeesTheOther(families.get(0), families.get(1));
        assertNeitherSideSeesTheOther(families.get(1), families.get(0));
    }

    // ---------------------------------------------------------------- the class of each family

    private void assertTheClassCarriesTheFamilysGoalAndSessions(Family f) {
        UUID classId = UUID.fromString(JsonPath.read(f.accepted, "$.id"));
        assertThat((String) JsonPath.read(f.accepted, "$.goal")).as(f.name).isEqualTo(f.goal);
        assertThat((String) JsonPath.read(f.accepted, "$.studentId")).as(f.name).isEqualTo(f.student.toString());
        // OP-25 (#97): the tutor's answer names the child.
        assertThat((String) JsonPath.read(f.accepted, "$.studentDisplayName")).as(f.name).isEqualTo("Hoc sinh " + f.name);
        assertThat((String) JsonPath.read(f.accepted, "$.tutorId")).as(f.name)
                .isEqualTo(f.tutor.actor().accountId().toString());
        assertThat((Integer) JsonPath.read(f.accepted, "$.durationMinutes")).isEqualTo(DURATION);

        // Q1 of #19: the need's goal becomes version 1 of the class goal, already EFFECTIVE.
        String goal = jdbc.sql("select id || ' ' || version || ' ' || status || ' ' || goal from class_goal_versions "
                + "where class_id = ?").param(classId).query(String.class).single();
        assertThat(goal).as(f.name).isEqualTo(
                JsonPath.read(f.accepted, "$.effectiveGoalVersionId") + " 1 EFFECTIVE " + f.goal);

        // The sessions are the ones the parent sent, in order, inside both families' mornings.
        List<Instant> sent = f.candidate.schedule().stream().map(w -> Instant.parse(w.get("start"))).toList();
        List<OffsetDateTime[]> sessions = jdbc.sql("""
                select lower(during), upper(during) from lesson_sessions where class_id = ? and state = 'SCHEDULED'
                order by ordinal""").param(classId).query((rs, n) -> new OffsetDateTime[] {
                        rs.getObject(1, OffsetDateTime.class), rs.getObject(2, OffsetDateTime.class)}).list();
        assertThat(sessions.stream().map(s -> s[0].toInstant()).toList()).as(f.name).isEqualTo(sent);
        for (OffsetDateTime[] s : sessions) {
            ZonedDateTime from = s[0].atZoneSameInstant(VIETNAM);
            assertThat(Duration.between(s[0], s[1])).isEqualTo(Duration.ofMinutes(DURATION));
            assertThat(from.toLocalTime()).as(f.name + ": in the morning windows").isBetween(FROM,
                    TO.minusMinutes(DURATION));
            assertThat(from.toLocalDate()).isBetween(f.need.start(), f.need.start().plusDays(27));
        }
        assertThat(fx.count("""
                select count(*) from resource_bookings b join lesson_sessions s on s.id = b.session_id
                where s.class_id = ? and b.active
                  and ((b.resource_kind = 'STUDENT' and b.resource_id = ?) or (b.resource_kind = 'TUTOR' and b.resource_id = ?))
                """, classId, f.student, f.tutor.actor().accountId())).isEqualTo(2 * sent.size());
        assertThat(fx.count("select count(*) from learning_needs where id = ? and status = 'CLOSED'", f.need.id()))
                .isOne();
    }

    // ---------------------------------------------------------------- AC-UC02-02: two families apart

    private void assertNeitherSideSeesTheOther(Family self, Family other) throws Exception {
        String who = self.name + " about " + other.name;
        // The parent: the other child, need and offer answer as an unknown id would.
        assertSameAsUnknown(self.parent, "/api/v1/students/" + other.student + "/needs",
                "/api/v1/students/" + UUID.randomUUID() + "/needs", who);
        assertSameAsUnknown(self.parent, "/api/v1/needs/" + other.need.id(), "/api/v1/needs/" + UUID.randomUUID(), who);
        assertSameAsUnknown(self.parent, "/api/v1/offers/" + other.offer, "/api/v1/offers/" + UUID.randomUUID(), who);
        // The tutor: the other family's offer too.
        assertSameAsUnknown(self.tutor.actor(), "/api/v1/offers/" + other.offer,
                "/api/v1/offers/" + UUID.randomUUID(), who + " (tutor)");

        // The lists hold exactly their own.
        assertThat(ids(self.parent, "/api/v1/students")).as(who).containsExactly(self.student.toString());
        assertThat(ids(self.parent, "/api/v1/students/" + self.student + "/needs")).as(who)
                .containsExactly(self.need.id().toString());
        assertThat(ids(self.parent, "/api/v1/me/offers")).as(who).containsExactly(self.offer.toString());
        assertThat(ids(self.tutor.actor(), "/api/v1/me/offers")).as(who + " (tutor)")
                .containsExactly(self.offer.toString());
        for (Actor reader : List.of(self.parent, self.tutor.actor())) {
            String lists = read(reader, "/api/v1/me/offers").getContentAsString()
                    + read(reader, "/api/v1/students").getContentAsString();
            for (UUID id : List.of(other.student, other.need.id(), other.offer, other.need.versionId())) {
                assertThat(lists).as(who).doesNotContain(id.toString());
            }
        }
    }

    private void assertSameAsUnknown(Actor actor, String theirs, String unknown, String who) throws Exception {
        MockHttpServletResponse a = read(actor, theirs);
        MockHttpServletResponse b = read(actor, unknown);
        assertThat(a.getStatus()).as(who + " " + theirs + ": " + a.getContentAsString()).isEqualTo(404);
        assertThat(problem(a)).as(who + " " + theirs).isEqualTo(problem(b));
    }

    private List<String> ids(Actor actor, String path) throws Exception {
        MockHttpServletResponse answer = read(actor, path);
        assertThat(answer.getStatus()).as(path + ": " + answer.getContentAsString()).isEqualTo(200);
        return JsonPath.read(answer.getContentAsString(), "$.items[*].id");
    }

    // ---------------------------------------------------------------- set-up and requests

    /** An ACTIVE tutor with an APPROVED profile (seeded), whose free mornings are saved with replaceTutorCalendar. */
    private Tutor tutor(String name, LocalDate start) throws Exception {
        Actor tutor = newActor(Actor.Role.TUTOR);
        jdbc.sql("update accounts set display_name = ? where id = ?").params(name, tutor.accountId()).update();
        UUID profile = UUID.randomUUID();
        jdbc.sql("""
                insert into tutor_profiles(id, tutor_id, version, status, description, experience_months, rate_vnd_60,
                                           current)
                values (?, ?, 1, 'APPROVED', ?, 24, ?, true)
                """).params(profile, tutor.accountId(), OfferHttpFixture.DESCRIPTION, OfferHttpFixture.RATE).update();
        write(put("/api/v1/me/tutor-calendar").header("If-None-Match", "*"), tutor, mornings(start))
                .andExpect(status().is2xxSuccessful());
        UUID calendar = jdbc.sql("select id from calendars where owner_kind = 'TUTOR' and owner_id = ? and effective")
                .param(tutor.accountId()).query(UUID.class).single();
        return new Tutor(tutor, profile, calendar);
    }

    /** 05:30–08:00 Vietnam time on each of the 28 days from {@code first}, as a CalendarInput. */
    private static String mornings(LocalDate first) {
        String windows = IntStream.range(0, 28).mapToObj(i -> first.plusDays(i))
                .map(day -> "{\"start\":\"%s\",\"end\":\"%s\",\"kind\":\"AVAILABLE\"}".formatted(
                        day.atTime(FROM).atZone(VIETNAM).toInstant(), day.atTime(TO).atZone(VIETNAM).toInstant()))
                .collect(Collectors.joining(","));
        return "{\"timezone\":\"Asia/Ho_Chi_Minh\",\"windows\":[" + windows + "]}";
    }

    private ResultActions write(MockHttpServletRequestBuilder request, Actor actor, String json) throws Exception {
        return mvc.perform(request.with(sessions().as(actor)).header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(Contract.matches());
    }

    private MockHttpServletResponse read(Actor actor, String path) throws Exception {
        return mvc.perform(get(path).with(sessions().as(actor))).andExpect(Contract.matches()).andReturn()
                .getResponse();
    }

    private static String problem(MockHttpServletResponse answer) throws Exception {
        return answer.getStatus() + " " + answer.getContentAsString().replaceAll("\"traceId\":\"[^\"]*\",?", "");
    }
}
