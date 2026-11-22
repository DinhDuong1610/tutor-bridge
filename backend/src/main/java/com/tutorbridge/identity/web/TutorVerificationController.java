package com.tutorbridge.identity.web;

import com.tutorbridge.identity.internal.TutorVerifications;
import com.tutorbridge.platform.api.Actor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** getOwnTutorVerification (UC03, TB-12c). The service checks the role; the owner is the session. */
@RestController
class TutorVerificationController {

    private final TutorVerifications verifications;

    TutorVerificationController(TutorVerifications verifications) {
        this.verifications = verifications;
    }

    @GetMapping("/api/v1/me/tutor-profile/verification")
    TutorVerifications.View get(@AuthenticationPrincipal Actor actor) {
        return verifications.getOwn(actor);
    }
}
