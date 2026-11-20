package com.tutorbridge.identity.internal;

import com.tutorbridge.platform.api.Actor;
import com.tutorbridge.platform.api.ApiException;
import com.tutorbridge.platform.api.ApiException.FieldProblem;
import com.tutorbridge.platform.api.AuditEvents;
import com.tutorbridge.platform.api.CanonicalJson;
import com.tutorbridge.platform.api.CommandReceipts;
import com.tutorbridge.platform.api.CommandReceipts.Receipt;
import com.tutorbridge.platform.api.IdempotencyKey;
import com.tutorbridge.platform.api.WritePrecondition;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.util.IdGenerator;

/**
 * UC03 for the tutor (TB-12c): submitTutorProfile. The newest version, a DRAFT, is frozen as PENDING
 * with 1 to 10 READY evidence files of its own tutor and a new PENDING verification (#12 Q3, Q5).
 *
 * <p>OP-24 (Đính, 17/11/2026): an earlier version that needs information is RETIRED, its verification
 * kept as the verifier left it and the replacement written to audit_events; one still waiting for its
 * verifier blocks the submission; rejected and approved versions are left alone, so parents keep seeing
 * the approved one.
 *
 * <p>Transaction, in this lock order (INV-16): the tutor's account (FOR SHARE, ACTIVE), the receipt, the
 * newest version (FOR UPDATE), the tutor's other PENDING versions by version and then their PENDING
 * verifications by id, the evidence by id (FOR SHARE). A version is always locked before its
 * verification, because the V11 guard reads the version when a verification is written. Every check
 * runs under these locks, and the writes, audit rows and receipt commit together (INV-21). No outside
 * I/O inside it (INV-18). Any refusal rolls the whole command back, receipt included.
 */
@Service
public class TutorProfileSubmissions {

    /**
     * The {@code VerificationRecord} schema of the contract. To the tutor, {@code assignedTo} is always
     * null: the verifier is never named to them (OP-23), and a new verification has none yet.
     */
    public record Verification(UUID id, UUID profileVersionId, UUID assignedTo, String status, String scope,
            long revision) {}

    static final String SUBMIT = "SUBMIT_TUTOR_PROFILE";
    static final int EVIDENCE_MAX = 10;
    static final String UNUSABLE_EVIDENCE = "Minh chứng này không dùng được. Vui lòng chọn lại.";
    private static final String RESULT_TYPE = "TUTOR_VERIFICATION";
    private static final String NEW_STATUS = "PENDING";
    private static final long NEW_REVISION = 1;

    private final TutorProfileRepository profiles;
    private final TutorVerificationRepository verifications;
    private final CommandReceipts receipts;
    private final AuditEvents audit;
    private final TransactionOperations transactions;
    private final IdGenerator ids;

    TutorProfileSubmissions(TutorProfileRepository profiles, TutorVerificationRepository verifications,
            CommandReceipts receipts, AuditEvents audit, TransactionOperations transactions, IdGenerator ids) {
        this.profiles = profiles;
        this.verifications = verifications;
        this.receipts = receipts;
        this.audit = audit;
        this.transactions = transactions;
        this.ids = ids;
    }

    public Verification submit(Actor actor, IdempotencyKey key, WritePrecondition.Expected precondition,
            UUID profileVersionId, List<UUID> evidenceIds) {
        if (actor.role() != Actor.Role.TUTOR) {
            throw accessUnavailable();
        }
        validate(evidenceIds);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("precondition", precondition.version());
        payload.put("profileVersionId", profileVersionId.toString());
        payload.put("evidenceUploadIds", evidenceIds.stream().map(UUID::toString).toList());
        return transactions.execute(status -> submitInTransaction(actor, key, precondition, profileVersionId,
                evidenceIds, payload));
    }

    private Verification submitInTransaction(Actor actor, IdempotencyKey key, WritePrecondition.Expected precondition,
            UUID profileVersionId, List<UUID> evidenceIds, Map<String, Object> payload) {
        UUID tutorId = actor.accountId();
        if (!profiles.lockActiveTutor(tutorId)) {
            throw accessUnavailable();
        }
        Receipt receipt = receipts.begin(tutorId, SUBMIT, key.value(), payload);
        if (receipt.replay()) {
            // INV-22: the owner is checked again. INV-20: the first answer, which the verification's later
            // assignment or decision does not change, so a replay never names the verifier (INV-06).
            return verifications.findOwn(receipt.resultId(), tutorId)
                    .map(v -> new Verification(v.id(), v.profileId(), null, NEW_STATUS, v.scope(), NEW_REVISION))
                    .orElseThrow(TutorProfileSubmissions::notFound);
        }

        TutorProfileRepository.Row newest = profiles.lockLatest(tutorId).orElseThrow(TutorProfileSubmissions::notFound);
        // Huy, 20/11/2026: a stale If-Match is 412; the right revision on a version that cannot be sent is 409.
        if (newest.revision() != precondition.version()) {
            throw stale();
        }
        if (!newest.id().equals(profileVersionId) || !"DRAFT".equals(newest.status())) {
            throw notDraft();
        }

        List<UUID> superseded = new ArrayList<>();
        List<UUID> retiring = new ArrayList<>();
        for (UUID pending : verifications.lockOtherPendingVersions(tutorId, newest.id())) {
            List<TutorVerificationRepository.PendingVerification> open = verifications.lockPendingVerifications(pending);
            // NEEDS_INFO is a PENDING verification with a reason (G3-D07-05). Without one the version is
            // waiting, the safe reading, as saveTutorProfile reads it too.
            if (open.isEmpty() || open.stream().anyMatch(v -> v.reason() == null)) {
                throw underReview();
            }
            retiring.add(pending);
            open.forEach(v -> superseded.add(v.id()));
        }

        requireUsable(tutorId, evidenceIds, verifications.lockEvidence(evidenceIds));

        retiring.forEach(verifications::retire);
        for (UUID old : superseded) {
            audit.record(tutorId, receipt.id(), "VERIFICATION_SUPERSEDED", "TUTOR_VERIFICATION", old, "SUPERSEDED");
        }
        evidenceIds.forEach(upload -> verifications.link(newest.id(), upload));
        verifications.submit(newest.id(), newest.revision() + 1);
        UUID id = ids.generateId();
        String scope = verifications.subjectOf(newest.id());
        verifications.insertVerification(id, newest.id(), scope, evidenceKey(evidenceIds));
        receipts.succeed(receipt.id(), RESULT_TYPE, id);
        return new Verification(id, newest.id(), null, NEW_STATUS, scope, NEW_REVISION);
    }

    /** #12 Q3: the SHA-256 of the set's ids in a stable order, so a verification names its evidence. */
    static String evidenceKey(List<UUID> evidenceIds) {
        return CanonicalJson.sha256(evidenceIds.stream().map(UUID::toString).sorted().toList());
    }

    /** G3-D07-04, #12 Q5: 1 to 10 files a submission, none twice. Checked before the receipt. */
    static void validate(List<UUID> evidenceIds) {
        if (evidenceIds.isEmpty() || evidenceIds.size() > EVIDENCE_MAX) {
            throw invalid(List.of(new FieldProblem("evidenceUploadIds", "Cần từ 1 đến 10 minh chứng.")));
        }
        Set<UUID> seen = new HashSet<>();
        List<FieldProblem> repeats = new ArrayList<>();
        for (int i = 0; i < evidenceIds.size(); i++) {
            if (!seen.add(evidenceIds.get(i))) {
                repeats.add(unusable(i));
            }
        }
        if (!repeats.isEmpty()) {
            throw invalid(repeats);
        }
    }

    /** INV-06: unknown, another tutor's and not READY are the same answer, so nobody learns which. */
    private static void requireUsable(UUID tutorId, List<UUID> evidenceIds,
            List<TutorVerificationRepository.Evidence> found) {
        Map<UUID, TutorVerificationRepository.Evidence> byId = found.stream()
                .collect(Collectors.toMap(TutorVerificationRepository.Evidence::id, Function.identity()));
        List<FieldProblem> problems = new ArrayList<>();
        for (int i = 0; i < evidenceIds.size(); i++) {
            TutorVerificationRepository.Evidence evidence = byId.get(evidenceIds.get(i));
            if (evidence == null || !tutorId.equals(evidence.ownerId()) || !"READY".equals(evidence.state())) {
                problems.add(unusable(i));
            }
        }
        if (!problems.isEmpty()) {
            throw invalid(problems);
        }
    }

    private static FieldProblem unusable(int index) {
        return new FieldProblem("evidenceUploadIds[" + index + "]", UNUSABLE_EVIDENCE);
    }

    private static ApiException invalid(List<FieldProblem> problems) {
        return new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "VALIDATION_FAILED", "Dữ liệu chưa hợp lệ.",
                problems);
    }

    private static ApiException stale() {
        return new ApiException(HttpStatus.PRECONDITION_FAILED, "VERSION_CONFLICT",
                "Dữ liệu đã thay đổi. Vui lòng tải lại.");
    }

    private static ApiException notDraft() {
        return new ApiException(HttpStatus.CONFLICT, "TUTOR_PROFILE_NOT_DRAFT",
                "Chỉ gửi được bản nháp mới nhất của hồ sơ. Vui lòng tải lại.");
    }

    private static ApiException underReview() {
        return new ApiException(HttpStatus.CONFLICT, "TUTOR_PROFILE_UNDER_REVIEW",
                "Hồ sơ đang chờ xác minh nên chưa gửi bản mới được.");
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "TUTOR_PROFILE_NOT_FOUND", "Chưa có hồ sơ gia sư.");
    }

    private static ApiException accessUnavailable() {
        return new ApiException(HttpStatus.FORBIDDEN, "ACCESS_UNAVAILABLE",
                "Tài khoản không thể thực hiện thao tác này.");
    }
}
