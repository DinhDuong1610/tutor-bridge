package com.tutorbridge.m7;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.jayway.jsonpath.JsonPath;
import com.tutorbridge.Contract;
import com.tutorbridge.HttpTest;
import com.tutorbridge.platform.api.Actor;
import com.tutorbridge.platform.api.Actor.Role;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
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
 * TB-23, Huy's part of the M7 acceptance: the access cases of every operation of Lát 1 in Huy's API groups
 * (`r0a-scope.md`; Huy took Lịch, Nhu cầu and Trạng thái lệnh on 25/11/2026). One row per operation, and
 * for each row:
 * <ul>
 * <li>no session: 401, except a public operation, which must not ask for one;
 * <li>each role the contract's {@code x-authorization} leaves out: 403, or the same 404 as an unknown id
 * where a confirmed decision hides the object from that role too ({@link #WRONG_ROLE_404});
 * <li>another member of the owner's role with the owner's id answers exactly as with an unknown id (404,
 * same code), so nothing tells them the id exists (INV-06, AC-UC02-02);
 * <li>an own-data read ({@code /me}, a list) shown to another member holds no id of the owner's.
 * </ul>
 * The answers recorded here are what the contract and the earlier decisions say. A row that answers
 * otherwise is reported, not bent to fit (`docs/handoff/TB-23.md`).
 *
 * <p>Real PostgreSQL, every answer checked against the contract. Fake accounts only.
 */
class AuthorizationMatrixHttpTest extends HttpTest {

    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final String DESCRIPTION = "Dạy đại số lớp 8 theo từng bước, có bài tập về nhà mỗi buổi học.";
    private static final String GOAL = "Nắm chắc phương trình bậc nhất một ẩn trước kỳ kiểm tra giữa kỳ.";
    private static final String SHA = "a".repeat(64);

    /** Huy's groups of `r0a-scope.md` (Huy on Notion; Lịch, Nhu cầu, Trạng thái lệnh: Huy, 25/11/2026). */
    private static final List<String> HUY_GROUPS = List.of("Xác thực", "Học sinh", "Hồ sơ gia sư", "Lịch",
            "Nhu cầu", "Gợi ý", "Lớp", "Trạng thái lệnh");

    /** Not on `dev` yet; each gets its row when its PR merges (`docs/handoff/TB-23.md`). */
    private static final Map<String, String> PENDING = Map.of(
            "getClass", "#240 (TB-20)",
            "listSessions", "#240 (TB-20)",
            "getCommandStatus", "#222 (TB-22)",
            "findCommandStatus", "#222 (TB-22)");

    /**
     * Rows where a role outside the contract's gets 404, not 403, because the decision hides whether the
     * object exists from them too. A child profile: TUTOR, ADMIN and STUDENT get the same 404
     * STUDENT_NOT_FOUND as an unknown id (#114, TB-11, confirmed by Đính on review; STUDENT is P1, Q1 of
     * 11/11/2026).
     */
    private static final Map<String, String> WRONG_ROLE_404 = Map.of(
            "getStudentProfile", "#114",
            "patchStudent", "#114");

    private enum Access { PUBLIC, ANY, ROLES }

    @FunctionalInterface
    private interface Setup {
        /** Makes the owner's resources; returns the names the request uses ("owner" is always there). */
        Map<String, String> make(Actor owner) throws Exception;
    }

    @FunctionalInterface
    private interface Request {
        MockHttpServletRequestBuilder build(Map<String, String> vars);
    }

    /**
     * One operation. {@code idName} names the path id of the owner's resource, or is null when the path has
     * none. {@code leak}: the answer to another member must hold none of the owner's ids.
     */
    private record Op(String id, Access access, Set<Role> allowed, Setup setup, Request request, String idName,
            boolean leak) {}

    // ---------------------------------------------------------------- the matrix

    private List<Op> ops() {
        Set<Role> parent = EnumSet.of(Role.PARENT);
        Set<Role> tutor = EnumSet.of(Role.TUTOR);
        // The contract lets a currently linked student read the list and the profile (P1, outside Lát 1).
        Set<Role> parentOrStudent = EnumSet.of(Role.PARENT, Role.STUDENT);
        List<Op> ops = new ArrayList<>();

        // Xác thực: public ones need no session; the others are any role's own session.
        ops.add(publicOp("registerAccount", "/api/v1/auth/register"));
        ops.add(publicOp("verifyEmail", "/api/v1/auth/verify"));
        ops.add(publicOp("resendVerification", "/api/v1/auth/verification-resend"));
        ops.add(publicOp("loginAccount", "/api/v1/auth/login"));
        ops.add(publicOp("requestRecovery", "/api/v1/auth/recovery"));
        ops.add(publicOp("resetPassword", "/api/v1/auth/reset"));
        ops.add(new Op("logoutAccount", Access.ANY, Set.of(), this::nothing, v -> post("/api/v1/auth/logout"), null,
                false));
        ops.add(new Op("listOwnSessions", Access.ANY, Set.of(), this::ownSession, v -> get("/api/v1/me/sessions"),
                null, true));
        ops.add(new Op("revokeOwnSession", Access.ANY, Set.of(), this::ownSession,
                v -> delete("/api/v1/me/sessions/" + v.get("session")), "session", false));
        ops.add(new Op("changePassword", Access.ANY, Set.of(), this::nothing, v -> json(post("/api/v1/me/password"),
                "{\"currentPassword\":\"mot-mat-khau-du-dai-15\",\"newPassword\":\"mot-mat-khau-moi-du-dai-16\"}"),
                null, false));
        ops.add(new Op("reauthenticate", Access.ANY, Set.of(), this::nothing, v -> json(post("/api/v1/auth/reauth"),
                "{\"password\":\"mot-mat-khau-du-dai-15\",\"purpose\":\"PRIVACY\"}"), null, false));

        // Học sinh.
        ops.add(new Op("listStudents", Access.ROLES, parentOrStudent, this::family, v -> get("/api/v1/students"),
                null, true));
        ops.add(new Op("createStudent", Access.ROLES, parent, this::nothing, v -> keyed(json(post("/api/v1/students"),
                "{\"displayName\":\"Minh\",\"grade\":8,\"shortGoal\":\"\"}")), null, false));
        ops.add(new Op("getStudentProfile", Access.ROLES, parentOrStudent, this::family,
                v -> get("/api/v1/students/" + v.get("student")), "student", false));
        ops.add(new Op("patchStudent", Access.ROLES, parent, this::family,
                v -> keyed(json(patch("/api/v1/students/" + v.get("student")), "{\"displayName\":\"Lan\"}"))
                        .header("If-Match", "\"1\""), "student", false));

        // Hồ sơ gia sư: all of it is the tutor's own (/me), except an upload named by its id.
        String profileBody = "{\"description\":\"%s\",\"experienceMonths\":12,\"rateVnd60\":180000,\"subject\":\"MATH8_ALGEBRA\"}"
                .formatted(DESCRIPTION);
        ops.add(new Op("saveTutorProfile", Access.ROLES, tutor, this::nothing,
                v -> keyed(json(put("/api/v1/me/tutor-profile"), profileBody)).header("If-None-Match", "*"), null,
                false));
        ops.add(new Op("getOwnTutorProfile", Access.ROLES, tutor, this::tutorData, v -> get("/api/v1/me/tutor-profile"),
                null, true));
        ops.add(new Op("submitTutorProfile", Access.ROLES, tutor, this::nothing,
                v -> keyed(json(post("/api/v1/me/tutor-profile/submit"),
                        "{\"profileVersionId\":\"%s\",\"evidenceUploadIds\":[]}".formatted(UUID.randomUUID())))
                        .header("If-Match", "\"1\""), null, false));
        ops.add(new Op("getOwnTutorVerification", Access.ROLES, tutor, this::tutorData,
                v -> get("/api/v1/me/tutor-profile/verification"), null, true));
        ops.add(new Op("createEvidenceUpload", Access.ROLES, tutor, this::nothing,
                v -> keyed(json(post("/api/v1/me/tutor-profile/evidence-uploads"),
                        "{\"mediaType\":\"application/pdf\",\"sizeBytes\":100,\"sha256\":\"%s\"}".formatted(SHA))),
                null, false));
        ops.add(new Op("uploadEvidenceContent", Access.ROLES, tutor, this::tutorData,
                v -> keyed(put("/api/v1/me/tutor-profile/evidence-uploads/" + v.get("upload") + "/content")
                        .contentType("application/pdf").content(new byte[100])), "upload", false));
        ops.add(new Op("completeEvidenceUpload", Access.ROLES, tutor, this::tutorData,
                v -> keyed(json(post("/api/v1/me/tutor-profile/evidence-uploads/" + v.get("upload") + "/complete"),
                        "{\"sizeBytes\":100,\"sha256\":\"%s\"}".formatted(SHA))), "upload", false));

        // Lịch.
        ops.add(new Op("getTutorCalendar", Access.ROLES, tutor, this::tutorData, v -> get("/api/v1/me/tutor-calendar"),
                null, true));
        ops.add(new Op("replaceTutorCalendar", Access.ROLES, tutor, this::nothing,
                v -> keyed(json(put("/api/v1/me/tutor-calendar"), calendarBody())).header("If-None-Match", "*"),
                null, false));
        ops.add(new Op("getStudentCalendar", Access.ROLES, parent, this::family,
                v -> get("/api/v1/students/" + v.get("student") + "/calendar"), "student", false));
        ops.add(new Op("replaceStudentCalendar", Access.ROLES, parent, this::family,
                v -> keyed(json(put("/api/v1/students/" + v.get("student") + "/calendar"), calendarBody()))
                        .header("If-Match", "\"1\""), "student", false));

        // Nhu cầu.
        ops.add(new Op("listNeeds", Access.ROLES, parent, this::family,
                v -> get("/api/v1/students/" + v.get("student") + "/needs"), "student", false));
        ops.add(new Op("createNeedDraft", Access.ROLES, parent, this::family,
                v -> keyed(json(post("/api/v1/students/" + v.get("student") + "/needs"), "{}")), "student", false));
        ops.add(new Op("getNeed", Access.ROLES, parent, this::family, v -> get("/api/v1/needs/" + v.get("need")),
                "need", false));
        ops.add(new Op("updateNeedDraft", Access.ROLES, parent, this::family,
                v -> keyed(json(patch("/api/v1/needs/" + v.get("need")), "{\"goal\":\"%s\"}".formatted(GOAL)))
                        .header("If-Match", "\"1\""), "need", false));
        ops.add(new Op("confirmNeed", Access.ROLES, parent, this::family,
                v -> keyed(json(post("/api/v1/needs/" + v.get("need") + "/confirm"), "{\"confirmed\":true}"))
                        .header("If-Match", "\"1\""), "need", false));

        // Gợi ý.
        ops.add(new Op("createMatchingRun", Access.ROLES, parent, this::family,
                v -> keyed(json(post("/api/v1/needs/" + v.get("need") + "/matching-runs"),
                        "{\"mode\":\"B\",\"needVersionId\":\"%s\",\"fallbackExplicitlyChosen\":true}"
                                .formatted(v.get("needVersion")))).header("If-Match", "\"1\""), "need", false));
        ops.add(new Op("getMatchingRun", Access.ROLES, parent, this::family,
                v -> get("/api/v1/matching-runs/" + v.get("run")), "run", false));
        // A public profile is no one's own data: no other-member row, only the roles.
        ops.add(new Op("getTutorPublic", Access.ROLES, parent, this::publicTutor,
                v -> get("/api/v1/tutors/" + v.get("tutor") + "/public-profile"), null, false));
        return ops;
    }

    @TestFactory
    Stream<DynamicNode> every_operation_of_huys_groups_answers_its_access_cases() {
        return ops().stream().map(op -> DynamicContainer.dynamicContainer(op.id(), cases(op)));
    }

    private Stream<DynamicNode> cases(Op op) {
        List<DynamicNode> cases = new ArrayList<>();
        if (op.access() == Access.PUBLIC) {
            cases.add(DynamicTest.dynamicTest("public: no session is not 401", () -> {
                MockHttpServletResponse answer = send(op.request().build(Map.of()), null);
                assertThat(answer.getStatus()).as(op.id()).isNotEqualTo(401);
            }));
            return cases.stream();
        }
        cases.add(DynamicTest.dynamicTest("no session: 401", () -> {
            Actor owner = owner(op);
            MockHttpServletResponse answer = send(op.request().build(op.setup().make(owner)), null);
            assertThat(answer.getStatus()).as(op.id()).isEqualTo(401);
        }));
        if (op.access() == Access.ROLES) {
            boolean hidden = WRONG_ROLE_404.containsKey(op.id());
            for (Role role : EnumSet.complementOf(EnumSet.copyOf(op.allowed()))) {
                cases.add(DynamicTest.dynamicTest("role " + role + (hidden ? ": same 404 as an unknown id" : ": 403"),
                        () -> {
                    Map<String, String> vars = op.setup().make(owner(op));
                    Actor caller = newActor(role);
                    MockHttpServletResponse answer = send(op.request().build(vars), caller);
                    String what = op.id() + " as " + role + ": " + answer.getContentAsString();
                    if (!hidden) {
                        assertThat(answer.getStatus()).as(what).isEqualTo(403);
                        return;
                    }
                    Map<String, String> unknown = new HashMap<>(vars);
                    unknown.put(op.idName(), UUID.randomUUID().toString());
                    MockHttpServletResponse none = send(op.request().build(unknown), caller);
                    assertThat(answer.getStatus()).as(what).isEqualTo(404);
                    assertThat(none.getStatus()).as(what).isEqualTo(404);
                    assertThat(code(answer)).as(what).isEqualTo(code(none));
                }));
            }
        }
        if (op.idName() != null) {
            cases.add(DynamicTest.dynamicTest("another member with the owner's id: same 404 as an unknown id", () -> {
                Actor owner = owner(op);
                Map<String, String> vars = op.setup().make(owner);
                Actor other = newActor(owner.role());
                Map<String, String> unknown = new HashMap<>(vars);
                unknown.put(op.idName(), UUID.randomUUID().toString());

                MockHttpServletResponse theirs = send(op.request().build(vars), other);
                MockHttpServletResponse none = send(op.request().build(unknown), other);

                assertThat(theirs.getStatus()).as(op.id() + ": " + theirs.getContentAsString()).isEqualTo(404);
                assertThat(none.getStatus()).as(op.id() + ": " + none.getContentAsString()).isEqualTo(404);
                assertThat(code(theirs)).as(op.id()).isEqualTo(code(none));
            }));
        }
        if (op.leak()) {
            cases.add(DynamicTest.dynamicTest("another member's own-data answer holds none of the owner's ids", () -> {
                Actor owner = owner(op);
                Map<String, String> vars = op.setup().make(owner);
                MockHttpServletResponse answer = send(op.request().build(vars), newActor(owner.role()));
                String body = answer.getContentAsString();
                for (String value : vars.values()) {
                    assertThat(body).as(op.id()).doesNotContain(value);
                }
            }));
        }
        return cases.stream();
    }

    // ---------------------------------------------------------------- the matrix covers the groups

    @Test
    void the_matrix_has_a_row_for_every_operation_of_huys_groups_in_r0a_scope() throws Exception {
        Set<String> scoped = huysOperations();
        Set<String> rows = new TreeSet<>();
        for (Op op : ops()) {
            assertThat(rows.add(op.id())).as("one row per operation: " + op.id()).isTrue();
        }

        assertThat(scoped).as("Huy's groups as r0a-scope.md lists them").hasSize(38);
        Set<String> covered = new TreeSet<>(rows);
        covered.addAll(PENDING.keySet());
        assertThat(covered).isEqualTo(new TreeSet<>(scoped));
        assertThat(rows).doesNotContainAnyElementsOf(PENDING.keySet());
    }

    /** The operationIds of Huy's groups in the API table of `r0a-scope.md`. */
    private static Set<String> huysOperations() throws Exception {
        Set<String> ids = new LinkedHashSet<>();
        Pattern row = Pattern.compile("^\\| ([^|]+) \\| \\d+ \\| (.+) \\|$");
        Pattern id = Pattern.compile("`(\\w+)`");
        for (String line : Files.readAllLines(Path.of("../docs/context/r0a-scope.md"))) {
            Matcher m = row.matcher(line);
            if (m.matches() && HUY_GROUPS.contains(m.group(1).trim())) {
                Matcher each = id.matcher(m.group(2));
                while (each.find()) {
                    ids.add(each.group(1));
                }
            }
        }
        return ids;
    }

    // ---------------------------------------------------------------- owners and their resources

    private Actor owner(Op op) {
        if (op.access() == Access.ANY) {
            return newActor(Role.PARENT);
        }
        return newActor(op.allowed().contains(Role.PARENT) ? Role.PARENT : Role.TUTOR);
    }

    private Map<String, String> nothing(Actor owner) {
        return Map.of("owner", owner.accountId().toString());
    }

    /** One real session row of the owner's (the test resolver stands in for the token, not for the row). */
    private Map<String, String> ownSession(Actor owner) {
        UUID session = UUID.randomUUID();
        jdbc.sql("""
                insert into auth_sessions(id, account_id, token_digest, expires_at, device_label)
                values (?, ?, ?, now() + interval '1 day', 'Android')
                """).params(session, owner.accountId(), UUID.randomUUID().toString()).update();
        return Map.of("owner", owner.accountId().toString(), "session", session.toString());
    }

    /**
     * The owner's child at revision 1 with a calendar at version 1, a DRAFT need at revision 1, and a FAILED
     * run of that need's version (the run's data does not matter here, only whose it is).
     */
    private Map<String, String> family(Actor owner) {
        UUID student = UUID.randomUUID();
        jdbc.sql("insert into students(id, parent_id, display_name) values (?, ?, 'Hoc sinh thu')")
                .params(student, owner.accountId()).update();
        UUID calendar = UUID.randomUUID();
        jdbc.sql("insert into calendars(id, owner_kind, owner_id, version, effective) values (?, 'STUDENT', ?, 1, true)")
                .params(calendar, student).update();
        UUID need = UUID.randomUUID();
        UUID version = UUID.randomUUID();
        jdbc.sql("insert into learning_needs(id, student_id, status, revision) values (?, ?, 'DRAFT', 1)")
                .params(need, student).update();
        jdbc.sql("insert into need_versions(id, need_id, version) values (?, ?, 1)").params(version, need).update();
        jdbc.sql("update learning_needs set current_version_id = ? where id = ?").params(version, need).update();
        UUID run = UUID.randomUUID();
        jdbc.sql("""
                insert into matching_runs(id, need_version_id, mode, algorithm_version, config_version, status,
                                          failure_code, expires_at)
                values (?, ?, 'S', 'S-v1', 'none', 'FAILED', 'AI_RANKING_UNAVAILABLE', now() + interval '1 day')
                """).params(run, version).update();
        return Map.of("owner", owner.accountId().toString(), "student", student.toString(),
                "calendar", calendar.toString(), "need", need.toString(), "needVersion", version.toString(),
                "run", run.toString());
    }

    /** The owner's current APPROVED profile, its verification, an open upload and an effective calendar. */
    private Map<String, String> tutorData(Actor owner) {
        UUID profile = UUID.randomUUID();
        jdbc.sql("""
                insert into tutor_profiles(id, tutor_id, version, status, description, experience_months, rate_vnd_60,
                                           current)
                values (?, ?, 1, 'APPROVED', ?, 12, 180000, true)
                """).params(profile, owner.accountId(), DESCRIPTION).update();
        UUID verification = UUID.randomUUID();
        jdbc.sql("""
                insert into tutor_verifications(id, profile_id, scope, status, evidence_key)
                values (?, ?, 'MATH8_ALGEBRA', 'PENDING', ?)
                """).params(verification, profile, "0".repeat(64)).update();
        UUID upload = UUID.randomUUID();
        jdbc.sql("""
                insert into tutor_evidence_uploads(id, owner_id, object_key, media_type, size_bytes, sha256, state,
                                                   expires_at)
                values (?, ?, ?, 'application/pdf', 100, ?, 'UPLOADING', now() + interval '15 minutes')
                """).params(upload, owner.accountId(), UUID.randomUUID().toString(), SHA).update();
        UUID calendar = UUID.randomUUID();
        jdbc.sql("insert into calendars(id, owner_kind, owner_id, version, effective) values (?, 'TUTOR', ?, 1, true)")
                .params(calendar, owner.accountId()).update();
        return Map.of("owner", owner.accountId().toString(), "profile", profile.toString(),
                "verification", verification.toString(), "upload", upload.toString(), "calendar", calendar.toString());
    }

    /** A tutor with a public profile, for getTutorPublic; the caller is someone else. */
    private Map<String, String> publicTutor(Actor caller) {
        Actor tutor = newActor(Role.TUTOR);
        jdbc.sql("update accounts set display_name = 'Cô Lan Thử' where id = ?").param(tutor.accountId()).update();
        Map<String, String> vars = new HashMap<>(tutorData(tutor));
        vars.put("tutor", tutor.accountId().toString());
        return vars;
    }

    // ---------------------------------------------------------------- requests

    private static Op publicOp(String id, String path) {
        return new Op(id, Access.PUBLIC, Set.of(), owner -> Map.of(), v -> json(post(path), "{}"), null, false);
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static MockHttpServletRequestBuilder keyed(MockHttpServletRequestBuilder request) {
        return request.header("Idempotency-Key", UUID.randomUUID().toString());
    }

    /** One AVAILABLE window tomorrow evening, Vietnam time. */
    private static String calendarBody() {
        LocalDate tomorrow = LocalDate.now(VIETNAM).plusDays(1);
        return "{\"timezone\":\"Asia/Ho_Chi_Minh\",\"windows\":[{\"start\":\"%s\",\"end\":\"%s\",\"kind\":\"AVAILABLE\"}]}"
                .formatted(tomorrow.atTime(18, 0).atZone(VIETNAM).toInstant(),
                        tomorrow.atTime(20, 0).atZone(VIETNAM).toInstant());
    }

    private MockHttpServletResponse send(MockHttpServletRequestBuilder request, Actor actor) throws Exception {
        if (actor != null) {
            request.with(sessions().as(actor));
        }
        return mvc.perform(request).andExpect(Contract.matches()).andReturn().getResponse();
    }

    private static String code(MockHttpServletResponse answer) throws Exception {
        return JsonPath.read(answer.getContentAsString(), "$.code");
    }
}
