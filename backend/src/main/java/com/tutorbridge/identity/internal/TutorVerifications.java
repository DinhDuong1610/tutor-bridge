package com.tutorbridge.identity.internal;

import com.tutorbridge.platform.api.Actor;
import com.tutorbridge.platform.api.ApiException;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * UC03 for the tutor (TB-12c PR 2b): getOwnTutorVerification.
 *
 * <p>The tutor reads the verification of their most recently submitted version. The state is derived
 * (OP-23, G3-D07-05): the table keeps PENDING for both a version still waiting and one that needs more
 * information, told apart by the verifier's reason. The verifier, the evidence and other tutors' records
 * are never part of the answer (INV-06). The owner always comes from the session (INV-03).
 *
 * <p>{@code approvedVersion} is the version parents see now (OP-24 (1), #166): while an edit waits or
 * was refused, the older approved version stays public. Read only, no lock, no receipt.
 */
@Service
public class TutorVerifications {

    /** The {@code TutorVerificationView} schema of the contract. */
    public record View(UUID id, UUID profileVersionId, int profileVersion, String state, String scope, String reason,
            Instant decidedAt, Integer approvedVersion) {}

    private final TutorVerificationRepository verifications;

    TutorVerifications(TutorVerificationRepository verifications) {
        this.verifications = verifications;
    }

    /** Never submitted, a draft included, is 404. */
    public View getOwn(Actor actor) {
        if (actor.role() != Actor.Role.TUTOR) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCESS_UNAVAILABLE",
                    "Tài khoản không thể thực hiện thao tác này.");
        }
        return verifications.findLatestOwn(actor.accountId())
                .map(TutorVerifications::view)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TUTOR_VERIFICATION_NOT_FOUND",
                        "Hồ sơ chưa được gửi xác minh."));
    }

    private static View view(TutorVerificationRepository.OwnVerification row) {
        String state = switch (row.status()) {
            case "PENDING" -> row.reason() == null ? "WAITING" : "NEEDS_INFO";
            case "VERIFIED" -> "VERIFIED";
            case "REJECTED" -> "REJECTED";
            default -> throw new IllegalStateException("Unknown verification status");
        };
        // The contract shows the reason only when the tutor has to act on it; a VERIFIED decision keeps
        // the verifier's note to themselves.
        String reason = state.equals("NEEDS_INFO") || state.equals("REJECTED") ? row.reason() : null;
        Instant decidedAt = state.equals("WAITING") ? null : row.reviewedAt();
        return new View(row.id(), row.profileVersionId(), row.profileVersion(), state, row.scope(), reason, decidedAt,
                row.approvedVersion());
    }
}
