package com.tutorbridge.identity.internal;

import com.tutorbridge.platform.api.Actor;
import com.tutorbridge.platform.api.ApiException;
import com.tutorbridge.platform.api.AuditEvents;
import com.tutorbridge.platform.api.CommandReceipts;
import com.tutorbridge.platform.api.CommandReceipts.Receipt;
import com.tutorbridge.platform.api.ApiException.FieldProblem;
import com.tutorbridge.platform.api.IdempotencyKey;
import com.tutorbridge.platform.api.WritePrecondition;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

/**
 * decideVerification (UC03, TB-13; AC-UC03-02). The assigned verifier records VERIFIED, REJECTED or
 * NEEDS_INFO with a scope and a reason, on the exact revision they read (If-Match).
 *
 * <p>One transaction, in the lock order of docs/handoff/TB-13.md (INV-16): the verifier's account (FOR
 * SHARE, ACTIVE ADMIN), the receipt, the tutor's versions it changes (FOR UPDATE by version), the record
 * (FOR UPDATE), then the VERIFY row (FOR SHARE). Every check runs under these locks; the record, the
 * versions, the audit row and the receipt commit together (INV-21), with no outside I/O (INV-18). Any
 * refusal rolls the whole command back, receipt included.
 *
 * <ul>
 *   <li>VERIFIED: the version becomes APPROVED and current; the previous current one stops being shown
 *       and stays APPROVED (OP-24 (1)).
 *   <li>REJECTED: the version is REJECTED; the current one, if any, stays public (OP-24 (1)).
 *   <li>NEEDS_INFO: the record stays PENDING with the reason (G3-D07-05) and a decision time (Huy, #13).
 * </ul>
 */
@Service
public class VerificationDecisions {

    static final String DECIDE = "DECIDE_VERIFICATION";
    private static final String RESULT_TYPE = "TUTOR_VERIFICATION";
    private static final Set<String> RESULTS = Set.of("VERIFIED", "REJECTED", "NEEDS_INFO");
    private static final int SCOPE_MAX = 200;
    private static final int REASON_MAX = 2000;

    /** {@code VerificationDecisionInput}. */
    public record Decision(UUID profileVersionId, String result, String scope, String reason) {}

    private final AssignedVerificationRepository verifications;
    private final CommandReceipts receipts;
    private final AuditEvents audit;
    private final TransactionOperations transactions;
    private final Clock clock;

    VerificationDecisions(AssignedVerificationRepository verifications, CommandReceipts receipts, AuditEvents audit,
            TransactionOperations transactions, Clock clock) {
        this.verifications = verifications;
        this.receipts = receipts;
        this.audit = audit;
        this.transactions = transactions;
        this.clock = clock;
    }

    public TutorProfileSubmissions.Verification decide(Actor actor, UUID verificationId, IdempotencyKey key,
            WritePrecondition.Expected precondition, Decision decision) {
        if (actor.role() != Actor.Role.ADMIN) {
            throw accessDenied();
        }
        validate(decision);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("verificationId", verificationId.toString());
        payload.put("precondition", precondition.version());
        payload.put("profileVersionId", decision.profileVersionId().toString());
        payload.put("result", decision.result());
        payload.put("scope", decision.scope());
        payload.put("reason", decision.reason());
        return transactions.execute(status -> decideInTransaction(actor, verificationId, key, precondition, decision,
                payload));
    }

    private TutorProfileSubmissions.Verification decideInTransaction(Actor actor, UUID verificationId,
            IdempotencyKey key, WritePrecondition.Expected precondition, Decision decision, Map<String, Object> payload) {
        UUID me = actor.accountId();
        if (!verifications.lockActiveAdmin(me)) {
            throw accessDenied();
        }
        Receipt receipt = receipts.begin(me, DECIDE, key.value(), payload);
        if (receipt.replay()) {
            // INV-22: still assigned to the caller. INV-20: the first answer follows from the same payload,
            // so a later decision on the record does not change what a retry of this one gets.
            if (!verifications.isAssigned(verificationId, me)) {
                throw notFound();
            }
            return answer(verificationId, me, decision, precondition.version() + 1);
        }

        AssignedVerificationRepository.Target target = verifications.findTarget(verificationId)
                .orElseThrow(VerificationDecisions::notFound);
        List<AssignedVerificationRepository.Version> versions =
                verifications.lockVersions(target.tutorId(), target.profileId());
        AssignedVerificationRepository.Locked record = verifications.lockVerification(verificationId)
                .filter(v -> me.equals(v.assignedTo()))
                .orElseThrow(VerificationDecisions::notFound);
        if (!verifications.lockVerify(me)) {
            throw accessDenied();
        }
        AssignedVerificationRepository.Version version = versions.stream()
                .filter(v -> v.id().equals(record.profileId())).findFirst()
                .orElseThrow(() -> new IllegalStateException("A record's version is always there"));

        // In the order of docs/handoff/TB-13.md, so each refusal is the same whatever else is wrong.
        if (target.tutorId().equals(me)) {
            throw selfReview();
        }
        if ("RETIRED".equals(version.status())) {
            throw superseded();
        }
        if (record.revision() != precondition.version()) {
            throw WritePrecondition.stale();
        }
        if (!record.profileId().equals(decision.profileVersionId())) {
            throw superseded();
        }
        if (!"PENDING".equals(record.status())) {
            throw decided();
        }

        Instant now = clock.instant();
        long revision = record.revision() + 1;
        String stored = "NEEDS_INFO".equals(decision.result()) ? "PENDING" : decision.result();
        if ("VERIFIED".equals(decision.result())) {
            // The unique current index is checked per row, so the old one goes first.
            versions.stream().filter(v -> v.current() && !v.id().equals(version.id()))
                    .forEach(v -> verifications.retireCurrent(v.id()));
            verifications.settleVersion(version.id(), "APPROVED", true);
        } else if ("REJECTED".equals(decision.result())) {
            verifications.settleVersion(version.id(), "REJECTED", false);
        }
        verifications.decide(verificationId, stored, decision.scope(), decision.reason(), me, now, revision);
        audit.record(me, receipt.id(), "VERIFICATION_DECIDED", RESULT_TYPE, verificationId, decision.result());
        receipts.succeed(receipt.id(), RESULT_TYPE, verificationId);
        return answer(verificationId, me, decision, revision);
    }

    /** {@code VerificationRecord}: NEEDS_INFO is stored as PENDING (G3-D07-05). */
    private static TutorProfileSubmissions.Verification answer(UUID id, UUID me, Decision decision, long revision) {
        String status = "NEEDS_INFO".equals(decision.result()) ? "PENDING" : decision.result();
        return new TutorProfileSubmissions.Verification(id, decision.profileVersionId(), me, status, decision.scope(),
                revision);
    }

    /** The contract's limits in code points, as the other commands count them. */
    static void validate(Decision decision) {
        List<FieldProblem> problems = new ArrayList<>();
        if (decision.result() == null || !RESULTS.contains(decision.result())) {
            problems.add(new FieldProblem("result", "Chọn một kết quả."));
        }
        if (!within(decision.scope(), SCOPE_MAX)) {
            problems.add(new FieldProblem("scope", "Phạm vi cần từ 1 đến 200 ký tự."));
        }
        if (!within(decision.reason(), REASON_MAX)) {
            problems.add(new FieldProblem("reason", "Lý do cần từ 1 đến 2.000 ký tự."));
        }
        if (decision.profileVersionId() == null) {
            problems.add(new FieldProblem("profileVersionId", "Thiếu phiên bản hồ sơ."));
        }
        if (!problems.isEmpty()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "VALIDATION_FAILED", "Dữ liệu chưa hợp lệ.",
                    problems);
        }
    }

    private static boolean within(String text, int max) {
        if (text == null) {
            return false;
        }
        int length = text.codePointCount(0, text.length());
        return length >= 1 && length <= max;
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "VERIFICATION_NOT_FOUND", "Không tìm thấy hồ sơ cần xác minh.");
    }

    /** AD04 "Tự duyệt", approved wording (INV-05). */
    private static ApiException selfReview() {
        return new ApiException(HttpStatus.FORBIDDEN, "SELF_REVIEW_FORBIDDEN",
                "Bạn không thể thẩm định hồ sơ của chính mình. Hồ sơ này cần người xác minh khác.");
    }

    /** AD04 "Bản cũ", approved wording (OP-24). */
    private static ApiException superseded() {
        return new ApiException(HttpStatus.CONFLICT, "VERIFICATION_SUPERSEDED",
                "Hồ sơ đã có phiên bản mới trong lúc bạn xem. Kết quả chưa được ghi. Tải lại để thẩm định đúng phiên bản.");
    }

    /** Agent's wording, not in AD04: Đính to approve. */
    private static ApiException decided() {
        return new ApiException(HttpStatus.CONFLICT, "VERIFICATION_DECIDED",
                "Hồ sơ này đã có kết quả xác minh. Kết quả mới chưa được ghi.");
    }

    private static ApiException accessDenied() {
        return new ApiException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Tài khoản không thể thực hiện thao tác này.");
    }
}
