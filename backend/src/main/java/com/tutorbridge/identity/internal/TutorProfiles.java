package com.tutorbridge.identity.internal;

import com.tutorbridge.platform.api.Actor;
import com.tutorbridge.platform.api.ApiException;
import com.tutorbridge.platform.api.ApiException.FieldProblem;
import com.tutorbridge.platform.api.CommandReceipts;
import com.tutorbridge.platform.api.CommandReceipts.Receipt;
import com.tutorbridge.platform.api.IdempotencyKey;
import com.tutorbridge.platform.api.WritePrecondition;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.util.IdGenerator;

/**
 * UC03 for the tutor (TB-12a): getOwnTutorProfile, saveTutorProfile.
 *
 * <p>A profile is a series of versions. The newest one is what the tutor reads and edits. A DRAFT is
 * edited in place; a version that was approved, rejected or needs more information (OP-24) is never
 * changed (INV-54): saving creates the next version as a new DRAFT. A version waiting for its verifier
 * cannot be edited (OP-24). The owner always comes from the session (INV-03).
 *
 * <p>The revision runs on across versions, so an ETag taken from an older version can never match a
 * newer one (INV-23).
 *
 * <p>Transaction: the tutor's account (FOR SHARE, ACTIVE), the receipt, the newest version (FOR
 * UPDATE), the version condition, the write and the receipt result in the same commit (INV-21). No
 * outside I/O inside it (INV-18).
 */
@Service
public class TutorProfiles {

    /** The {@code TutorProfileDraft} schema of the contract. */
    public record Profile(UUID id, int version, long revision, String status, String description,
            int experienceMonths, int rateVnd60) {}

    /** {@code TutorProfileInput}. */
    public record Input(String description, int experienceMonths, int rateVnd60, String subject) {}

    static final int DESCRIPTION_MIN = 50;
    static final int DESCRIPTION_MAX = 3000;
    static final int EXPERIENCE_MAX = 600;
    /** R0 segment (INV-30). */
    static final String SUBJECT = "MATH8_ALGEBRA";
    static final String SAVE = "SAVE_TUTOR_PROFILE";
    static final String RESULT_TYPE = "TUTOR_PROFILE";
    private static final String VERSION_KEY = "tutor_profiles_tutor_id_version_key";

    private final TutorProfileRepository profiles;
    private final CommandReceipts receipts;
    private final TransactionOperations transactions;
    private final IdGenerator ids;

    TutorProfiles(TutorProfileRepository profiles, CommandReceipts receipts, TransactionOperations transactions,
            IdGenerator ids) {
        this.profiles = profiles;
        this.receipts = receipts;
        this.transactions = transactions;
        this.ids = ids;
    }

    /** No version yet is 404, so GS02 knows to create the first one with {@code If-None-Match: *}. */
    public Profile get(Actor actor) {
        requireTutorRole(actor);
        return profiles.findLatest(actor.accountId())
                .map(TutorProfiles::profile)
                .orElseThrow(TutorProfiles::notFound);
    }

    public Profile save(Actor actor, IdempotencyKey key, WritePrecondition precondition, Input input) {
        requireTutorRole(actor);
        validate(input);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("precondition", precondition instanceof WritePrecondition.Expected expected
                ? expected.version() : "*");
        payload.put("description", input.description());
        payload.put("experienceMonths", input.experienceMonths());
        payload.put("rateVnd60", input.rateVnd60());
        payload.put("subject", input.subject());
        try {
            return transactions.execute(status -> saveInTransaction(actor, key, precondition, input, payload));
        } catch (DuplicateKeyException e) {
            // Two first saves at once: neither had a version to lock, the unique key let one through.
            if (e.getMessage() != null && e.getMessage().contains(VERSION_KEY)) {
                throw WritePrecondition.stale();
            }
            throw e;
        }
    }

    private Profile saveInTransaction(Actor actor, IdempotencyKey key, WritePrecondition precondition, Input input,
            Map<String, Object> payload) {
        if (!profiles.lockActiveTutor(actor.accountId())) {
            throw accessUnavailable();
        }
        Receipt receipt = receipts.begin(actor.accountId(), SAVE, key.value(), payload);
        if (receipt.replay()) {
            // INV-22: the owner is checked again; the answer is the version that command wrote, as it is now.
            return profiles.find(receipt.resultId(), actor.accountId())
                    .map(TutorProfiles::profile)
                    .orElseThrow(TutorProfiles::notFound);
        }
        Optional<TutorProfileRepository.Row> latest = profiles.lockLatest(actor.accountId());
        requireMatches(precondition, latest);

        Profile saved;
        if (latest.isEmpty()) {
            saved = insertDraft(actor, 1, 1, input);
        } else {
            TutorProfileRepository.Row current = latest.get();
            long revision = current.revision() + 1;
            switch (current.status()) {
                case "DRAFT" -> {
                    profiles.updateDraft(current.id(), revision, input.description(), input.experienceMonths(),
                            input.rateVnd60(), input.subject());
                    saved = new Profile(current.id(), current.version(), revision, "DRAFT", input.description(),
                            input.experienceMonths(), input.rateVnd60());
                }
                case "PENDING" -> {
                    if (!profiles.needsInformation(current.id())) {
                        throw underReview();
                    }
                    saved = insertDraft(actor, current.version() + 1, revision, input);
                }
                default -> saved = insertDraft(actor, current.version() + 1, revision, input);
            }
        }
        receipts.succeed(receipt.id(), RESULT_TYPE, saved.id());
        return saved;
    }

    private Profile insertDraft(Actor actor, int version, long revision, Input input) {
        UUID id = ids.generateId();
        profiles.insertDraft(id, actor.accountId(), version, revision, input.description(), input.experienceMonths(),
                input.rateVnd60(), input.subject());
        return new Profile(id, version, revision, "DRAFT", input.description(), input.experienceMonths(),
                input.rateVnd60());
    }

    private static void requireMatches(WritePrecondition precondition, Optional<TutorProfileRepository.Row> latest) {
        boolean matches = switch (precondition) {
            case WritePrecondition.CreateNew create -> latest.isEmpty();
            case WritePrecondition.Expected expected ->
                    latest.isPresent() && latest.get().revision() == expected.version();
        };
        if (!matches) {
            throw WritePrecondition.stale();
        }
    }

    /**
     * AC-UC03-01: experience 0 to 600 months, 0 included; a positive price with no upper bound (OP-20).
     * Lengths count code points, as the contract's JSON Schema and PostgreSQL's length() do. Checked
     * before the receipt, so a bad body leaves nothing.
     */
    static void validate(Input input) {
        List<FieldProblem> problems = new ArrayList<>();
        String description = input.description();
        int length = description.codePointCount(0, description.length());
        if (length < DESCRIPTION_MIN || length > DESCRIPTION_MAX) {
            problems.add(new FieldProblem("description", "Mô tả cần từ 50 đến 3000 ký tự."));
        }
        if (input.experienceMonths() < 0 || input.experienceMonths() > EXPERIENCE_MAX) {
            problems.add(new FieldProblem("experienceMonths", "Kinh nghiệm cần từ 0 đến 600 tháng."));
        }
        if (input.rateVnd60() < 1) {
            problems.add(new FieldProblem("rateVnd60", "Học phí phải là số nguyên dương."));
        }
        if (!SUBJECT.equals(input.subject())) {
            problems.add(new FieldProblem("subject", "Hiện chỉ nhận môn Đại số lớp 8."));
        }
        if (!problems.isEmpty()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "VALIDATION_FAILED", "Dữ liệu chưa hợp lệ.",
                    problems);
        }
    }

    private static Profile profile(TutorProfileRepository.Row row) {
        return new Profile(row.id(), row.version(), row.revision(), row.status(), row.description(),
                row.experienceMonths(), row.rateVnd60());
    }

    private static void requireTutorRole(Actor actor) {
        if (actor.role() != Actor.Role.TUTOR) {
            throw accessUnavailable();
        }
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "TUTOR_PROFILE_NOT_FOUND", "Chưa có hồ sơ gia sư.");
    }

    private static ApiException underReview() {
        return new ApiException(HttpStatus.CONFLICT, "TUTOR_PROFILE_UNDER_REVIEW",
                "Hồ sơ đang chờ xác minh nên chưa sửa được.");
    }

    private static ApiException accessUnavailable() {
        return new ApiException(HttpStatus.FORBIDDEN, "ACCESS_UNAVAILABLE",
                "Tài khoản không thể thực hiện thao tác này.");
    }
}
