package com.tutorbridge.identity.web;

import com.tutorbridge.identity.internal.TutorProfileSubmissions;
import com.tutorbridge.platform.api.Actor;
import com.tutorbridge.platform.api.Etag;
import com.tutorbridge.platform.api.IdempotencyKey;
import com.tutorbridge.platform.api.WritePrecondition;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** submitTutorProfile (UC03, TB-12c). The service checks the owner, the version and the evidence. */
@RestController
class TutorProfileSubmissionController {

    // The service checks 1 to 10 files and repeats, so the answer names the field the way the contract does.
    record SubmitInput(@NotNull UUID profileVersionId, @NotNull List<@NotNull UUID> evidenceUploadIds) {}

    private final TutorProfileSubmissions submissions;

    TutorProfileSubmissionController(TutorProfileSubmissions submissions) {
        this.submissions = submissions;
    }

    @PostMapping("/api/v1/me/tutor-profile/submit")
    ResponseEntity<TutorProfileSubmissions.Verification> submit(@AuthenticationPrincipal Actor actor,
            IdempotencyKey key, WritePrecondition.Expected precondition, @Valid @RequestBody SubmitInput input) {
        TutorProfileSubmissions.Verification verification = submissions.submit(actor, key, precondition,
                input.profileVersionId(), input.evidenceUploadIds());
        return ResponseEntity.status(HttpStatus.CREATED).header(HttpHeaders.ETAG, Etag.of(verification.revision()))
                .body(verification);
    }
}
