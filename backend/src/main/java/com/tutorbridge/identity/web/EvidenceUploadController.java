package com.tutorbridge.identity.web;

import com.tutorbridge.identity.internal.EvidenceUploads;
import com.tutorbridge.platform.api.Actor;
import com.tutorbridge.platform.api.IdempotencyKey;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/** createEvidenceUpload, uploadEvidenceContent (UC03, TB-12b1). The service checks the owner. */
@RestController
class EvidenceUploadController {

    static final String UPLOADS = "/api/v1/me/tutor-profile/evidence-uploads";

    // Upper bound only, so a huge string is refused early; the service checks the contract's rules.
    record CreateInput(@NotNull @Size(max = 100) String mediaType, @NotNull Integer sizeBytes,
            @NotNull @Size(max = 100) String sha256) {}

    record Ticket(UUID uploadId, URI uploadUrl, Instant expiresAt) {}

    private final EvidenceUploads uploads;

    EvidenceUploadController(EvidenceUploads uploads) {
        this.uploads = uploads;
    }

    @PostMapping(UPLOADS)
    ResponseEntity<Ticket> create(@AuthenticationPrincipal Actor actor, IdempotencyKey key,
            @Valid @RequestBody CreateInput input) {
        EvidenceUploads.Ticket ticket = uploads.create(actor, key, input.mediaType(), input.sizeBytes(),
                input.sha256());
        // The origin of this very request, so uploadUrl is always on the API origin and the client may send
        // its session there (#163). Behind a proxy this needs forwarded headers trusted at deploy time.
        URI uploadUrl = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path(UPLOADS + "/{uploadId}/content").buildAndExpand(ticket.uploadId()).toUri();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new Ticket(ticket.uploadId(), uploadUrl, ticket.expiresAt()));
    }

    /** The raw body is streamed to the service, never buffered whole and never bound by a converter. */
    @PutMapping(UPLOADS + "/{uploadId}/content")
    EvidenceUploads.Evidence putContent(@AuthenticationPrincipal Actor actor, @PathVariable UUID uploadId,
            IdempotencyKey key, HttpServletRequest request) throws IOException {
        return uploads.putContent(actor, uploadId, key, request.getHeader(HttpHeaders.CONTENT_TYPE),
                request.getInputStream());
    }
}
