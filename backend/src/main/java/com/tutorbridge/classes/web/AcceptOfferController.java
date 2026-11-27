package com.tutorbridge.classes.web;

import com.tutorbridge.classes.api.AcceptOffer.AcceptedClass;
import com.tutorbridge.classes.internal.AcceptOfferService;
import com.tutorbridge.platform.api.Actor;
import com.tutorbridge.platform.api.Etag;
import com.tutorbridge.platform.api.IdempotencyKey;
import com.tutorbridge.platform.api.WritePrecondition;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * acceptOffer (UC06, TX05, TB-19). The service checks the role, the recipient and every term again under
 * its locks. Its own file, apart from {@code OfferController}, which TB-21 changes in the same week.
 */
@RestController
class AcceptOfferController {

    static final String OPERATION_ID = "X-Operation-Id";

    /** {@code AcceptInput}: the constant true, the tutor agreeing to the terms unchanged (TX06). */
    record AcceptInput(@NotNull @AssertTrue Boolean acceptUnchangedTerms) {}

    /** {@code Class}; the receipt id goes in {@code X-Operation-Id}, not in the body. */
    record ClassBody(UUID id, UUID studentId, UUID tutorId, long revision, String state, int priceVndSession,
            int durationMinutes, UUID effectiveGoalVersionId, String goal) {

        static ClassBody of(AcceptedClass c) {
            return new ClassBody(c.id(), c.studentId(), c.tutorId(), c.revision(), c.state(), c.priceVndSession(),
                    c.durationMinutes(), c.effectiveGoalVersionId(), c.goal());
        }
    }

    private final AcceptOfferService accepts;

    AcceptOfferController(AcceptOfferService accepts) {
        this.accepts = accepts;
    }

    @PostMapping("/api/v1/offers/{offerId}/accept")
    ResponseEntity<ClassBody> accept(@AuthenticationPrincipal Actor actor, @PathVariable UUID offerId,
            IdempotencyKey key, WritePrecondition.Expected expected, @Valid @RequestBody AcceptInput input) {
        AcceptedClass created = accepts.accept(actor, offerId, expected, key);
        // A replay answers 201 too, with the same body and the same receipt id (AC-UC06-04).
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.ETAG, Etag.of(created.revision()))
                .header(OPERATION_ID, created.operationId().toString())
                .body(ClassBody.of(created));
    }
}
