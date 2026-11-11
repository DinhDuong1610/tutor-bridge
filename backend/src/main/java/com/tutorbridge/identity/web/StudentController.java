package com.tutorbridge.identity.web;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.tutorbridge.identity.internal.StudentProfiles;
import com.tutorbridge.platform.api.Actor;
import com.tutorbridge.platform.api.Etag;
import com.tutorbridge.platform.api.IdempotencyKey;
import com.tutorbridge.platform.api.Page;
import com.tutorbridge.platform.api.WritePrecondition;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** listStudents, createStudent, getStudentProfile, patchStudent (UC02, TB-11). The service checks the owner. */
@RestController
class StudentController {

    // Upper bounds in UTF-16 units; the service checks the contract's limits in code points.
    record CreateInput(@NotNull @Size(max = 200) String displayName, @NotNull Integer grade,
            @NotNull @Size(max = 4000) String shortGoal) {}

    // Every field is optional, but an explicit null is not a valid value in the contract. A class, not
    // a record: Jackson passes a missing creator property as null, which Nulls.FAIL would refuse too.
    static final class PatchInput {
        @JsonSetter(nulls = Nulls.FAIL)
        @Size(max = 200)
        public String displayName;

        @JsonSetter(nulls = Nulls.FAIL)
        public Integer grade;

        @JsonSetter(nulls = Nulls.FAIL)
        @Size(max = 4000)
        public String shortGoal;
    }

    private final StudentProfiles students;

    StudentController(StudentProfiles students) {
        this.students = students;
    }

    @GetMapping("/api/v1/students")
    Page<StudentProfiles.Student> list(@AuthenticationPrincipal Actor actor,
            @RequestParam(required = false) @Size(max = 500) String cursor,
            @RequestParam(required = false) Integer limit) {
        return students.list(actor, cursor, limit);
    }

    @PostMapping("/api/v1/students")
    ResponseEntity<StudentProfiles.Student> create(@AuthenticationPrincipal Actor actor, IdempotencyKey key,
            @Valid @RequestBody CreateInput input) {
        return withEtag(HttpStatus.CREATED,
                students.create(actor, key, input.displayName(), input.grade(), input.shortGoal()));
    }

    @GetMapping("/api/v1/students/{studentId}")
    ResponseEntity<StudentProfiles.Student> get(@AuthenticationPrincipal Actor actor, @PathVariable UUID studentId) {
        return withEtag(HttpStatus.OK, students.get(actor, studentId));
    }

    @PatchMapping("/api/v1/students/{studentId}")
    ResponseEntity<StudentProfiles.Student> patch(@AuthenticationPrincipal Actor actor, @PathVariable UUID studentId,
            IdempotencyKey key, WritePrecondition.Expected expected, @Valid @RequestBody PatchInput input) {
        return withEtag(HttpStatus.OK, students.patch(actor, studentId, key, expected,
                new StudentProfiles.Changes(input.displayName, input.grade, input.shortGoal)));
    }

    private static ResponseEntity<StudentProfiles.Student> withEtag(HttpStatus status, StudentProfiles.Student student) {
        return ResponseEntity.status(status).header(HttpHeaders.ETAG, Etag.of(student.revision())).body(student);
    }
}
