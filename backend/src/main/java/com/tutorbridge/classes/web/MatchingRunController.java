package com.tutorbridge.classes.web;

import com.tutorbridge.classes.internal.MatchingRunService;
import com.tutorbridge.platform.api.Actor;
import com.tutorbridge.platform.api.Etag;
import com.tutorbridge.platform.api.IdempotencyKey;
import com.tutorbridge.platform.api.Receipted;
import com.tutorbridge.platform.api.WritePrecondition;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * createMatchingRun, getMatchingRun (UC05, TX03, TB-17). The service checks the owner. A run never
 * changes once written, so its ETag is the constant {@code "1"}.
 */
@RestController
class MatchingRunController {

    /** {@code MatchInput}. */
    record MatchInput(@NotNull @Pattern(regexp = "S|B") String mode, @NotNull UUID needVersionId,
            Boolean fallbackExplicitlyChosen) {

        MatchingRunService.Input input() {
            return new MatchingRunService.Input(mode, needVersionId, fallbackExplicitlyChosen);
        }
    }

    private static final String RUN_ETAG = Etag.of(1);

    private final MatchingRunService runs;

    MatchingRunController(MatchingRunService runs) {
        this.runs = runs;
    }

    /** 202 as the contract says; the run is already COMPLETED or FAILED when it answers. */
    @PostMapping("/api/v1/needs/{needId}/matching-runs")
    ResponseEntity<MatchingRunService.MatchingRun> create(@AuthenticationPrincipal Actor actor,
            @PathVariable UUID needId, IdempotencyKey key, WritePrecondition.Expected expected,
            @Valid @RequestBody MatchInput input) {
        Receipted<MatchingRunService.MatchingRun> created = runs.create(actor, needId, key, expected, input.input());
        return ResponseEntity.status(HttpStatus.ACCEPTED).header(HttpHeaders.ETAG, RUN_ETAG)
                .header(Receipted.HEADER, created.operationId().toString()).body(created.value());
    }

    @GetMapping("/api/v1/matching-runs/{runId}")
    ResponseEntity<MatchingRunService.MatchingRun> get(@AuthenticationPrincipal Actor actor,
            @PathVariable UUID runId) {
        return ResponseEntity.ok().header(HttpHeaders.ETAG, RUN_ETAG).body(runs.get(actor, runId));
    }
}
