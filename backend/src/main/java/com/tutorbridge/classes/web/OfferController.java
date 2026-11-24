package com.tutorbridge.classes.web;

import com.tutorbridge.classes.internal.MatchingRunService;
import com.tutorbridge.classes.internal.OfferService;
import com.tutorbridge.platform.api.Actor;
import com.tutorbridge.platform.api.Etag;
import com.tutorbridge.platform.api.IdempotencyKey;
import com.tutorbridge.platform.api.Page;
import com.tutorbridge.platform.api.WritePrecondition;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** sendOffer, listOffers, getOffer (UC06, TX04, TX06, TB-18). The service checks the owner and the recipient. */
@RestController
class OfferController {

    /** {@code Window}. */
    record WindowInput(@NotNull Instant start, @NotNull Instant end) {}

    /** {@code OfferInput}: {@code acceptUnchangedTerms} is the constant true, the parent's agreement (BR05). */
    record OfferInput(@NotNull UUID needVersionId, @NotNull UUID matchingRunId, @NotNull UUID tutorId,
            @NotNull UUID profileVersionId, @NotNull UUID tutorCalendarVersionId,
            @NotNull UUID studentCalendarVersionId,
            @NotNull @Size(min = 4, max = 12) List<@NotNull @Valid WindowInput> schedule,
            @NotNull @AssertTrue Boolean acceptUnchangedTerms) {

        OfferService.Input input() {
            return new OfferService.Input(needVersionId, matchingRunId, tutorId, profileVersionId,
                    tutorCalendarVersionId, studentCalendarVersionId,
                    schedule.stream().map(w -> new MatchingRunService.Window(w.start(), w.end())).toList());
        }
    }

    private final OfferService offers;

    OfferController(OfferService offers) {
        this.offers = offers;
    }

    @PostMapping("/api/v1/needs/{needId}/offers")
    ResponseEntity<OfferService.Offer> send(@AuthenticationPrincipal Actor actor, @PathVariable UUID needId,
            IdempotencyKey key, WritePrecondition.Expected expected, @Valid @RequestBody OfferInput input) {
        return withEtag(HttpStatus.CREATED, offers.send(actor, needId, key, expected, input.input()));
    }

    @GetMapping("/api/v1/me/offers")
    Page<OfferService.Offer> list(@AuthenticationPrincipal Actor actor,
            @RequestParam(required = false) @Size(max = 500) String cursor,
            @RequestParam(required = false) Integer limit) {
        return offers.list(actor, cursor, limit);
    }

    @GetMapping("/api/v1/offers/{offerId}")
    ResponseEntity<OfferService.Offer> get(@AuthenticationPrincipal Actor actor, @PathVariable UUID offerId) {
        return withEtag(HttpStatus.OK, offers.get(actor, offerId));
    }

    private static ResponseEntity<OfferService.Offer> withEtag(HttpStatus status, OfferService.Offer offer) {
        return ResponseEntity.status(status).header(HttpHeaders.ETAG, Etag.of(offer.revision())).body(offer);
    }
}
