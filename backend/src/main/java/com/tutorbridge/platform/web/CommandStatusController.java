package com.tutorbridge.platform.web;

import com.tutorbridge.platform.api.Actor;
import com.tutorbridge.platform.api.Etag;
import com.tutorbridge.platform.internal.CommandStatuses;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * getCommandStatus and findCommandStatus (TX16, TB-22). Any signed-in role; the service shows only the
 * caller's own receipts. The key travels in the query, and nothing here or in platform logs a request line,
 * so it never reaches the log; {@code CommandStatusHttpTest} checks it.
 */
@RestController
class CommandStatusController {

    private final CommandStatuses statuses;

    CommandStatusController(CommandStatuses statuses) {
        this.statuses = statuses;
    }

    @GetMapping("/api/v1/operations/{operationId}")
    ResponseEntity<CommandStatuses.CommandStatus> get(@AuthenticationPrincipal Actor actor,
            @PathVariable UUID operationId) {
        return answer(statuses.get(actor, operationId));
    }

    @GetMapping("/api/v1/operations")
    ResponseEntity<CommandStatuses.CommandStatus> find(@AuthenticationPrincipal Actor actor,
            @RequestParam @Size(min = 16, max = 128) String idempotencyKey,
            @RequestParam @Size(min = 1, max = 100) String commandType) {
        return answer(statuses.find(actor, commandType, idempotencyKey));
    }

    private static ResponseEntity<CommandStatuses.CommandStatus> answer(CommandStatuses.Answer answer) {
        return ResponseEntity.ok().header(HttpHeaders.ETAG, Etag.of(answer.version())).body(answer.status());
    }
}
