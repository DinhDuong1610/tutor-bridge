package com.tutorbridge.identity.internal;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Clock;
import java.time.Instant;
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
 * <p>Running it again for the same email replaces the secret and the password and raises the account epoch,
 * which ends every earlier session of that admin.
 */
@Service
public class AdminAccounts {

    private static final String DISPLAY_NAME = "Quản trị viên";

    private final AdminMfaRepository admins;
    private final AccountSecrets secrets;
    private final PasswordHasher hasher;
    private final TransactionOperations transactions;
    private final Clock clock;
    private final IdGenerator ids;

    AdminAccounts(AdminMfaRepository admins, AccountSecrets secrets, PasswordHasher hasher,
            TransactionOperations transactions, Clock clock, IdGenerator ids) {
        this.admins = admins;
        this.secrets = secrets;
        this.hasher = hasher;
        this.transactions = transactions;
        this.clock = clock;
        this.ids = ids;
    }

    /** The admin's account id. Refuses before writing anything when an input is wrong. */
    public UUID setUp(String rawEmail, String password, Path enrollmentFile) {
        String email = AccountSecrets.normalizeEmail(rawEmail);
        if (!email.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
            throw new IllegalArgumentException("admin-setup: the email is not an email address");
        }
        if (password == null || !PasswordHasher.acceptableLength(password)) {
            throw new IllegalArgumentException("admin-setup: the password needs 15 to 128 characters");
        }
        if (Files.exists(enrollmentFile)) {
            throw new IllegalArgumentException("admin-setup: " + enrollmentFile + " exists; name a new file");
        }
        String digest = secrets.emailDigest(email);
        String passwordHash = hasher.hash(password);
        byte[] secret = secrets.newTotpSecret();
        Instant now = clock.instant();
        try {
            return transactions.execute(status -> {
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
                // Last, so a file that cannot be written rolls the account back.
                write(enrollmentFile, Totp.enrollmentUri(secret, email));
                return id;
            });
        } catch (RuntimeException e) {
            deleteQuietly(enrollmentFile, e);
            throw e;
        }
    }

    private static void write(Path file, String uri) {
        try {
            if (file.getFileSystem().supportedFileAttributeViews().contains("posix")) {
                Files.createFile(file, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
            } else {
                Files.createFile(file);
            }
            Files.writeString(file, uri + System.lineSeparator(), StandardCharsets.US_ASCII, StandardOpenOption.WRITE);
        } catch (FileAlreadyExistsException e) {
            throw new IllegalArgumentException("admin-setup: " + file + " exists; name a new file");
        } catch (IOException e) {
            throw new UncheckedIOException("admin-setup: cannot write " + file, e);
        }
    }

    /** Only a file this run made: an existing file was refused before the transaction. */
    private static void deleteQuietly(Path file, RuntimeException cause) {
        if (cause instanceof IllegalArgumentException && cause.getMessage() != null && cause.getMessage().contains("exists")) {
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // The operator sees the first error; a file left behind holds a secret no account uses.
        }
    }
}
