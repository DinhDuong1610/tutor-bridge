package com.tutorbridge.classes;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.jayway.jsonpath.JsonPath;
import com.tutorbridge.Contract;
import com.tutorbridge.platform.api.Actor;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A parent with a CONFIRMED need, a matchable tutor and a stored B run over HTTP, ready for sendOffer
 * (TB-18). Fake accounts and texts only.
 *
 * <p>The database is shared, and other tests run matching. Each scenario puts its cycle in its own 28
 * days far beyond the cycles of MatchingRunsHttpTest, so only its own tutors have free time there and its
 * tutors appear in no other run.
 */
final class OfferHttpFixture {

    static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");
    static final String DESCRIPTION = "Dạy đại số lớp 8 theo từng bước, có bài tập về nhà mỗi buổi học.";
    static final String GOAL = "Nắm chắc phương trình bậc nhất một ẩn trước kỳ kiểm tra.";
    static final int RATE = 120_000;
    /** 90 minutes at 120.000 ₫ / 60 minutes. */
    static final int PRICE = 180_000;
    private static final AtomicInteger CYCLES = new AtomicInteger();

    record Need(UUID id, UUID versionId, UUID studentId, UUID calendarId, LocalDate start) {}

    record Tutor(Actor actor, UUID profileId, UUID calendarId) {
        UUID id() {
            return actor.accountId();
        }
    }

    /** A run's candidate as the parent saw it, enough to build an OfferInput. */
    record Candidate(UUID runId, Tutor tutor, List<Map<String, String>> schedule) {}

    /** One parent, one need, one tutor and the run that found that tutor. */
    record Scenario(Actor parent, Need need, Candidate candidate) {}

    private final MockMvc mvc;
    private final JdbcClient jdbc;
    private final TransactionTemplate tx;
    private final Function<Actor, RequestPostProcessor> as;
    private final Function<Actor.Role, Actor> newActor;

    OfferHttpFixture(MockMvc mvc, JdbcClient jdbc, TransactionTemplate tx, Function<Actor, RequestPostProcessor> as,
            Function<Actor.Role, Actor> newActor) {
        this.mvc = mvc;
        this.jdbc = jdbc;
        this.tx = tx;
        this.as = as;
        this.newActor = newActor;
    }

    /** A cycle start far ahead, unused by any other scenario (and after those of MatchingRunsHttpTest). */
    static LocalDate farCycle() {
        return LocalDate.now(VIETNAM).plusDays(3000L + 35L * CYCLES.getAndIncrement());
    }

    static OffsetDateTime utc(ZonedDateTime time) {
        return time.toOffsetDateTime().withOffsetSameInstant(ZoneOffset.UTC);
    }

    /** 18:00–22:00 on each of the 28 days from {@code first}, Vietnam time. */
    UUID calendar(String ownerKind, UUID ownerId, LocalDate first) {
        jdbc.sql("update calendars set effective = false where owner_kind = ? and owner_id = ? and effective")
                .params(ownerKind, ownerId).update();
        int version = jdbc.sql("select coalesce(max(version), 0) + 1 from calendars where owner_kind = ? and owner_id = ?")
                .params(ownerKind, ownerId).query(Integer.class).single();
        UUID id = UUID.randomUUID();
        jdbc.sql("insert into calendars(id, owner_kind, owner_id, version, effective) values (?, ?, ?, ?, true)")
                .params(id, ownerKind, ownerId, version).update();
        IntStream.range(0, 28).forEach(i -> jdbc.sql("""
                insert into availability_windows(id, calendar_id, during, kind) values (?, ?, tstzrange(?, ?), 'AVAILABLE')
                """).params(UUID.randomUUID(), id, utc(first.plusDays(i).atTime(18, 0).atZone(VIETNAM)),
                        utc(first.plusDays(i).atTime(22, 0).atZone(VIETNAM))).update());
        return id;
    }

    /** A CONFIRMED need at revision 2, as confirmNeed leaves it: 90 minutes, twice a week, 200.000 ₫. */
    Need need(Actor parent, LocalDate start) {
        UUID student = UUID.randomUUID();
        jdbc.sql("insert into students(id, parent_id, display_name) values (?, ?, 'Hoc sinh thu')")
                .params(student, parent.accountId()).update();
        UUID calendar = calendar("STUDENT", student, start);
        return tx.execute(s -> {
            UUID need = UUID.randomUUID();
            UUID version = UUID.randomUUID();
            jdbc.sql("insert into learning_needs(id, student_id, status, revision) values (?, ?, 'CONFIRMED', 2)")
                    .params(need, student).update();
            jdbc.sql("""
                    insert into need_versions(id, need_id, version, goal, budget_vnd_session, duration_minutes,
                                              frequency, start_date, confirmed_at, student_calendar_id)
                    values (?, ?, 1, ?, 200000, 90, 2, ?, now(), ?)
                    """).params(version, need, GOAL, start, calendar).update();
            jdbc.sql("update learning_needs set current_version_id = ? where id = ?").params(version, need).update();
            return new Need(need, version, student, calendar, start);
        });
    }

    /** An ACTIVE tutor with a display name, a current APPROVED profile and free evenings in the cycle. */
    Tutor tutor(String name, LocalDate start) {
        Actor tutor = newActor.apply(Actor.Role.TUTOR);
        jdbc.sql("update accounts set display_name = ? where id = ?").params(name, tutor.accountId()).update();
        UUID profile = UUID.randomUUID();
        jdbc.sql("""
                insert into tutor_profiles(id, tutor_id, version, status, description, experience_months, rate_vnd_60,
                                           current)
                values (?, ?, 1, 'APPROVED', ?, 24, ?, true)
                """).params(profile, tutor.accountId(), DESCRIPTION, RATE).update();
        return new Tutor(tutor, profile, calendar("TUTOR", tutor.accountId(), start));
    }

    /** createMatchingRun B for the need; the candidate of {@code tutor} in it. */
    Candidate run(Actor parent, Need need, Tutor tutor) throws Exception {
        String body = mvc.perform(post("/api/v1/needs/" + need.id() + "/matching-runs").with(as.apply(parent))
                        .header("Idempotency-Key", UUID.randomUUID().toString()).header("If-Match", "\"2\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"B\",\"needVersionId\":\"%s\",\"fallbackExplicitlyChosen\":true}"
                                .formatted(need.versionId())))
                .andExpect(Contract.matches()).andReturn().getResponse().getContentAsString();
        UUID runId = UUID.fromString(JsonPath.read(body, "$.id"));
        List<Map<String, String>> schedule = JsonPath.read(body,
                "$.candidates[?(@.tutor.tutorId == '" + tutor.id() + "')].commonSchedule[*]");
        if (schedule.isEmpty()) {
            throw new IllegalStateException("The tutor is not a candidate of the run: " + body);
        }
        return new Candidate(runId, tutor, schedule);
    }

    /** A new parent, need and tutor, and the run that found the tutor. */
    Scenario scenario() throws Exception {
        Actor parent = newActor.apply(Actor.Role.PARENT);
        LocalDate start = farCycle();
        Need need = need(parent, start);
        Tutor tutor = tutor("Cô Lan Thử", start);
        return new Scenario(parent, need, run(parent, need, tutor));
    }

    /** The OfferInput the parent's app sends for this candidate. */
    static String input(Need need, Candidate candidate) {
        return input(need.versionId(), candidate.runId(), candidate.tutor().id(), candidate.tutor().profileId(),
                candidate.tutor().calendarId(), need.calendarId(), schedule(candidate.schedule()));
    }

    static String input(UUID needVersionId, UUID runId, UUID tutorId, UUID profileId, UUID tutorCalendarId,
            UUID studentCalendarId, String schedule) {
        return """
                {"needVersionId":"%s","matchingRunId":"%s","tutorId":"%s","profileVersionId":"%s",
                 "tutorCalendarVersionId":"%s","studentCalendarVersionId":"%s","schedule":%s,
                 "acceptUnchangedTerms":true}""".formatted(needVersionId, runId, tutorId, profileId, tutorCalendarId,
                studentCalendarId, schedule);
    }

    static String schedule(List<Map<String, String>> windows) {
        return windows.stream().map(w -> "{\"start\":\"%s\",\"end\":\"%s\"}".formatted(w.get("start"), w.get("end")))
                .collect(Collectors.joining(",", "[", "]"));
    }

    ResultActions send(Actor actor, UUID needId, String key, String ifMatch, String json) throws Exception {
        MockHttpServletRequestBuilder request = post("/api/v1/needs/" + needId + "/offers")
                .contentType(MediaType.APPLICATION_JSON).content(json);
        if (actor != null) {
            request.with(as.apply(actor));
        }
        if (key != null) {
            request.header("Idempotency-Key", key);
        }
        if (ifMatch != null) {
            request.header("If-Match", ifMatch);
        }
        return mvc.perform(request).andExpect(Contract.matches());
    }

    /** sendOffer of the scenario with a new key and the need's current revision. */
    ResultActions send(Scenario s) throws Exception {
        return send(s.parent(), s.need().id(), UUID.randomUUID().toString(), "\"2\"", input(s.need(), s.candidate()));
    }

    ResultActions read(Actor actor, UUID offerId) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/v1/offers/" + offerId);
        if (actor != null) {
            request.with(as.apply(actor));
        }
        return mvc.perform(request).andExpect(Contract.matches());
    }

    ResultActions list(Actor actor, String query) throws Exception {
        return mvc.perform(get("/api/v1/me/offers" + query).with(as.apply(actor))).andExpect(Contract.matches());
    }

    static String body(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString();
    }

    static UUID id(ResultActions result) throws Exception {
        return UUID.fromString(JsonPath.read(body(result), "$.id"));
    }

    int count(String sql, Object... params) {
        return jdbc.sql(sql).params(params).query(Integer.class).single();
    }

    /**
     * A PENDING offer of the scenario sent 49 hours ago, so its deadline passed an hour ago and the job has
     * not run yet. Written directly: sendOffer would never send one in the past.
     */
    UUID overdueOffer(Scenario s) {
        return tx.execute(st -> {
            UUID offer = UUID.randomUUID();
            jdbc.sql("""
                    insert into class_offers(id, need_id, need_version_id, tutor_id, profile_id, tutor_calendar_id,
                                             student_calendar_id, run_id, state, goal_snapshot, duration_minutes,
                                             frequency, cycle_start_date, rate_vnd_60, price_vnd_session, sent_at,
                                             expires_at)
                    values (?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', ?, 90, 2, ?, ?, ?,
                            date_trunc('milliseconds', now()) - interval '49 hours',
                            date_trunc('milliseconds', now()) - interval '1 hour')
                    """).params(offer, s.need().id(), s.need().versionId(), s.candidate().tutor().id(),
                            s.candidate().tutor().profileId(), s.candidate().tutor().calendarId(),
                            s.need().calendarId(), s.candidate().runId(), GOAL, s.need().start(), RATE, PRICE)
                    .update();
            List<Map<String, String>> windows = s.candidate().schedule();
            for (int i = 0; i < windows.size(); i++) {
                jdbc.sql("""
                        insert into offer_sessions(offer_id, ordinal, during)
                        values (?, ?, tstzrange(cast(? as timestamptz), cast(? as timestamptz), '[)'))
                        """).params(offer, i + 1, windows.get(i).get("start"), windows.get(i).get("end")).update();
            }
            return offer;
        });
    }
}
