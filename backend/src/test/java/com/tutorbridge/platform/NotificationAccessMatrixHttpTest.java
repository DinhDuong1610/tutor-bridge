package com.tutorbridge.platform;

import static com.tutorbridge.R0aScope.operationsOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.jayway.jsonpath.JsonPath;
import com.tutorbridge.Contract;
import com.tutorbridge.HttpTest;
import com.tutorbridge.platform.api.Actor;
import com.tutorbridge.platform.api.Actor.Role;
import com.tutorbridge.platform.api.Notifications;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * TB-23, Đính's part of the M7 acceptance, group Thông báo of `r0a-scope.md`: the access cases of both
 * notification operations, same shape as {@code classes.OfferAccessMatrixHttpTest}. The contract says
 * "Recipient only" and every role has an inbox, so there is no 403 row: each role is checked as an owner
 * against another member of the same role.
 * <ul>
 * <li>no session: 401, and the owner's notification unchanged;
 * <li>another member with the owner's id: the same 404 as an unknown id, whole problem compared, and the
 * owner's notification unchanged (INV-06, AC-UC15-03);
 * <li>another member's list is a real, empty list holding no id of the owner's;
 * <li>the owner is answered, and their list holds the notification.
 * </ul>
 * Real PostgreSQL, every answer checked against the contract. Fake accounts only.
 */
class NotificationAccessMatrixHttpTest extends HttpTest {

    @Autowired
    Notifications inbox;

    /** One member and one unread notification of theirs. */
    private record Owned(Actor owner, UUID notification, UUID target) {}

    /**
     * One operation. {@code request} builds the call for a path id (the owner's or an unknown one); {@code list}
     * marks an own-data list, checked for leaked ids instead of a path id.
     */
    private record Op(String id, Function<UUID, MockHttpServletRequestBuilder> request, boolean list) {}

    private static List<Op> ops() {
        return List.of(
                new Op("listOwnNotifications", ignored -> get("/api/v1/me/notifications"), true),
                // A new key each call, so no replay answers for the first one.
                new Op("markNotificationRead", id -> post("/api/v1/notifications/" + id + "/read")
                        .header("Idempotency-Key", UUID.randomUUID().toString()), false));
    }

    @TestFactory
    Stream<DynamicNode> every_notification_operation_answers_its_access_cases() {
        return ops().stream().map(op -> DynamicContainer.dynamicContainer(op.id(), cases(op)));
    }

    private Stream<DynamicNode> cases(Op op) {
        List<DynamicNode> cases = new ArrayList<>();
        cases.add(DynamicTest.dynamicTest("no session: 401", () -> {
            Owned owned = owned(Role.PARENT);
            String before = fingerprint(owned);
            MockHttpServletResponse answer = send(op.request().apply(owned.notification()), null);
            assertThat(answer.getStatus()).as(op.id()).isEqualTo(401);
            assertThat(fingerprint(owned)).as(op.id() + ": the owner's data is unchanged").isEqualTo(before);
        }));
        for (Role role : Role.values()) {
            if (op.list()) {
                cases.add(DynamicTest.dynamicTest("another " + role + "'s list holds none of the owner's ids", () -> {
                    Owned owned = owned(role);
                    MockHttpServletResponse answer = send(op.request().apply(null), newActor(role));
                    String body = answer.getContentAsString();
                    // A real list first: an error body holds no id either (Codex on #250).
                    assertThat(answer.getStatus()).as(op.id() + " as another " + role + ": " + body).isEqualTo(200);
                    List<Object> items = JsonPath.read(body, "$.items");
                    assertThat(items).as(op.id() + ": the other member has no notification").isEmpty();
                    assertThat(body).doesNotContain(owned.notification().toString())
                            .doesNotContain(owned.target().toString());
                }));
                continue;
            }
            cases.add(DynamicTest.dynamicTest("another " + role + " with the owner's id: same 404 as an unknown id",
                    () -> {
                Owned owned = owned(role);
                String before = fingerprint(owned);
                Actor other = newActor(role);
                MockHttpServletResponse theirs = send(op.request().apply(owned.notification()), other);
                MockHttpServletResponse none = send(op.request().apply(UUID.randomUUID()), other);

                assertThat(theirs.getStatus()).as(op.id() + ": " + theirs.getContentAsString()).isEqualTo(404);
                // The whole problem, not only its code: nothing in it may tell the id exists (INV-06).
                assertThat(problem(theirs)).as(op.id()).isEqualTo(problem(none));
                assertThat(fingerprint(owned)).as(op.id() + ": the owner's data is unchanged").isEqualTo(before);
            }));
        }
        return cases.stream();
    }

    /** The owners themselves get through, so the refusals above are about the caller, not a broken request. */
    @TestFactory
    Stream<DynamicNode> the_owners_themselves_are_answered() {
        return ops().stream().map(op -> DynamicContainer.dynamicContainer(op.id(), Stream.of(Role.values())
                .map(role -> DynamicTest.dynamicTest("as the owning " + role, () -> {
                    Owned owned = owned(role);
                    MockHttpServletResponse answer = send(op.request().apply(owned.notification()), owned.owner());
                    assertThat(answer.getStatus()).as(op.id() + ": " + answer.getContentAsString()).isEqualTo(200);
                    if (op.list()) {
                        List<String> ids = JsonPath.read(answer.getContentAsString(), "$.items[*].id");
                        assertThat(ids).as(op.id() + " as the owning " + role)
                                .containsExactly(owned.notification().toString());
                    } else {
                        assertThat(fingerprint(owned)).as(op.id() + " as the owning " + role).startsWith("read");
                    }
                }))));
    }

    @Test
    void the_matrix_has_a_row_for_every_notification_operation_in_r0a_scope() throws Exception {
        Set<String> rows = new TreeSet<>();
        for (Op op : ops()) {
            assertThat(rows.add(op.id())).as("one row per operation: " + op.id()).isTrue();
        }
        Set<String> scoped = operationsOf("Thông báo");
        assertThat(scoped).as("Thông báo as r0a-scope.md lists it").hasSize(2);
        assertThat(rows).isEqualTo(new TreeSet<>(scoped));
    }

    // ---------------------------------------------------------------- owners and their notifications

    private Owned owned(Role role) {
        Actor owner = newActor(role);
        UUID event = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        assertThat(inbox.deliver(owner.accountId(), event, "OFFER_REJECTED", "OFFER", target, "1")).isTrue();
        UUID id = jdbc.sql("select id from notifications where recipient_id = ? and event_id = ?")
                .params(owner.accountId(), event).query(UUID.class).single();
        return new Owned(owner, id, target);
    }

    /** What a refused request must leave as it was: whether the notification is read, and the owner's inbox size. */
    private String fingerprint(Owned owned) {
        return jdbc.sql("select case when read_at is null then 'unread' else 'read' end from notifications where id = ?")
                .param(owned.notification()).query(String.class).single()
                + " | " + jdbc.sql("select count(*) from notifications where recipient_id = ?")
                        .param(owned.owner().accountId()).query(Long.class).single();
    }

    private MockHttpServletResponse send(MockHttpServletRequestBuilder request, Actor actor) throws Exception {
        if (actor != null) {
            request.with(sessions().as(actor));
        }
        return mvc.perform(request).andExpect(Contract.matches()).andReturn().getResponse();
    }

    private static String problem(MockHttpServletResponse answer) throws Exception {
        return answer.getStatus() + " " + answer.getContentAsString().replaceAll("\"traceId\":\"[^\"]*\",?", "");
    }
}
