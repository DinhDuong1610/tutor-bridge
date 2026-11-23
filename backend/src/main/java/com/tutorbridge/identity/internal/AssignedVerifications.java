package com.tutorbridge.identity.internal;

import com.tutorbridge.platform.api.Actor;
import com.tutorbridge.platform.api.ApiException;
import com.tutorbridge.platform.api.Cursor;
import com.tutorbridge.platform.api.Page;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * UC03 for the verifier (TB-13, OP-28): listAssignedVerifications (AD03) and getAssignedVerification (AD04).
 *
 * <p>Only an ADMIN who holds VERIFY reads here; anyone else gets 403 before any record is looked at. Among
 * the records, the caller sees only those assigned to them; one assigned to someone else, or no longer to
 * them, is 404 as if it did not exist (INV-06), and no support role reads them implicitly. Read only, no
 * lock, no receipt.
 */
@Service
public class AssignedVerifications {

    /** The {@code AssignedVerification} schema of the contract. */
    public record Verification(UUID id, UUID profileVersionId, int profileVersion, String tutorDisplayName,
            UUID assignedTo, String status, String scope, String reason, Instant submittedAt, Instant decidedAt,
            boolean superseded, long revision) {}

    /** {@code SubmittedTutorProfile}. */
    public record SubmittedProfile(UUID id, int version, String subject, String description, int experienceMonths,
            int rateVnd60) {}

    /** {@code AssignedEvidence}. No object key and no hash. */
    public record Evidence(UUID id, String mediaType, int sizeBytes) {}

    /** {@code AssignedVerificationDetail}. */
    public record Detail(Verification verification, SubmittedProfile profile, List<Evidence> evidence) {}

    private final AssignedVerificationRepository verifications;

    AssignedVerifications(AssignedVerificationRepository verifications) {
        this.verifications = verifications;
    }

    /** {@code status} is PENDING, VERIFIED, REJECTED or null for all; the controller checks the value. */
    public Page<Verification> list(Actor actor, String status, String cursor, Integer requestedLimit) {
        requireVerifier(actor);
        int limit = Page.limit(requestedLimit);
        Cursor after = Cursor.decode(cursor);
        List<AssignedVerificationRepository.Row> rows =
                verifications.listAssigned(actor.accountId(), status, after, limit + 1);
        Page<AssignedVerificationRepository.Row> page =
                Page.of(rows, limit, row -> new Cursor(row.submittedAt(), row.id()));
        return new Page<>(page.items().stream().map(AssignedVerifications::verification).toList(), page.nextCursor());
    }

    public Detail get(Actor actor, UUID verificationId) {
        requireVerifier(actor);
        AssignedVerificationRepository.Detail found = verifications.findAssigned(verificationId, actor.accountId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "VERIFICATION_NOT_FOUND",
                        "Không tìm thấy hồ sơ cần xác minh."));
        AssignedVerificationRepository.Profile p = found.profile();
        return new Detail(verification(found.verification()),
                new SubmittedProfile(p.id(), p.version(), p.subject(), p.description(), p.experienceMonths(),
                        p.rateVnd60()),
                verifications.evidenceOf(p.id()).stream()
                        .map(e -> new Evidence(e.id(), e.mediaType(), e.sizeBytes())).toList());
    }

    private void requireVerifier(Actor actor) {
        if (actor.role() != Actor.Role.ADMIN || !verifications.canVerify(actor.accountId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Tài khoản không thể thực hiện thao tác này.");
        }
    }

    /**
     * A record without a reason has no decision yet, so it has no decision time either (the contract ties
     * decidedAt to reason). NEEDS_INFO is PENDING with a reason (G3-D07-05) and shows when it was asked.
     */
    private static Verification verification(AssignedVerificationRepository.Row row) {
        Instant decidedAt = row.reason() == null ? null : row.reviewedAt();
        return new Verification(row.id(), row.profileVersionId(), row.profileVersion(), row.tutorDisplayName(),
                row.assignedTo(), row.status(), row.scope(), row.reason(), row.submittedAt(), decidedAt,
                row.superseded(), row.revision());
    }
}
