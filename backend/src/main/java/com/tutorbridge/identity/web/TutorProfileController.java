package com.tutorbridge.identity.web;

import com.tutorbridge.identity.internal.TutorProfiles;
import com.tutorbridge.platform.api.Actor;
import com.tutorbridge.platform.api.Etag;
import com.tutorbridge.platform.api.IdempotencyKey;
import com.tutorbridge.platform.api.WritePrecondition;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** getOwnTutorProfile, saveTutorProfile (UC03, TB-12a). The service checks the owner and the fields. */
@RestController
class TutorProfileController {

    // Upper bounds in UTF-16 units; the service checks the contract's limits in code points, so every
    // problem names its field.
    record ProfileInput(@NotNull @Size(max = 6000) String description, @NotNull Integer experienceMonths,
            @NotNull Integer rateVnd60, @NotNull @Size(max = 100) String subject) {}

    private final TutorProfiles profiles;

    TutorProfileController(TutorProfiles profiles) {
        this.profiles = profiles;
    }

    @GetMapping("/api/v1/me/tutor-profile")
    ResponseEntity<TutorProfiles.Profile> get(@AuthenticationPrincipal Actor actor) {
        return withEtag(profiles.get(actor));
    }

    @PutMapping("/api/v1/me/tutor-profile")
    ResponseEntity<TutorProfiles.Profile> save(@AuthenticationPrincipal Actor actor, IdempotencyKey key,
            WritePrecondition precondition, @Valid @RequestBody ProfileInput input) {
        return withEtag(profiles.save(actor, key, precondition, new TutorProfiles.Input(input.description(),
                input.experienceMonths(), input.rateVnd60(), input.subject())));
    }

    private static ResponseEntity<TutorProfiles.Profile> withEtag(TutorProfiles.Profile profile) {
        return ResponseEntity.ok().header(HttpHeaders.ETAG, Etag.of(profile.revision())).body(profile);
    }
}
