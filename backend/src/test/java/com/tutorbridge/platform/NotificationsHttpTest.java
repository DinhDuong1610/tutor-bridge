package com.tutorbridge.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.tutorbridge.Contract;
import com.tutorbridge.HttpTest;
import com.tutorbridge.platform.api.Actor;
import com.tutorbridge.platform.api.Notifications;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * listOwnNotifications and markNotificationRead over HTTP (UC15, TX30, TB-21). Real PostgreSQL, every answer
 * checked against the contract. Notifications are delivered directly here; OfferNotificationsTest covers
 * how events become them.
 */
class NotificationsHttpTest extends HttpTest {

    @Autowired
    Notifications inbox;

    /** One notification of a new event for {@code recipient}, made a little after the previous one. */
    private UUID deliver(Actor recipient, String eventType) {
        UUID event = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        assertThat(inbox.deliver(recipient.accountId(), event, eventType, "OFFER", target, "1")).isTrue();
        return jdbc.sql("select id from notifications where recipient_id = ? and event_id = ?")
                .params(recipient.accountId(), event).query(UUID.class).single();
    }

    private ResultActions list(Actor actor, String query) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/v1/me/notifications" + query);
        if (actor != null) {
            request.with(sessions().as(actor));
        }
        return mvc.perform(request).andExpect(Contract.matches());
    }

    private ResultActions read(Actor actor, UUID id, String key) throws Exception {
        MockHttpServletRequestBuilder request = post("/api/v1/notifications/" + id + "/read");
        if (actor != null) {
            request.with(sessions().as(actor));
        }
        if (key != null) {
            request.header("Idempotency-Key", key);
        }
        return mvc.perform(request).andExpect(Contract.matches());
    }

    private static String body(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString();
    }

    @Test
    void UC15_a_member_lists_their_own_notifications_newest_first_a_page_at_a_time() throws Exception {
        Actor tutor = newActor(Actor.Role.TUTOR);
        UUID first = deliver(tutor, "OFFER_SENT");
        UUID second = deliver(tutor, "OFFER_CANCELLED");
        deliver(newActor(Actor.Role.TUTOR), "OFFER_SENT");

        String page1 = body(list(tutor, "?limit=1").andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(second.toString()))
                .andExpect(jsonPath("$.items[0].eventType").value("OFFER_CANCELLED"))
                .andExpect(jsonPath("$.items[0].targetType").value("OFFER"))
                .andExpect(jsonPath("$.items[0].targetVersion").value("1"))
                .andExpect(jsonPath("$.items[0].read").value(false)));
        String cursor = JsonPath.read(page1, "$.nextCursor");
        String page2 = body(list(tutor, "?limit=1&cursor=" + cursor).andExpect(status().isOk()));

        assertThat(JsonPath.<List<String>>read(page2, "$.items[*].id")).containsExactly(first.toString());
        assertThat(JsonPath.<String>read(page2, "$.nextCursor")).isNull();
        assertThat(JsonPath.<List<String>>read(body(list(newActor(Actor.Role.PARENT), "")), "$.items")).isEmpty();
        list(null, "").andExpect(status().isUnauthorized());
        list(tutor, "?limit=51").andExpect(status().isUnprocessableContent());
    }

    @Test
    void TX30_marking_read_is_once_and_a_replay_answers_the_same() throws Exception {
        Actor parent = newActor(Actor.Role.PARENT);
        UUID id = deliver(parent, "OFFER_REJECTED");
        String key = UUID.randomUUID().toString();

        String first = body(read(parent, id, key).andExpect(status().isOk()).andExpect(jsonPath("$.read").value(true)));
        String readAt = jdbc.sql("select read_at::text from notifications where id = ?").param(id).query(String.class)
                .single();
        String again = body(read(parent, id, key).andExpect(status().isOk()));
        // Another key: still read, and the first time is kept.
        read(parent, id, UUID.randomUUID().toString()).andExpect(status().isOk()).andExpect(jsonPath("$.read").value(true));

        assertThat(again).isEqualTo(first);
        assertThat(jdbc.sql("select read_at::text from notifications where id = ?").param(id).query(String.class)
                .single()).isEqualTo(readAt);
        list(parent, "").andExpect(jsonPath("$.items[0].read").value(true));
    }

    @Test
    void FR_37_the_status_of_a_mark_read_names_the_notification_to_its_recipient_while_it_is_there()
            throws Exception {
        Actor parent = newActor(Actor.Role.PARENT);
        UUID id = deliver(parent, "OFFER_REJECTED");
        String key = UUID.randomUUID().toString();
        read(parent, id, key).andExpect(status().isOk());

        // TB-22's lookup by key, as the app asks after a lost answer (INV-24): the result is shown (INV-22).
        commandStatus(parent, key).andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("SUCCEEDED"))
                .andExpect(jsonPath("$.resultType").value("NOTIFICATION"))
                .andExpect(jsonPath("$.resultId").value(id.toString()));

        // Gone since (INV-26): the status stays, without the result.
        jdbc.sql("delete from notifications where id = ?").param(id).update();
        commandStatus(parent, key).andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("SUCCEEDED"))
                .andExpect(jsonPath("$.resultType").doesNotExist())
                .andExpect(jsonPath("$.resultId").doesNotExist());
    }

    private ResultActions commandStatus(Actor actor, String key) throws Exception {
        return mvc.perform(get("/api/v1/operations").param("idempotencyKey", key)
                .param("commandType", "MARK_NOTIFICATION_READ").with(sessions().as(actor)))
                .andExpect(Contract.matches());
    }

    @Test
    void AC_UC15_03_nobody_reads_or_marks_another_members_notification() throws Exception {
        Actor parent = newActor(Actor.Role.PARENT);
        UUID id = deliver(parent, "OFFER_REJECTED");

        read(newActor(Actor.Role.PARENT), id, UUID.randomUUID().toString())
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOTIFICATION_NOT_FOUND"));
        read(newActor(Actor.Role.TUTOR), id, UUID.randomUUID().toString()).andExpect(status().isNotFound());
        read(null, id, UUID.randomUUID().toString()).andExpect(status().isUnauthorized());
        read(parent, UUID.randomUUID(), UUID.randomUUID().toString()).andExpect(status().isNotFound());
        read(parent, id, null).andExpect(status().isBadRequest());

        assertThat(jdbc.sql("select read_at is null from notifications where id = ?").param(id).query(Boolean.class)
                .single()).isTrue();
    }

    @Autowired
    org.springframework.jdbc.core.simple.JdbcClient jdbcClient;

    @Test
    void a_notification_takes_its_id_from_the_id_generator_of_the_platform() {
        Actor tutor = newActor(Actor.Role.TUTOR);
        UUID fixed = UUID.randomUUID();
        Notifications withFixedIds = new Notifications(jdbcClient, () -> fixed);

        assertThat(withFixedIds.deliver(tutor.accountId(), UUID.randomUUID(), "OFFER_SENT", "OFFER", UUID.randomUUID(),
                "1")).isTrue();

        assertThat(jdbc.sql("select id from notifications where recipient_id = ?").param(tutor.accountId())
                .query(UUID.class).single()).isEqualTo(fixed);
    }

    @Test
    void AC_UC15_02_delivering_the_same_event_to_the_same_member_again_adds_nothing() {
        Actor tutor = newActor(Actor.Role.TUTOR);
        UUID event = UUID.randomUUID();
        UUID target = UUID.randomUUID();

        assertThat(inbox.deliver(tutor.accountId(), event, "OFFER_SENT", "OFFER", target, "1")).isTrue();
        assertThat(inbox.deliver(tutor.accountId(), event, "OFFER_SENT", "OFFER", target, "1")).isFalse();

        assertThat(jdbc.sql("select count(*) from notifications where recipient_id = ?").param(tutor.accountId())
                .query(Integer.class).single()).isOne();
    }
}
