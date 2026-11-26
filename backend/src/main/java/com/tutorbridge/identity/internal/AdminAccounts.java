package com.tutorbridge.identity.internal;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.util.IdGenerator;

/**
 * The admin-setup command of TB-13 (Đính 23/11/2026 on #13): there is no API to create an ADMIN in Lát 1.
 * Creates the ADMIN, or resets an existing one, with a password, a new TOTP enrollment and the VERIFY
 * capability, and writes the {@code otpauth://} URI once to a new file the operator names. The URI holds the
 * secret, so it never goes to stdout or the log, which a deployed service sends to its log store (INV-63).
 *
 * <p>No file I/O inside the transaction (INV-18, Codex's review of #217): the URI is first written to a
 * private temporary file next to the target, the account is then changed in one transaction, and only after
 * the commit the temporary file is moved to its name. A refused or failed transaction deletes the temporary
 * file, so no secret is left that no account uses. If the move fails after the commit, the temporary file
 * is deleted too: the account then has a secret nobody holds, so nobody can sign in with it, and running the
 * command again gives a new one. Only a process killed between the commit and the rename leaves the staged
 * file behind, with the secret in use; {@link AdminSetupRunner} tells the operator to delete it and run again.
 *
 * <p>Running it again for the same email replaces the secret and the password and raises the account epoch,
 * which ends every earlier session of that admin.
 *
 * <p>Then, in a second transaction, the PENDING verifications nobody has are shared out by turns among
 * every verifier (decision 4, Đính 23/11/2026 on #13). A second transaction, so it takes its locks in the
 * order of the verification commands (INV-16): the first one already holds the VERIFY row. If it fails, the
 * account stays as set up and running the command again assigns them.
 */
@Service
public class AdminAccounts {

    private static final String DISPLAY_NAME = "Quản trị viên";

    /** The admin's account id and how many waiting verifications were assigned. */
    public record Result(UUID accountId, int assigned) {}

    private final AdminMfaRepository admins;
    private final VerifierRotation rotation;
    private final AccountSecrets secrets;
    private final PasswordHasher hasher;
    private final TransactionOperations transactions;
    private final Clock clock;
    private final IdGenerator ids;

    AdminAccounts(AdminMfaRepository admins, VerifierRotation rotation, AccountSecrets secrets, PasswordHasher hasher,
            TransactionOperations transactions, Clock clock, IdGenerator ids) {
        this.admins = admins;
        this.rotation = rotation;
        this.secrets = secrets;
        this.hasher = hasher;
        this.transactions = transactions;
        this.clock = clock;
        this.ids = ids;
    }

    /** Refuses before writing anything when an input is wrong. */
    public Result setUp(String rawEmail, String password, Path enrollmentFile) {
        UUID id = setUpAccount(rawEmail, password, enrollmentFile);
        return new Result(id, assignWaiting());
    }

    /** The versions, then their verifications, then the VERIFY rows; oldest submission first. */
    int assignWaiting() {
        Integer assigned = transactions.execute(status -> {
            List<AdminMfaRepository.Waiting> waiting = admins.lockWaiting(admins.lockVersionsWaitingForAVerifier());
            VerifierRotation.Turns turns = rotation.lock();
            int count = 0;
            for (AdminMfaRepository.Waiting w : waiting.stream()
                    .sorted(Comparator.comparing(AdminMfaRepository.Waiting::submittedAt)
                            .thenComparing(w -> w.id().toString()))
                    .toList()) {
                UUID verifier = turns.next(w.tutorId());
                if (verifier != null) {
                    admins.assign(w.id(), verifier);
                    count++;
                }
            }
            return count;
        });
        return assigned == null ? 0 : assigned;
    }

    private UUID setUpAccount(String rawEmail, String password, Path enrollmentFile) {
        String email = AccountSecrets.normalizeEmail(rawEmail);
        if (!email.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
            throw new IllegalArgumentException("admin-setup: the email is not an email address");
        }
        if (password == null || !PasswordHasher.acceptableLength(password)) {
            throw new IllegalArgumentException("admin-setup: the password needs 15 to 128 characters");
        }
        Path target = enrollmentFile.toAbsolutePath().normalize();
        if (Files.exists(target)) {
            throw new IllegalArgumentException("admin-setup: " + target + " exists; name a new file");
        }
        if (!Files.isDirectory(target.getParent())) {
            throw new IllegalArgumentException("admin-setup: the folder of " + target + " does not exist");
        }
        String digest = secrets.emailDigest(email);
        String passwordHash = hasher.hash(password);
        byte[] secret = secrets.newTotpSecret();
        Instant now = clock.instant();

        Path staged = stage(target.getParent(), Totp.enrollmentUri(secret, email));
        UUID id;
        try {
            id = transactions.execute(status -> changeAccount(digest, email, passwordHash, secret, now));
        } catch (RuntimeException e) {
            deleteQuietly(staged);
            throw e;
        }
        publish(staged, target);
        return id;
    }

    /** Locks the enrollment, then the account (INV-16), then writes; no I/O outside the database here. */
    private UUID changeAccount(String digest, String email, String passwordHash, byte[] secret, Instant now) {
        admins.findAccountId(digest).ifPresent(admins::lockEnrollment);
        UUID id = admins.findAccountRole(digest).map(found -> {
            if (!"ADMIN".equals(found.role())) {
                throw new IllegalArgumentException("admin-setup: this email belongs to an account that is not an ADMIN");
            }
            admins.endSessions(found.id());
            return found.id();
        }).orElseGet(() -> {
            UUID created = ids.generateId();
            admins.insertAdmin(created, digest, secrets.encryptEmail(created, email), DISPLAY_NAME, now);
            return created;
        });
        admins.setPassword(id, passwordHash, PasswordHasher.POLICY, now);
        admins.enroll(id, secrets.encryptTotpSecret(id, secret), now);
        admins.grantVerify(id, now);
        return id;
    }

    /** A new private file in the target's folder, so the later move stays on one file system. */
    private static Path stage(Path folder, String uri) {
        try {
            Path staged = folder.getFileSystem().supportedFileAttributeViews().contains("posix")
                    ? Files.createTempFile(folder, ".admin-setup-", ".tmp",
                            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))
                    : Files.createTempFile(folder, ".admin-setup-", ".tmp");
            try {
                Files.writeString(staged, uri + System.lineSeparator(), StandardCharsets.US_ASCII);
            } catch (IOException e) {
                deleteQuietly(staged);
                throw e;
            }
            return staged;
        } catch (IOException e) {
            throw new UncheckedIOException("admin-setup: cannot write in " + folder, e);
        }
    }

    /**
     * A rename within the folder. Without REPLACE_EXISTING it never replaces a file (an atomic move may on
     * POSIX): one made under the name meanwhile is kept and this run fails.
     */
    private static void publish(Path staged, Path target) {
        try {
            Files.move(staged, target);
        } catch (IOException e) {
            deleteQuietly(staged);
            throw new IllegalStateException("admin-setup: the account was set up but " + target
                    + " could not be written; nobody holds its new secret, run the command again", e);
        }
    }

    private static void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // The operator sees the first error; the file holds a secret no account uses.
        }
    }
}
