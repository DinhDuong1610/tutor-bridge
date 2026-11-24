package com.tutorbridge.identity.web;

import com.tutorbridge.identity.internal.AssignedVerifications;
import com.tutorbridge.platform.api.Actor;
import com.tutorbridge.platform.api.Etag;
import com.tutorbridge.platform.api.Page;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** listAssignedVerifications and getAssignedVerification (UC03, TB-13). The service checks the verifier. */
@RestController
class AssignedVerificationController {

    private final AssignedVerifications verifications;

    AssignedVerificationController(AssignedVerifications verifications) {
        this.verifications = verifications;
    }

    @GetMapping("/api/v1/admin/verifications")
    Page<AssignedVerifications.Verification> list(@AuthenticationPrincipal Actor actor,
            @RequestParam(required = false) @Pattern(regexp = "PENDING|VERIFIED|REJECTED") String status,
            @RequestParam(required = false) @Size(max = 500) String cursor,
            @RequestParam(required = false) Integer limit) {
        return verifications.list(actor, status, cursor, limit);
    }

    /** The ETag is the record revision that decideVerification takes as If-Match. */
    @GetMapping("/api/v1/admin/verifications/{verificationId}")
    ResponseEntity<AssignedVerifications.Detail> get(@AuthenticationPrincipal Actor actor,
            @PathVariable UUID verificationId) {
        AssignedVerifications.Detail detail = verifications.get(actor, verificationId);
        return ResponseEntity.ok().header(HttpHeaders.ETAG, Etag.of(detail.verification().revision())).body(detail);
    }
}
