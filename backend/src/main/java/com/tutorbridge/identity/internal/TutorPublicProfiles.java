package com.tutorbridge.identity.internal;

import com.tutorbridge.identity.api.Parties.PublicTutor;
import com.tutorbridge.platform.api.Actor;
import com.tutorbridge.platform.api.ApiException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * getTutorPublic (UC03, UC05; TB-17 PR 2): what a parent sees of a tutor before an agreement. Only an
 * authenticated PARENT may read it (contract). A tutor that is not shown to parents (no current approved
 * version, account not ACTIVE, no display name) and an unknown id get the same 404 (INV-06). Read only.
 */
@Service
public class TutorPublicProfiles {

    private final TutorPublicRepository tutors;

    TutorPublicProfiles(TutorPublicRepository tutors) {
        this.tutors = tutors;
    }

    public PublicTutor get(Actor actor, UUID tutorId) {
        if (actor.role() != Actor.Role.PARENT) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCESS_UNAVAILABLE",
                    "Tài khoản không thể thực hiện thao tác này.");
        }
        return tutors.findCurrent(tutorId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TUTOR_NOT_FOUND",
                "Không tìm thấy gia sư này."));
    }
}
