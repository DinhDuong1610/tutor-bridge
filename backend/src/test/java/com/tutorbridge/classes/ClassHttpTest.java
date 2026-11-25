package com.tutorbridge.classes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.tutorbridge.Contract;
import com.tutorbridge.HttpTest;
import com.tutorbridge.classes.OfferFixture.Need;
import com.tutorbridge.classes.OfferFixture.Tutor;
import com.tutorbridge.classes.api.AcceptOffer;
import com.tutorbridge.classes.api.AcceptOffer.AcceptedClass;
import com.tutorbridge.platform.api.Actor;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * getClass and listSessions over HTTP (UC07, TB-20). The class is made by the TX05 service already on dev,
 * so these tests do not wait for acceptOffer over HTTP (TB-19). Real PostgreSQL, every answer checked
 * against the contract. Fake accounts and texts only.
 */
class ClassHttpTest extends HttpTest {

    @Autowired
    TransactionTemplate tx;

    @Autowired
    AcceptOffer acceptOffer;

    private OfferFixture fixture;

    private record Made(AcceptedClass created, Actor parent, Actor tutor, Need need, UUID offer) {}

    @BeforeEach
    void setUp() {
        fixture = new OfferFixture(jdbc, tx);
    }

    /** A class of 4 weekly sessions made by TX05, and its two parties as signed-in actors. */
    private Made aClass() {
        Need need = fixture.need(fixture.student());
        Tutor tutor = fixture.tutor();
        UUID offer = fixture.offer(need, tutor);
        AcceptedClass created = acceptOffer.accept(tutor.id(), offer, 1, UUID.randomUUID().toString());
        return new Made(created, actor(need.student().parentId(), Actor.Role.PARENT),
                actor(tutor.id(), Actor.Role.TUTOR), need, offer);
    }

    private static Actor actor(UUID accountId, Actor.Role role) {
        return new Actor(accountId, role, UUID.randomUUID());
    }

    private ResultActions read(Actor actor, String path) throws Exception {
        MockHttpServletRequestBuilder request = get(path);
        if (actor != null) {
            request.with(sessions().as(actor));
        }
        return mvc.perform(request).andExpect(Contract.matches());
    }

    private ResultActions getClass(Actor actor, UUID classId) throws Exception {
        return read(actor, "/api/v1/classes/" + classId);
    }

    private ResultActions sessionsOf(Actor actor, UUID classId, String query) throws Exception {
        return read(actor, "/api/v1/classes/" + classId + "/sessions" + query);
    }

    // ---------------------------------------------------------------- the two parties read the class

    @Test
    void AC_UC07_04_the_parent_and_the_tutor_read_the_class_as_tx05_made_it() throws Exception {
        Made m = aClass();
        UUID classId = m.created().id();

        for (Actor party : List.of(m.parent(), m.tutor())) {
            getClass(party, classId).andExpect(status().isOk())
                    .andExpect(header().string("ETag", "\"1\""))
                    .andExpect(header().string("Cache-Control", "private, no-store"))
                    .andExpect(jsonPath("$.id").value(classId.toString()))
                    .andExpect(jsonPath("$.studentId").value(m.need().student().id().toString()))
                    .andExpect(jsonPath("$.tutorId").value(m.tutor().accountId().toString()))
                    .andExpect(jsonPath("$.state").value("WAITING"))
                    .andExpect(jsonPath("$.revision").value(1))
                    // INV-17: the price and length the offer fixed.
                    .andExpect(jsonPath("$.priceVndSession").value(OfferFixture.PRICE_90_MINUTES))
                    .andExpect(jsonPath("$.durationMinutes").value(90))
                    .andExpect(jsonPath("$.effectiveGoalVersionId").value(m.created().effectiveGoalVersionId().toString()))
                    .andExpect(jsonPath("$.goal").value(m.created().goal()));
        }
    }

    @Test
    void AC_UC07_04_the_sessions_are_those_of_the_offer_in_time_order() throws Exception {
        Made m = aClass();
        UUID classId = m.created().id();
        List<String> offered = jdbc.sql("""
                select to_char(lower(during) at time zone 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"')
                from offer_sessions where offer_id = ? order by ordinal
                """).param(m.offer()).query(String.class).list();

        for (Actor party : List.of(m.parent(), m.tutor())) {
            String body = sessionsOf(party, classId, "").andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control", "private, no-store"))
                    .andExpect(jsonPath("$.items.length()").value(4))
                    .andExpect(jsonPath("$.nextCursor").isEmpty())
                    .andExpect(jsonPath("$.items[0].classId").value(classId.toString()))
                    .andExpect(jsonPath("$.items[*].ordinal").value(org.hamcrest.Matchers.contains(1, 2, 3, 4)))
                    .andExpect(jsonPath("$.items[*].state").value(org.hamcrest.Matchers.everyItem(
                            org.hamcrest.Matchers.is("SCHEDULED"))))
                    .andExpect(jsonPath("$.items[*].revision").value(org.hamcrest.Matchers.everyItem(
                            org.hamcrest.Matchers.is(1))))
                    .andReturn().getResponse().getContentAsString();
            List<String> starts = JsonPath.read(body, "$.items[*].during.start");
            List<String> ends = JsonPath.read(body, "$.items[*].during.end");
            assertThat(starts.stream().map(s -> OffsetDateTime.parse(s).toInstant()).toList())
                    .isEqualTo(offered.stream().map(s -> OffsetDateTime.parse(s).toInstant()).toList());
            // 90 minutes each, [start, end).
            for (int i = 0; i < starts.size(); i++) {
                assertThat(java.time.Duration.between(OffsetDateTime.parse(starts.get(i)),
                        OffsetDateTime.parse(ends.get(i)))).hasMinutes(90);
            }
        }
    }

    @Test
    void the_sessions_page_by_cursor_without_gaps_or_repeats() throws Exception {
        Made m = aClass();
        UUID classId = m.created().id();

        List<String> seen = new ArrayList<>();
        String cursor = null;
        int pages = 0;
        do {
            String query = "?limit=3" + (cursor == null ? "" : "&cursor=" + cursor);
            String body = sessionsOf(m.parent(), classId, query).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            seen.addAll(JsonPath.read(body, "$.items[*].id"));
            cursor = JsonPath.read(body, "$.nextCursor");
            pages++;
        } while (cursor != null);

        assertThat(pages).isEqualTo(2);
        assertThat(seen).doesNotHaveDuplicates().hasSize(4)
                .containsExactlyInAnyOrderElementsOf(jdbc.sql("select id::text from lesson_sessions where class_id = ?")
                        .param(classId).query(String.class).list());
        sessionsOf(m.parent(), classId, "?limit=0").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("LIMIT_INVALID"));
        sessionsOf(m.parent(), classId, "?limit=51").andExpect(status().isUnprocessableContent());
        sessionsOf(m.parent(), classId, "?cursor=not-a-cursor").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("CURSOR_INVALID"));
    }

    @Test
    void a_cursor_of_another_class_reaches_only_this_class() throws Exception {
        Made mine = aClass();
        Made other = aClass();
        String body = sessionsOf(other.parent(), other.created().id(), "?limit=1").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String foreignCursor = JsonPath.read(body, "$.nextCursor");

        String page = sessionsOf(mine.parent(), mine.created().id(), "?cursor=" + foreignCursor)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<String> classIds = JsonPath.read(page, "$.items[*].classId");
        assertThat(classIds).allMatch(id -> id.equals(mine.created().id().toString()));
    }

    // ---------------------------------------------------------------- AC-UC07-04, AC-UC02-02: nobody else

    @Test
    void AC_UC07_04_another_family_another_tutor_and_an_unknown_id_get_the_same_404() throws Exception {
        Made m = aClass();
        Made other = aClass();
        UUID classId = m.created().id();

        for (Actor stranger : List.of(other.parent(), other.tutor(), newActor(Actor.Role.PARENT),
                newActor(Actor.Role.TUTOR))) {
            getClass(stranger, classId).andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("CLASS_NOT_FOUND"));
            sessionsOf(stranger, classId, "").andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("CLASS_NOT_FOUND"));
        }
        getClass(m.parent(), UUID.randomUUID()).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CLASS_NOT_FOUND"));
        sessionsOf(m.tutor(), UUID.randomUUID(), "").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CLASS_NOT_FOUND"));
    }

    @Test
    void AC_UC06_03_the_tutor_whose_competing_offer_was_cancelled_does_not_see_the_class() throws Exception {
        Need need = fixture.need(fixture.student());
        Tutor chosen = fixture.tutor();
        Tutor other = fixture.tutor();
        UUID accepted = fixture.offer(need, chosen);
        fixture.offer(need, other);
        UUID classId = acceptOffer.accept(chosen.id(), accepted, 1, UUID.randomUUID().toString()).id();

        getClass(actor(other.id(), Actor.Role.TUTOR), classId).andExpect(status().isNotFound());
        sessionsOf(actor(other.id(), Actor.Role.TUTOR), classId, "").andExpect(status().isNotFound());
    }

    @Test
    void INV_01_another_role_is_403_and_no_session_is_401() throws Exception {
        Made m = aClass();
        UUID classId = m.created().id();

        getClass(newActor(Actor.Role.ADMIN), classId).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_UNAVAILABLE"));
        sessionsOf(newActor(Actor.Role.ADMIN), classId, "").andExpect(status().isForbidden());
        getClass(null, classId).andExpect(status().isUnauthorized());
        sessionsOf(null, classId, "").andExpect(status().isUnauthorized());
    }
}
