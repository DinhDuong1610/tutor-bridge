package com.tutorbridge.classes.web;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.tutorbridge.classes.internal.NeedService;
import com.tutorbridge.platform.api.Actor;
import com.tutorbridge.platform.api.Etag;
import com.tutorbridge.platform.api.IdempotencyKey;
import com.tutorbridge.platform.api.Page;
import com.tutorbridge.platform.api.Receipted;
import com.tutorbridge.platform.api.WritePrecondition;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
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

/** listNeeds, createNeedDraft, getNeed, updateNeedDraft, confirmNeed (UC04, TB-16). The service checks the owner. */
@RestController
class NeedController {

    // NeedDraftInput: every field is optional and an absent one is empty (#16 Q3), but an explicit null
    // is not a value of the contract. A class, not a record: Jackson passes a missing creator property
    // as null, which Nulls.FAIL would refuse too. Upper bounds in UTF-16 units; the service checks the
    // contract's limits in code points, so every problem names its field.
    static final class DraftInput {
        @JsonSetter(nulls = Nulls.FAIL)
        @Size(max = 4000)
        public String goal;

        @JsonSetter(nulls = Nulls.FAIL)
        public Integer budgetVndSession;

        @JsonSetter(nulls = Nulls.FAIL)
        public Integer durationMinutes;

        @JsonSetter(nulls = Nulls.FAIL)
        public Integer frequencyPer7Days;

        @JsonSetter(nulls = Nulls.FAIL)
        public LocalDate cycleStartDate;

        // Deprecated in the contract (#16 Q1): read only to refuse a non-empty array.
        @JsonSetter(nulls = Nulls.FAIL)
        @Size(max = 100)
        public List<Object> availableWindows;

        @JsonSetter(nulls = Nulls.FAIL)
        @Size(max = 100)
        public List<Object> preferredWindows;

        NeedService.Draft draft() {
            return new NeedService.Draft(goal, budgetVndSession, durationMinutes, frequencyPer7Days, cycleStartDate);
        }

        boolean hasWindows() {
            return (availableWindows != null && !availableWindows.isEmpty())
                    || (preferredWindows != null && !preferredWindows.isEmpty());
        }
    }

    private final NeedService needs;

    NeedController(NeedService needs) {
        this.needs = needs;
    }

    @GetMapping("/api/v1/students/{studentId}/needs")
    Page<NeedService.Need> list(@AuthenticationPrincipal Actor actor, @PathVariable UUID studentId,
            @RequestParam(required = false) @Size(max = 500) String cursor,
            @RequestParam(required = false) Integer limit) {
        return needs.list(actor, studentId, cursor, limit);
    }

    @PostMapping("/api/v1/students/{studentId}/needs")
    ResponseEntity<NeedService.Need> create(@AuthenticationPrincipal Actor actor, @PathVariable UUID studentId,
            IdempotencyKey key, @Valid @RequestBody DraftInput input) {
        return written(HttpStatus.CREATED, needs.create(actor, studentId, key, input.draft(), input.hasWindows()));
    }

    @GetMapping("/api/v1/needs/{needId}")
    ResponseEntity<NeedService.Need> get(@AuthenticationPrincipal Actor actor, @PathVariable UUID needId) {
        return withEtag(HttpStatus.OK, needs.get(actor, needId));
    }

    @PatchMapping("/api/v1/needs/{needId}")
    ResponseEntity<NeedService.Need> update(@AuthenticationPrincipal Actor actor, @PathVariable UUID needId,
            IdempotencyKey key, WritePrecondition.Expected expected, @Valid @RequestBody DraftInput input) {
        return written(HttpStatus.OK, needs.update(actor, needId, key, expected, input.draft(), input.hasWindows()));
    }

    /** ConfirmNeedInput: {@code confirmed} is the constant true, so the parent states the confirmation. */
    record ConfirmInput(@NotNull @AssertTrue Boolean confirmed) {}

    @PostMapping("/api/v1/needs/{needId}/confirm")
    ResponseEntity<NeedService.Need> confirm(@AuthenticationPrincipal Actor actor, @PathVariable UUID needId,
            IdempotencyKey key, WritePrecondition.Expected expected, @Valid @RequestBody ConfirmInput input) {
        return written(HttpStatus.OK, needs.confirm(actor, needId, key, expected));
    }

    private static ResponseEntity<NeedService.Need> withEtag(HttpStatus status, NeedService.Need need) {
        return ResponseEntity.status(status).header(HttpHeaders.ETAG, Etag.of(need.revision())).body(need);
    }

    /** A write's answer: the need with its ETag and the receipt id in X-Operation-Id (#221). */
    private static ResponseEntity<NeedService.Need> written(HttpStatus status, Receipted<NeedService.Need> written) {
        NeedService.Need need = written.value();
        return ResponseEntity.status(status).header(HttpHeaders.ETAG, Etag.of(need.revision()))
                .header(Receipted.HEADER, written.operationId().toString()).body(need);
    }
}
