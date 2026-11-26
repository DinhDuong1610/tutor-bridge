package com.tutorbridge.classes;

import static com.tutorbridge.classes.OfferHttpFixture.id;
import static com.tutorbridge.classes.OfferHttpFixture.input;
import static com.tutorbridge.classes.OfferHttpFixture.reason;
import static com.tutorbridge.R0aScope.operationsOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.tutorbridge.Contract;
import com.tutorbridge.HttpTest;
import com.tutorbridge.classes.OfferHttpFixture.Scenario;
import com.tutorbridge.platform.api.Actor;
import com.tutorbridge.platform.api.Actor.Role;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * TB-23, Đính's part of the M7 acceptance, group Đề nghị of `r0a-scope.md`: the access cases of every offer
 * operation, on offers made through the real API (createMatchingRun, sendOffer). For each row:
 * <ul>
 * <li>no session: 401;
 * <li>each role the contract's {@code x-authorization} leaves out: 403, and the owner's data unchanged;
 * <li>another member of an allowed role with the owner's id: the same 404 as an unknown id, whole problem
 * compared, and the owner's data unchanged (INV-06, AC-UC02-02);
 * <li>a list read by another member holds no id of the owner's.
 * </ul>
 * An answer that differs from this is reported, not bent to fit (`docs/handoff/TB-23-dinh.md`). Huy's
 * groups are in {@code m7.AuthorizationMatrixHttpTest} (#242). Real PostgreSQL, every answer checked
 * against the contract. Fake accounts only.
 */
class OfferAccessMatrixHttpTest extends HttpTest {

    /** Not on {@code dev} yet; each gets its row when its PR merges. */
    private static final Map<String, String> PENDING = Map.of("acceptOffer", "#238 (TB-19)");

    @Autowired
    TransactionTemplate tx;

    private OfferHttpFixture fx;

    @BeforeEach
    void fixture() {
        fx = new OfferHttpFixture(mvc, jdbc, tx, a -> sessions().as(a), this::newActor);
    }

    /** The owner's side of one offer: the parent's need and the PENDING offer sent to the tutor. */
    private record Owned(Scenario scenario, UUID offer) {
        Actor parent() {
            return scenario.parent();
        }

        Actor tutor() {
            return scenario.candidate().tutor().actor();
        }
    }

    @FunctionalInterface
    private interface Setup {
        Owned make() throws Exception;
    }

    /**
     * One operation. {@code allowed}: the roles of {@code x-authorization}. {@code owners}: the allowed roles
     * whose member owns the resource, each checked with another member of that role. {@code request} builds
     * the call for a path id (the owner's or an unknown one). {@code list}: an own-data list, checked for
     * leaked ids instead of a path id.
     */
    private record Op(String id, Set<Role> allowed, Set<Role> owners,
            Function<Owned, Function<UUID, MockHttpServletRequestBuilder>> request,
            Function<Owned, UUID> pathId, boolean list) {}

    // ---------------------------------------------------------------- the matrix

    private List<Op> ops() {
        Set<Role> parent = EnumSet.of(Role.PARENT);
        Set<Role> tutor = EnumSet.of(Role.TUTOR);
        Set<Role> both = EnumSet.of(Role.PARENT, Role.TUTOR);
        List<Op> ops = new ArrayList<>();
        // "PARENT need owner". A new key each call, so no replay answers for the first one.
        ops.add(new Op("sendOffer", parent, parent,
                o -> need -> keyed(json(post("/api/v1/needs/" + need + "/offers"),
                        input(o.scenario().need(), o.scenario().candidate()))).header("If-Match", "\"2\""),
                o -> o.scenario().need().id(), false));
        // "PARENT own children or TUTOR recipient".
        ops.add(new Op("listOffers", both, both, o -> ignored -> get("/api/v1/me/offers"), o -> null, true));
        // "PARENT owner or TUTOR recipient".
        ops.add(new Op("getOffer", both, both, o -> offer -> get("/api/v1/offers/" + offer), Owned::offer, false));
        // "TUTOR recipient".
        ops.add(new Op("rejectOffer", tutor, tutor,
                o -> offer -> keyed(json(post("/api/v1/offers/" + offer + "/reject"), reason("NOT_AVAILABLE")))
                        .header("If-Match", "\"1\""),
                Owned::offer, false));
        // "PARENT owner".
        ops.add(new Op("cancelOffer", parent, parent,
                o -> offer -> keyed(json(post("/api/v1/offers/" + offer + "/cancel"), reason("PARENT_CANCELLED")))
                        .header("If-Match", "\"1\""),
                Owned::offer, false));
        return ops;
    }

    @TestFactory
    Stream<DynamicNode> every_offer_operation_answers_its_access_cases() {
        return ops().stream().map(op -> DynamicContainer.dynamicContainer(op.id(), cases(op)));
    }

    private Stream<DynamicNode> cases(Op op) {
        List<DynamicNode> cases = new ArrayList<>();
        cases.add(DynamicTest.dynamicTest("no session: 401", () -> {
            Owned owned = owned();
            String before = fingerprint(owned);
            MockHttpServletResponse answer = send(op.request().apply(owned).apply(op.pathId().apply(owned)), null);
            assertThat(answer.getStatus()).as(op.id()).isEqualTo(401);
            assertThat(fingerprint(owned)).as(op.id() + ": the owner's data is unchanged").isEqualTo(before);
        }));
        for (Role role : EnumSet.complementOf(EnumSet.copyOf(op.allowed()))) {
            cases.add(DynamicTest.dynamicTest("role " + role + ": 403", () -> {
                Owned owned = owned();
                String before = fingerprint(owned);
                MockHttpServletResponse answer = send(op.request().apply(owned).apply(op.pathId().apply(owned)),
                        newActor(role));
                assertThat(answer.getStatus()).as(op.id() + " as " + role + ": " + answer.getContentAsString())
                        .isEqualTo(403);
                assertThat(fingerprint(owned)).as(op.id() + ": the owner's data is unchanged").isEqualTo(before);
            }));
        }
        for (Role role : op.owners()) {
            if (op.list()) {
                cases.add(DynamicTest.dynamicTest("another " + role + "'s list holds none of the owner's ids", () -> {
                    Owned owned = owned();
                    MockHttpServletResponse answer = send(op.request().apply(owned).apply(null), newActor(role));
                    String body = answer.getContentAsString();
                    // A real list first (Codex on #250): an error body holds no id either, so without this
                    // a 403 or a 500 to a caller who may read their own list would pass.
                    assertThat(answer.getStatus()).as(op.id() + " as another " + role + ": " + body).isEqualTo(200);
                    List<Object> items = JsonPath.read(body, "$.items");
                    assertThat(items).as(op.id() + ": the other member has no offer of their own").isEmpty();
                    for (UUID id : ids(owned)) {
                        assertThat(body).as(op.id() + " as another " + role).doesNotContain(id.toString());
                    }
                }));
                continue;
            }
            cases.add(DynamicTest.dynamicTest("another " + role + " with the owner's id: same 404 as an unknown id",
                    () -> {
                Owned owned = owned();
                String before = fingerprint(owned);
                Actor other = newActor(role);
                MockHttpServletResponse theirs = send(op.request().apply(owned).apply(op.pathId().apply(owned)),
                        other);
                MockHttpServletResponse none = send(op.request().apply(owned).apply(UUID.randomUUID()), other);

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
        return ops().stream().map(op -> DynamicContainer.dynamicContainer(op.id(), op.owners().stream()
                .map(role -> DynamicTest.dynamicTest("as the owning " + role, () -> {
                    Owned owned = owned();
                    Actor self = role == Role.PARENT ? owned.parent() : owned.tutor();
                    // sendOffer: the pair already has a PENDING offer, so the owner's own call is refused on
                    // the offer, 409, after the access checks; every other row answers 2xx.
                    MockHttpServletResponse answer = send(op.request().apply(owned).apply(op.pathId().apply(owned)),
                            self);
                    int status = answer.getStatus();
                    if (op.id().equals("sendOffer")) {
                        assertThat(status).as(op.id()).isEqualTo(409);
                    } else {
                        assertThat(status).as(op.id()).isBetween(200, 299);
                    }
                    if (op.list()) {
                        // The list has the owner's offer, so its emptiness for another member above means
                        // something (Codex on #250).
                        List<String> offers = JsonPath.read(answer.getContentAsString(), "$.items[*].id");
                        assertThat(offers).as(op.id() + " as the owning " + role).contains(owned.offer().toString());
                    }
                }))));
    }

    // ---------------------------------------------------------------- the matrix covers the group

    @Test
    void the_matrix_has_a_row_for_every_offer_operation_in_r0a_scope() throws Exception {
        Set<String> rows = new TreeSet<>();
        for (Op op : ops()) {
            assertThat(rows.add(op.id())).as("one row per operation: " + op.id()).isTrue();
        }
        Set<String> scoped = operationsOf("Đề nghị");
        assertThat(scoped).as("Đề nghị as r0a-scope.md lists it").hasSize(6);
        Set<String> covered = new TreeSet<>(rows);
        covered.addAll(PENDING.keySet());
        assertThat(covered).isEqualTo(new TreeSet<>(scoped));
        assertThat(rows).doesNotContainAnyElementsOf(PENDING.keySet());
    }


    // ---------------------------------------------------------------- owners and their resources

    /** A new parent, need, tutor and run, and a PENDING offer sent over HTTP. */
    private Owned owned() throws Exception {
        Scenario s = fx.scenario();
        return new Owned(s, id(fx.send(s).andExpect(status().isCreated())));
    }

    private static List<UUID> ids(Owned owned) {
        Scenario s = owned.scenario();
        return List.of(owned.offer(), s.need().id(), s.need().versionId(), s.need().studentId(),
                s.candidate().runId(), s.candidate().tutor().id(), s.candidate().tutor().profileId());
    }

    /** What a refused request must leave as it was: the offer, the need, and how many offers and runs hang off it. */
    private String fingerprint(Owned owned) {
        UUID need = owned.scenario().need().id();
        return String.join(" | ",
                jdbc.sql("select state || '/' || revision || '/' || coalesce(reason_code, '-') from class_offers where id = ?")
                        .param(owned.offer()).query(String.class).single(),
                jdbc.sql("select status || '/' || revision from learning_needs where id = ?")
                        .param(need).query(String.class).single(),
                String.valueOf(fx.count("select count(*) from class_offers where need_id = ?", need)),
                String.valueOf(fx.count("select count(*) from classes where originating_need_id = ?", need)),
                String.valueOf(fx.count("select count(*) from outbox_events where aggregate_id = ?", owned.offer())));
    }

    // ---------------------------------------------------------------- requests

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static MockHttpServletRequestBuilder keyed(MockHttpServletRequestBuilder request) {
        return request.header("Idempotency-Key", UUID.randomUUID().toString());
    }

    private MockHttpServletResponse send(MockHttpServletRequestBuilder request, Actor actor) throws Exception {
        if (actor != null) {
            request.with(sessions().as(actor));
        }
        return mvc.perform(request).andExpect(Contract.matches()).andReturn().getResponse();
    }

    /** A problem as the client reads it, without its traceId, which differs on every answer. */
    private static String problem(MockHttpServletResponse answer) throws Exception {
        return answer.getStatus() + " " + answer.getContentAsString().replaceAll("\"traceId\":\"[^\"]*\",?", "");
    }
}
