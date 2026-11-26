package com.tutorbridge.identity.web;

import com.tutorbridge.identity.api.Parties.PublicTutor;
import com.tutorbridge.identity.internal.TutorPublicProfiles;
import com.tutorbridge.platform.api.Actor;
import com.tutorbridge.platform.api.Etag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** getTutorPublic (TB-17 PR 2). The service checks the role; the ETag is the profile version's revision. */
@RestController
class TutorPublicController {

    /** The {@code TutorPublic} schema; experience is always self-declared (AC-UC05-04, INV-37). */
    record TutorPublicView(UUID tutorId, UUID profileVersionId, String displayName, String description, int rateVnd60,
            int experienceMonths, String experienceLabel, List<String> verificationScopes) {

        static TutorPublicView of(PublicTutor tutor) {
            return new TutorPublicView(tutor.tutorId(), tutor.profileVersionId(), tutor.displayName(),
                    tutor.description(), tutor.rateVnd60(), tutor.experienceMonths(), "SELF_DECLARED",
                    tutor.verificationScopes());
        }
    }

    private final TutorPublicProfiles profiles;

    TutorPublicController(TutorPublicProfiles profiles) {
        this.profiles = profiles;
    }

    @GetMapping("/api/v1/tutors/{tutorId}/public-profile")
    ResponseEntity<TutorPublicView> get(@AuthenticationPrincipal Actor actor, @PathVariable UUID tutorId) {
        PublicTutor tutor = profiles.get(actor, tutorId);
        return ResponseEntity.ok().header(HttpHeaders.ETAG, Etag.of(tutor.revision())).body(TutorPublicView.of(tutor));
    }
}
