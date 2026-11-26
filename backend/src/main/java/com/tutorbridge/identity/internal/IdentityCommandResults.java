package com.tutorbridge.identity.internal;

import com.tutorbridge.platform.api.Actor;
import com.tutorbridge.platform.api.CommandResults;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The results of this module's commands, for the command status lookup (TX16, TB-22): the same owner checks
 * as the replays of those commands, made now (INV-22). {@code TUTOR_VERIFICATION} has two writers: the tutor's
 * submitTutorProfile, and the verifier's decideVerification (TB-13), shown as getAssignedVerification reads it:
 * an ADMIN who may still verify, on a verification assigned to them.
 */
@Component
class IdentityCommandResults implements CommandResults {

    private final StudentRepository students;
    private final TutorProfileRepository profiles;
    private final TutorVerificationRepository verifications;
    private final EvidenceUploadRepository uploads;
    private final AssignedVerificationRepository assigned;

    IdentityCommandResults(StudentRepository students, TutorProfileRepository profiles,
            TutorVerificationRepository verifications, EvidenceUploadRepository uploads,
            AssignedVerificationRepository assigned) {
        this.students = students;
        this.profiles = profiles;
        this.verifications = verifications;
        this.uploads = uploads;
        this.assigned = assigned;
    }

    @Override
    public Set<String> resultTypes() {
        return Set.of(StudentProfiles.RESULT_TYPE, TutorProfiles.RESULT_TYPE, TutorProfileSubmissions.RESULT_TYPE,
                EvidenceUploads.RESULT_TYPE);
    }

    @Override
    public boolean visibleTo(Actor actor, String resultType, UUID resultId) {
        UUID me = actor.accountId();
        return switch (resultType) {
            case StudentProfiles.RESULT_TYPE -> actor.role() == Actor.Role.PARENT
                    && students.find(resultId).filter(row -> row.parentId().equals(me)).isPresent();
            case TutorProfiles.RESULT_TYPE -> actor.role() == Actor.Role.TUTOR
                    && profiles.find(resultId, me).isPresent();
            case TutorProfileSubmissions.RESULT_TYPE -> switch (actor.role()) {
                case TUTOR -> verifications.findOwn(resultId, me).isPresent();
                case ADMIN -> assigned.canVerify(me) && assigned.findAssigned(resultId, me).isPresent();
                default -> false;
            };
            case EvidenceUploads.RESULT_TYPE -> actor.role() == Actor.Role.TUTOR
                    && uploads.find(resultId).filter(row -> row.ownerId().equals(me)).isPresent();
            default -> false;
        };
    }
}
