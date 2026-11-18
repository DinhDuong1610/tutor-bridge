package com.tutorbridge.identity.internal;

import com.tutorbridge.platform.api.Actor;
import com.tutorbridge.platform.api.ApiException;
import com.tutorbridge.platform.api.ApiException.FieldProblem;
import com.tutorbridge.platform.api.CommandReceipts;
import com.tutorbridge.platform.api.CommandReceipts.Receipt;
import com.tutorbridge.platform.api.IdempotencyKey;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.util.IdGenerator;

/**
 * UC03 evidence for the tutor: createEvidenceUpload, uploadEvidenceContent (TB-12b1) and
 * completeEvidenceUpload (TB-12b2).
 *
 * <p>The owner comes from the session (INV-03); another tutor's upload is the same 404 as an unknown id
 * (INV-06). An upload belongs to no profile version yet: TB-12c links READY evidence to a submitted
 * version. Storage I/O never runs inside a database transaction (INV-18), so a PUT stages and moves the
 * bytes first and records the command after; every body it accepts has the declared SHA-256 (#163), so
 * two PUTs, or a PUT racing complete, can only ever leave the declared bytes in place.
 */
@Service
public class EvidenceUploads {

    /** {@code EvidenceUploadTicket}, without the URL, which the web layer builds from the request. */
    public record Ticket(UUID uploadId, Instant expiresAt) {}

    /** {@code TutorEvidenceRecord}. */
    public record Evidence(UUID id, String state, Instant expiresAt) {}

    /** G3-D07-04. */
    static final Set<String> MEDIA_TYPES = Set.of("application/pdf", "image/jpeg", "image/png");
    static final int MAX_BYTES = 5 * 1024 * 1024;
    /** Huy, 18/11/2026 (TB-12b plan): a ticket lasts 15 minutes. */
    static final Duration TICKET_LIFETIME = Duration.ofMinutes(15);
    static final String CREATE = "CREATE_EVIDENCE_UPLOAD";
    static final String PUT_CONTENT = "UPLOAD_EVIDENCE_CONTENT";
    static final String COMPLETE = "COMPLETE_EVIDENCE_UPLOAD";
    private static final String RESULT_TYPE = "EVIDENCE_UPLOAD";
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

    private final EvidenceUploadRepository uploads;
    private final ObjectStore store;
    private final EvidenceScanner scanner;
    private final CommandReceipts receipts;
    private final TransactionOperations transactions;
    private final Clock clock;
    private final IdGenerator ids;

    EvidenceUploads(EvidenceUploadRepository uploads, ObjectStore store, EvidenceScanner scanner,
            CommandReceipts receipts, TransactionOperations transactions, Clock clock, IdGenerator ids) {
        this.uploads = uploads;
        this.store = store;
        this.scanner = scanner;
        this.receipts = receipts;
        this.transactions = transactions;
        this.clock = clock;
        this.ids = ids;
    }

    public Ticket create(Actor actor, IdempotencyKey key, String mediaType, int sizeBytes, String sha256) {
        requireTutorRole(actor);
        validate(mediaType, sizeBytes, sha256);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("mediaType", mediaType);
        payload.put("sizeBytes", sizeBytes);
        payload.put("sha256", sha256);
        return transactions.execute(status -> {
            if (!uploads.lockActiveTutor(actor.accountId())) {
                throw accessUnavailable();
            }
            Receipt receipt = receipts.begin(actor.accountId(), CREATE, key.value(), payload);
            if (receipt.replay()) {
                // INV-22: the owner is checked again; the ticket is the one that command created.
                EvidenceUploadRepository.Row row = owned(actor, receipt.resultId());
                return new Ticket(row.id(), row.expiresAt());
            }
            UUID id = ids.generateId();
            // Microseconds, as timestamptz stores them, so a replay answers the very same expiresAt.
            Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
            Instant expiresAt = now.plus(TICKET_LIFETIME);
            // A fresh random key: no file name, no tutor id, nothing a reader could guess or use.
            uploads.insert(id, actor.accountId(), UUID.randomUUID().toString(), mediaType, sizeBytes, sha256,
                    now, expiresAt);
            receipts.succeed(receipt.id(), RESULT_TYPE, id);
            return new Ticket(id, expiresAt);
        });
    }

    /**
     * uploadEvidenceContent. In order, with nothing written before the checks that need no body:
     * <ol>
     *   <li>Owner, active tutor, declared Content-Type, read without locks (404, 403, 422).
     *   <li>Stage at most sizeBytes + 1 bytes outside any transaction; length or SHA-256 other than
     *       declared is 422 and the staged bytes are removed. This comes before the receipt, so a reused
     *       key with other bytes is 422, not 409.
     *   <li>If the upload still looked open, move the bytes into place.
     *   <li>One transaction: account {@code FOR SHARE}, receipt (a replay answers the record as it is
     *       now), upload {@code FOR UPDATE}, then UPLOADING and expiresAt checked again at commit.
     * </ol>
     * If step 4 refuses after step 3 moved the bytes, they are the declared bytes of an upload that no
     * longer takes any; cleaning them up is a later Issue (TB-12b plan).
     */
    public Evidence putContent(Actor actor, UUID uploadId, IdempotencyKey key, String contentType, InputStream body) {
        requireTutorRole(actor);
        EvidenceUploadRepository.Row seen = uploads.find(uploadId)
                .filter(row -> row.ownerId().equals(actor.accountId()))
                .orElseThrow(EvidenceUploads::notFound);
        if (!uploads.isActiveTutor(actor.accountId())) {
            throw accessUnavailable();
        }
        if (!declaredType(contentType, seen.mediaType())) {
            throw invalid("Loại tệp không khớp với loại đã khai.");
        }
        String bodySha256;
        try (ObjectStore.Staged staged = store.stage(body, seen.sizeBytes())) {
            if (staged.size() != seen.sizeBytes()) {
                throw invalid("Kích thước tệp không khớp với kích thước đã khai.");
            }
            if (!staged.sha256().equals(seen.sha256())) {
                throw invalid("Nội dung tệp không khớp với mã SHA-256 đã khai.");
            }
            bodySha256 = staged.sha256();
            if (seen.state().equals("UPLOADING") && clock.instant().isBefore(seen.expiresAt())) {
                staged.commit(seen.objectKey());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("uploadId", uploadId);
        payload.put("bodySha256", bodySha256);
        return transactions.execute(status -> {
            if (!uploads.lockActiveTutor(actor.accountId())) {
                throw accessUnavailable();
            }
            Receipt receipt = receipts.begin(actor.accountId(), PUT_CONTENT, key.value(), payload);
            if (receipt.replay()) {
                return evidence(owned(actor, receipt.resultId()));
            }
            EvidenceUploadRepository.Row row = uploads.lock(uploadId)
                    .filter(r -> r.ownerId().equals(actor.accountId()))
                    .orElseThrow(EvidenceUploads::notFound);
            if (!row.state().equals("UPLOADING")) {
                throw closed();
            }
            if (!clock.instant().isBefore(row.expiresAt())) {
                throw expired();
            }
            receipts.succeed(receipt.id(), RESULT_TYPE, uploadId);
            return evidence(row);
        });
    }

    /**
     * completeEvidenceUpload (Huy, TB-12b2 plan, 18/11/2026). Reading the bytes is storage I/O, so it
     * sits between two transactions (INV-18):
     * <ol>
     *   <li>Without locks: owner (404), active tutor (403), input equal to what was declared (422), then
     *       the key ({@link CommandReceipts#peek}): used for other input is 409, a finished command with
     *       it is replayed. An upload still UPLOADING must be in time (409 expired) and have its bytes
     *       (409 missing).
     *   <li>Transaction 1, no receipt: account {@code FOR SHARE}, upload {@code FOR UPDATE}, UPLOADING in
     *       time becomes QUARANTINED. A QUARANTINED upload stays so and is checked again: that is how a
     *       call that died after this step goes on.
     *   <li>Outside any transaction, a QUARANTINED upload is scanned; bytes that are gone are FAILED.
     *   <li>Transaction 2: account {@code FOR SHARE}, receipt (a replay answers the record as it is now),
     *       upload {@code FOR UPDATE}; still QUARANTINED becomes READY or FAILED with the receipt.
     *       Anything else means another command finished it: 409 closed.
     * </ol>
     * The command's result, READY or FAILED, commits with its receipt (INV-21). QUARANTINED alone is the
     * intermediate step that INV-18 needs; Đính confirms this reading when reviewing.
     */
    public Evidence complete(Actor actor, UUID uploadId, IdempotencyKey key, int sizeBytes, String sha256) {
        requireTutorRole(actor);
        EvidenceUploadRepository.Row seen = owned(actor, uploadId);
        if (!uploads.isActiveTutor(actor.accountId())) {
            throw accessUnavailable();
        }
        requireDeclared(seen, sizeBytes, sha256);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("uploadId", uploadId);
        payload.put("sizeBytes", sizeBytes);
        payload.put("sha256", sha256);
        // Codex review of #183: the key is checked before transaction 1 changes anything. A key used for
        // other input is 409 here; a finished command with this key is answered as it is now (INV-22).
        Optional<Receipt> earlier = receipts.peek(actor.accountId(), COMPLETE, key.value(), payload);
        if (earlier.isPresent() && earlier.get().state() == CommandReceipts.State.SUCCEEDED) {
            return evidence(owned(actor, earlier.get().resultId()));
        }
        if (seen.state().equals("UPLOADING")) {
            if (!clock.instant().isBefore(seen.expiresAt())) {
                throw expired();
            }
            if (!exists(seen.objectKey())) {
                throw contentMissing();
            }
        }

        String state = transactions.execute(status -> {
            if (!uploads.lockActiveTutor(actor.accountId())) {
                throw accessUnavailable();
            }
            EvidenceUploadRepository.Row row = lockOwned(actor, uploadId);
            if (!row.state().equals("UPLOADING")) {
                return row.state();
            }
            if (!clock.instant().isBefore(row.expiresAt())) {
                throw expired();
            }
            uploads.moveState(uploadId, "UPLOADING", "QUARANTINED");
            return "QUARANTINED";
        });

        EvidenceScanner.Verdict verdict = state.equals("QUARANTINED") ? scan(seen) : null;

        return transactions.execute(status -> {
            if (!uploads.lockActiveTutor(actor.accountId())) {
                throw accessUnavailable();
            }
            Receipt receipt = receipts.begin(actor.accountId(), COMPLETE, key.value(), payload);
            if (receipt.replay()) {
                return evidence(owned(actor, receipt.resultId()));
            }
            EvidenceUploadRepository.Row row = lockOwned(actor, uploadId);
            if (verdict == null || !row.state().equals("QUARANTINED")) {
                throw closed();
            }
            uploads.moveState(uploadId, "QUARANTINED", verdict.name());
            receipts.succeed(receipt.id(), RESULT_TYPE, uploadId);
            return new Evidence(row.id(), verdict.name(), row.expiresAt());
        });
    }

    private boolean exists(String objectKey) {
        try {
            return store.exists(objectKey);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Bytes that are gone count as not the declared bytes. Any other I/O error is a 500, retried later. */
    private EvidenceScanner.Verdict scan(EvidenceUploadRepository.Row row) {
        try (InputStream bytes = store.open(row.objectKey())) {
            return scanner.scan(bytes, row.mediaType(), row.sizeBytes(), row.sha256());
        } catch (java.nio.file.NoSuchFileException gone) {
            return EvidenceScanner.Verdict.FAILED;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** CompleteEvidenceInput must repeat what createEvidenceUpload declared; checked before any receipt. */
    private static void requireDeclared(EvidenceUploadRepository.Row row, int sizeBytes, String sha256) {
        List<FieldProblem> problems = new ArrayList<>();
        if (sizeBytes != row.sizeBytes()) {
            problems.add(new FieldProblem("sizeBytes", "Kích thước không khớp với kích thước đã khai."));
        }
        if (!sha256.equals(row.sha256())) {
            problems.add(new FieldProblem("sha256", "Mã SHA-256 không khớp với mã đã khai."));
        }
        if (!problems.isEmpty()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "VALIDATION_FAILED", "Dữ liệu chưa hợp lệ.",
                    problems);
        }
    }

    private EvidenceUploadRepository.Row lockOwned(Actor actor, UUID uploadId) {
        return uploads.lock(uploadId)
                .filter(row -> row.ownerId().equals(actor.accountId()))
                .orElseThrow(EvidenceUploads::notFound);
    }

    private EvidenceUploadRepository.Row owned(Actor actor, UUID uploadId) {
        return uploads.find(uploadId)
                .filter(row -> row.ownerId().equals(actor.accountId()))
                .orElseThrow(EvidenceUploads::notFound);
    }

    /** Type and subtype only: parameters such as {@code charset} mean nothing for these types. */
    private static boolean declaredType(String contentType, String declared) {
        if (contentType == null) {
            return false;
        }
        try {
            return MediaType.parseMediaType(contentType).equalsTypeAndSubtype(MediaType.parseMediaType(declared));
        } catch (InvalidMediaTypeException e) {
            return false;
        }
    }

    static void validate(String mediaType, int sizeBytes, String sha256) {
        List<FieldProblem> problems = new ArrayList<>();
        if (!MEDIA_TYPES.contains(mediaType)) {
            problems.add(new FieldProblem("mediaType", "Chỉ nhận tệp PDF, JPEG hoặc PNG."));
        }
        if (sizeBytes < 1 || sizeBytes > MAX_BYTES) {
            problems.add(new FieldProblem("sizeBytes", "Tệp cần từ 1 byte đến 5 MiB."));
        }
        if (!SHA256.matcher(sha256).matches()) {
            problems.add(new FieldProblem("sha256", "Mã SHA-256 cần đúng 64 ký tự hex viết thường."));
        }
        if (!problems.isEmpty()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "VALIDATION_FAILED", "Dữ liệu chưa hợp lệ.",
                    problems);
        }
    }

    private static Evidence evidence(EvidenceUploadRepository.Row row) {
        return new Evidence(row.id(), row.state(), row.expiresAt());
    }

    private static void requireTutorRole(Actor actor) {
        if (actor.role() != Actor.Role.TUTOR) {
            throw accessUnavailable();
        }
    }

    private static ApiException invalid(String title) {
        return new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "VALIDATION_FAILED", title);
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "EVIDENCE_UPLOAD_NOT_FOUND", "Không tìm thấy lượt tải lên.");
    }

    private static ApiException contentMissing() {
        return new ApiException(HttpStatus.CONFLICT, "EVIDENCE_CONTENT_MISSING",
                "Chưa nhận được tệp của lượt tải lên này. Hãy tải tệp lên rồi thử lại.");
    }

    private static ApiException expired() {
        return new ApiException(HttpStatus.CONFLICT, "EVIDENCE_UPLOAD_EXPIRED",
                "Lượt tải lên đã hết hạn. Hãy tạo lượt tải lên mới.");
    }

    private static ApiException closed() {
        return new ApiException(HttpStatus.CONFLICT, "EVIDENCE_UPLOAD_CLOSED",
                "Lượt tải lên này đã đóng, không nhận thêm tệp.");
    }

    private static ApiException accessUnavailable() {
        return new ApiException(HttpStatus.FORBIDDEN, "ACCESS_UNAVAILABLE",
                "Tài khoản không thể thực hiện thao tác này.");
    }
}
