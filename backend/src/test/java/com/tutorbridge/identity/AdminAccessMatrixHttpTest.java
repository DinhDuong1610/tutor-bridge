package com.tutorbridge.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.jayway.jsonpath.JsonPath;
import com.tutorbridge.Contract;
import com.tutorbridge.HttpTest;
import com.tutorbridge.platform.api.Actor;
import com.tutorbridge.platform.api.Actor.Role;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * TB-23, Đính's part of the M7 acceptance, group Quản trị xác minh of `r0a-scope.md`: the access cases of
 * the admin sign-in and of the verifier's queue (TB-13). For the verifier's operations:
 * <ul>
 * <li>no session: 401;
 * <li>PARENT, TUTOR and STUDENT: 403; an ADMIN without VERIFY: 403 too, before any record is read;
 * <li>another verifier with the owner's record (and file): the same 404 as an unknown id, whole problem
 * compared, and the record unchanged (INV-06, OP-28);
 * <li>another verifier's queue holds no id of the owner's.
 * </ul>
 * The sign-in operations are public: loginAdmin needs no session, and completeAdminMfa answers an unknown
 * challenge the same with or without a session, so a session never stands in for the first factor.
 * getAdminSession is ADMIN only. Answers that differ from this are reported, not bent to fit
 * (`docs/handoff/TB-23-dinh.md`). Real PostgreSQL, every answer checked against the contract. Fake accounts
 * only.
 */
class AdminAccessMatrixHttpTest extends HttpTest {

    private static final String QUEUE = "/api/v1/admin/verifications";
    private static final String DESCRIPTION = "Dạy đại số lớp 8 theo từng bước, có bài tập về nhà mỗi buổi học.";

    /** The verifier's record: a PENDING version of a tutor with one READY file, assigned at revision 1. */
    private record Owned(Actor verifier, UUID tutor, UUID profile, UUID verification, UUID upload) {}

    /**
     * One verifier operation: {@code request} builds the call for a record id and a file id (the owner's or
     * unknown ones). {@code list}: the queue, checked for leaked ids instead of a path id.
     */
    private record Op(String id, Function<Owned, Function<UUID[], MockHttpServletRequestBuilder>> request,
            boolean list) {}

    private List<Op> verifierOps() {
        List<Op> ops = new ArrayList<>();
        ops.add(new Op("listAssignedVerifications", o -> ids -> get(QUEUE), true));
        ops.add(new Op("getAssignedVerification", o -> ids -> get(QUEUE + "/" + ids[0]), false));
        ops.add(new Op("decideVerification", o -> ids -> post(QUEUE + "/" + ids[0] + "/decision")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", UUID.randomUUID().toString()).header("If-Match", "\"1\"")
                .content("""
                        {"profileVersionId":"%s","result":"VERIFIED","scope":"Toán 8, đại số",\
                        "reason":"Bằng cử nhân sư phạm toán."}""".formatted(o.profile())), false));
        ops.add(new Op("readAssignedEvidence", o -> ids -> get(QUEUE + "/" + ids[0] + "/evidence/" + ids[1]), false));
        return ops;
    }

    /** The operations that are not the verifier's, each checked by its own test below. */
    private static final Set<String> SIGN_IN = Set.of("loginAdmin", "completeAdminMfa", "getAdminSession");

    // ---------------------------------------------------------------- the verifier's operations

    @TestFactory
    Stream<DynamicNode> every_verifier_operation_answers_its_access_cases() {
        return verifierOps().stream().map(op -> DynamicContainer.dynamicContainer(op.id(), cases(op)));
    }

    private Stream<DynamicNode> cases(Op op) {
        List<DynamicNode> cases = new ArrayList<>();
        cases.add(DynamicTest.dynamicTest("no session: 401", () -> {
            Owned owned = owned();
            String before = fingerprint(owned);
            MockHttpServletResponse answer = send(own(op, owned), null);
            assertThat(answer.getStatus()).as(op.id()).isEqualTo(401);
            assertThat(fingerprint(owned)).as(op.id() + ": the record is unchanged").isEqualTo(before);
        }));
        for (Role role : EnumSet.of(Role.PARENT, Role.TUTOR, Role.STUDENT)) {
            cases.add(DynamicTest.dynamicTest("role " + role + ": 403", () -> refused(op, newActor(role), role.name())));
        }
        cases.add(DynamicTest.dynamicTest("an ADMIN without VERIFY: 403", () -> {
            Actor support = newActor(Role.ADMIN);
            jdbc.sql("insert into admin_assignments(account_id, capability, granted_by) values (?, 'SUPPORT', ?)")
                    .params(support.accountId(), support.accountId()).update();
            refused(op, support, "ADMIN without VERIFY");
        }));
        if (op.list()) {
            cases.add(DynamicTest.dynamicTest("another verifier's queue holds none of the owner's ids", () -> {
                Owned owned = owned();
                MockHttpServletResponse answer = send(own(op, owned), verifier());
                String body = answer.getContentAsString();
                // A real queue first (Codex on #250): an error body holds no id either, so without this a 403
                // or a 500 to a verifier who may read their own queue would pass.
                assertThat(answer.getStatus()).as(op.id() + ": " + body).isEqualTo(200);
                List<Object> items = JsonPath.read(body, "$.items");
                assertThat(items).as(op.id() + ": the other verifier has no record of their own").isEmpty();
                for (UUID id : List.of(owned.verification(), owned.profile(), owned.upload(), owned.tutor())) {
                    assertThat(body).as(op.id()).doesNotContain(id.toString());
                }
            }));
        } else {
            cases.add(DynamicTest.dynamicTest("another verifier with the owner's record: same 404 as an unknown id",
                    () -> {
                Owned owned = owned();
                String before = fingerprint(owned);
                Actor other = verifier();
                MockHttpServletResponse theirs = send(own(op, owned), other);
                MockHttpServletResponse none = send(op.request().apply(owned)
                        .apply(new UUID[] {UUID.randomUUID(), UUID.randomUUID()}), other);

                assertThat(theirs.getStatus()).as(op.id() + ": " + theirs.getContentAsString()).isEqualTo(404);
                // The whole problem, not only its code: nothing in it may tell the record exists (INV-06).
                assertThat(problem(theirs)).as(op.id()).isEqualTo(problem(none));
                assertThat(fingerprint(owned)).as(op.id() + ": the record is unchanged").isEqualTo(before);
            }));
        }
        return cases.stream();
    }

    private void refused(Op op, Actor caller, String who) throws Exception {
        Owned owned = owned();
        String before = fingerprint(owned);
        MockHttpServletResponse answer = send(own(op, owned), caller);
        assertThat(answer.getStatus()).as(op.id() + " as " + who + ": " + answer.getContentAsString()).isEqualTo(403);
        assertThat(fingerprint(owned)).as(op.id() + ": the record is unchanged").isEqualTo(before);
    }

    /** The assigned verifier gets through, so the refusals above are about the caller, not the request. */
    @TestFactory
    Stream<DynamicNode> the_assigned_verifier_is_answered() {
        return verifierOps().stream().map(op -> DynamicTest.dynamicTest(op.id(), () -> {
            Owned owned = owned();
            MockHttpServletResponse answer = send(own(op, owned), owned.verifier());
            assertThat(answer.getStatus()).as(op.id()).isBetween(200, 299);
            if (op.list()) {
                // The queue has the owner's record, so its emptiness for another verifier means something.
                List<String> records = JsonPath.read(answer.getContentAsString(), "$.items[*].id");
                assertThat(records).as(op.id()).containsExactly(owned.verification().toString());
            }
        }));
    }

    // ---------------------------------------------------------------- sign-in and the admin session

    @Test
    void loginAdmin_is_public_no_session_is_not_401_for_lack_of_one() throws Exception {
        MockHttpServletResponse answer = send(signIn(post("/api/v1/auth/admin-login"), "{}"), null);
        assertThat(answer.getStatus()).as(answer.getContentAsString()).isEqualTo(422);
    }

    @Test
    void completeAdminMfa_answers_an_unknown_challenge_alike_with_or_without_a_session() throws Exception {
        String body = "{\"challengeToken\":\"%s\",\"code\":\"123456\"}".formatted("x".repeat(43));
        MockHttpServletResponse anonymous = send(signIn(post("/api/v1/auth/admin-mfa"), body), null);
        assertThat(anonymous.getStatus()).as(anonymous.getContentAsString()).isEqualTo(401);
        for (Role role : Role.values()) {
            MockHttpServletResponse signedIn = send(signIn(post("/api/v1/auth/admin-mfa"), body), newActor(role));
            assertThat(problem(signedIn)).as("a " + role + " session").isEqualTo(problem(anonymous));
        }
    }

    @Test
    void getAdminSession_is_401_without_a_session_403_for_other_roles_and_answers_an_admin() throws Exception {
        assertThat(send(get("/api/v1/admin/session"), null).getStatus()).isEqualTo(401);
        for (Role role : EnumSet.of(Role.PARENT, Role.TUTOR, Role.STUDENT)) {
            MockHttpServletResponse answer = send(get("/api/v1/admin/session"), newActor(role));
            assertThat(answer.getStatus()).as(role + ": " + answer.getContentAsString()).isEqualTo(403);
        }
        // An admin session exists only after MFA (loginAccount refuses ADMIN), and getAdminSession reads that
        // session's row: the row the MFA step writes stands in for it here.
        Actor admin = verifier();
        jdbc.sql("""
                insert into auth_sessions(id, account_id, token_digest, expires_at, admin_mfa_verified_at)
                values (?, ?, ?, now() + interval '1 hour', now())
                """).params(admin.sessionId(), admin.accountId(), UUID.randomUUID().toString()).update();
        assertThat(send(get("/api/v1/admin/session"), admin).getStatus()).isEqualTo(200);
    }

    // ---------------------------------------------------------------- the matrix covers the group

    @Test
    void the_matrix_has_a_row_for_every_admin_operation_in_r0a_scope() throws Exception {
        Set<String> rows = new TreeSet<>(SIGN_IN);
        for (Op op : verifierOps()) {
            assertThat(rows.add(op.id())).as("one row per operation: " + op.id()).isTrue();
        }
        Set<String> scoped = operationsOf("Quản trị xác minh");
        assertThat(scoped).as("Quản trị xác minh as r0a-scope.md lists it").hasSize(7);
        assertThat(rows).isEqualTo(new TreeSet<>(scoped));
    }

    /** The operationIds of one group in the API table of `r0a-scope.md`. */
    private static Set<String> operationsOf(String group) throws Exception {
        Set<String> ids = new LinkedHashSet<>();
        Pattern row = Pattern.compile("^\\| ([^|]+) \\| \\d+ \\| (.+) \\|$");
        Pattern id = Pattern.compile("`(\\w+)`");
        for (String line : Files.readAllLines(Path.of("../docs/context/r0a-scope.md"))) {
            Matcher m = row.matcher(line);
            if (m.matches() && m.group(1).trim().equals(group)) {
                Matcher each = id.matcher(m.group(2));
                while (each.find()) {
                    ids.add(each.group(1));
                }
            }
        }
        return ids;
    }

    // ---------------------------------------------------------------- owners and their records

    private Actor verifier() {
        Actor admin = newActor(Role.ADMIN);
        jdbc.sql("insert into admin_assignments(account_id, capability, granted_by) values (?, 'VERIFY', ?)")
                .params(admin.accountId(), admin.accountId()).update();
        return admin;
    }

    /** A tutor's PENDING version with one READY file (bytes in the test ObjectStore), assigned to a verifier. */
    private Owned owned() throws Exception {
        Actor verifier = verifier();
        Actor tutor = newActor(Role.TUTOR);
        UUID profile = UUID.randomUUID();
        jdbc.sql("""
                insert into tutor_profiles(id, tutor_id, version, status, description, experience_months, rate_vnd_60,
                                           revision)
                values (?, ?, 1, 'DRAFT', ?, 12, 180000, 2)
                """).params(profile, tutor.accountId(), DESCRIPTION).update();
        UUID upload = UUID.randomUUID();
        String key = UUID.randomUUID().toString();
        byte[] bytes = ("%PDF-1.4 minh chung " + upload).getBytes(StandardCharsets.US_ASCII);
        jdbc.sql("""
                insert into tutor_evidence_uploads(id, owner_id, object_key, media_type, size_bytes, sha256, state,
                                                   expires_at)
                values (?, ?, ?, 'application/pdf', ?, ?, 'READY', now() + interval '15 minutes')
                """).params(upload, tutor.accountId(), key, bytes.length, "c".repeat(64)).update();
        jdbc.sql("insert into tutor_profile_evidence(profile_id, upload_id) values (?, ?)").params(profile, upload)
                .update();
        jdbc.sql("update tutor_profiles set status = 'PENDING' where id = ?").param(profile).update();
        Path file = evidenceDir().resolve("objects").resolve(key);
        Files.createDirectories(file.getParent());
        Files.write(file, bytes);
        UUID verification = UUID.randomUUID();
        jdbc.sql("""
                insert into tutor_verifications(id, profile_id, scope, status, evidence_key, assigned_to, submitted_at,
                                                revision)
                values (?, ?, 'MATH8_ALGEBRA', 'PENDING', ?, ?, now(), 1)
                """).params(verification, profile, "0".repeat(64), verifier.accountId()).update();
        return new Owned(verifier, tutor.accountId(), profile, verification, upload);
    }

    private static MockHttpServletRequestBuilder own(Op op, Owned owned) {
        return op.request().apply(owned).apply(new UUID[] {owned.verification(), owned.upload()});
    }

    /** What a refused request must leave as it was: the record, the version, and the decision's traces. */
    private String fingerprint(Owned owned) {
        return String.join(" | ",
                jdbc.sql("""
                        select concat_ws('/', status, coalesce(reason, '-'), coalesce(reviewed_by::text, '-'), revision)
                        from tutor_verifications where id = ?""").param(owned.verification()).query(String.class).single(),
                jdbc.sql("select concat_ws('/', status, current::text, revision) from tutor_profiles where id = ?")
                        .param(owned.profile()).query(String.class).single(),
                jdbc.sql("select count(*)::text from audit_events where object_id = ?").param(owned.verification())
                        .query(String.class).single());
    }

    // ---------------------------------------------------------------- requests

    /** Each sign-in from its own address, so the per-source limits of the other tests are not touched. */
    private static MockHttpServletRequestBuilder signIn(MockHttpServletRequestBuilder request, String body) {
        return request.with(r -> {
            r.setRemoteAddr("10.23." + ThreadLocalRandom.current().nextInt(256) + "."
                    + ThreadLocalRandom.current().nextInt(256));
            return r;
        }).contentType(MediaType.APPLICATION_JSON).content(body);
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
