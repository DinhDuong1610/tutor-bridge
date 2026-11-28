package com.tutorbridge.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tutorbridge.IntegrationTest;
import com.tutorbridge.PostgresTestSupport;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * #182: the evidence cleanup job. Real PostgreSQL; the store is a directory of this test only, so the job
 * never removes the bytes of another test, and every check looks at its own uploads. The approved default keep
 * times are 24 h past the ticket, 7 days for FAILED, and 24 h for orphans.
 */
class EvidenceCleanupTest extends IntegrationTest {

    private static final Duration DAY = Duration.ofDays(1);

    @Autowired
    EvidenceUploadRepository uploads;

    @Autowired
    TransactionTemplate tx;

    @Autowired
    ApplicationContext context;

    @TempDir
    Path dir;

    private LocalObjectStore store;
    private EvidenceCleanup cleanup;
    private UUID tutor;

    @BeforeEach
    void setUp() {
        store = new LocalObjectStore(new EvidenceStoreProperties(dir.toString()));
        cleanup = cleanup(100);
        tutor = UUID.randomUUID();
        jdbc.sql("insert into accounts(id, role, status) values (?, 'TUTOR', 'ACTIVE')").param(tutor).update();
    }

    private EvidenceCleanup cleanup(int batchSize) {
        return new EvidenceCleanup(uploads, store, tx, Clock.systemUTC(), batchSize, 50,
                Duration.ofHours(24), Duration.ofDays(7), Duration.ofHours(24));
    }

    private record Upload(UUID id, String key) {}

    /**
     * An upload whose 15 minute ticket expired {@code expiredAgo} ago (negative: still in time). A FAILED one
     * failed when its ticket expired, the oldest it can be.
     */
    private Upload upload(String state, Duration expiredAgo) {
        return upload(state, expiredAgo, state.equals("FAILED") ? expiredAgo : null);
    }

    /** {@code failedAgo}: when it became FAILED (V21), null for no failure time. */
    private Upload upload(String state, Duration expiredAgo, Duration failedAgo) {
        UUID id = UUID.randomUUID();
        String key = UUID.randomUUID().toString();
        jdbc.sql("""
                insert into tutor_evidence_uploads(id, owner_id, object_key, media_type, size_bytes, sha256, state,
                                                   created_at, expires_at, failed_at)
                values (?, ?, ?, 'application/pdf', 1, repeat('a', 64), ?,
                        now() - cast(? as interval) - interval '15 minutes', now() - cast(? as interval),
                        now() - cast(? as interval))
                """).params(id, tutor, key, state, expiredAgo.toString(), expiredAgo.toString(),
                        failedAgo == null ? null : failedAgo.toString()).update();
        return new Upload(id, key);
    }

    /** Committed bytes under {@code key}, last written {@code age} ago. */
    private void bytes(String key, Duration age) throws Exception {
        try (ObjectStore.Staged staged = store.stage(new ByteArrayInputStream(new byte[] {1}), 1)) {
            staged.commit(key);
        }
        Files.setLastModifiedTime(object(key), FileTime.from(Instant.now().minus(age)));
    }

    private Path object(String key) {
        return dir.resolve("objects").resolve(key);
    }

    private String state(Upload upload) {
        return jdbc.sql("select state from tutor_evidence_uploads where id = ?").param(upload.id())
                .query(String.class).single();
    }

    /** A DRAFT version of the tutor's profile with {@code upload} linked, as TB-12c does (V11). */
    private void link(Upload upload) {
        UUID profile = UUID.randomUUID();
        jdbc.sql("""
                insert into tutor_profiles(id, tutor_id, version, status, description, experience_months, rate_vnd_60,
                                           current)
                values (?, ?, 1, 'DRAFT', repeat('x', 50), 24, 100000, false)
                """).params(profile, tutor).update();
        jdbc.sql("insert into tutor_profile_evidence(profile_id, upload_id) values (?, ?)")
                .params(profile, upload.id()).update();
    }

    @Test
    void an_upload_expired_past_the_keep_time_is_deleted_with_its_bytes_and_one_still_kept_is_not()
            throws Exception {
        Upload old = upload("UPLOADING", DAY.plusHours(1));
        bytes(old.key(), DAY.plusHours(2));
        Upload neverPut = upload("UPLOADING", DAY.plusHours(1));
        Upload recent = upload("UPLOADING", Duration.ofHours(23));
        bytes(recent.key(), DAY.plusHours(2));
        Upload open = upload("UPLOADING", Duration.ofMinutes(-10));
        bytes(open.key(), Duration.ofMinutes(1));

        assertThat(cleanup.runOnce().uploadsDeleted()).isGreaterThanOrEqualTo(2);

        assertThat(state(old)).isEqualTo("DELETED");
        assertThat(object(old.key())).doesNotExist();
        assertThat(state(neverPut)).isEqualTo("DELETED");
        assertThat(state(recent)).isEqualTo("UPLOADING");
        assertThat(object(recent.key())).exists();
        assertThat(state(open)).isEqualTo("UPLOADING");
        assertThat(object(open.key())).exists();
    }

    @Test
    void a_failed_upload_older_than_its_keep_time_is_deleted_and_a_recent_one_is_kept() throws Exception {
        Upload old = upload("FAILED", Duration.ofDays(8));
        bytes(old.key(), Duration.ofDays(8));
        Upload recent = upload("FAILED", Duration.ofDays(6));
        bytes(recent.key(), Duration.ofDays(6));

        cleanup.runOnce();

        assertThat(state(old)).isEqualTo("DELETED");
        assertThat(object(old.key())).doesNotExist();
        assertThat(state(recent)).isEqualTo("FAILED");
        assertThat(object(recent.key())).exists();
    }

    /**
     * Đính on #262: complete goes on with a QUARANTINED upload after its ticket expired (a PROCESSING receipt
     * replayed after a crash) and fails it only then. Its seven days count from the failure, not the ticket.
     */
    @Test
    void a_failure_newer_than_its_expired_ticket_is_kept_seven_days_from_the_failure() throws Exception {
        Upload justFailed = upload("QUARANTINED", Duration.ofDays(8));
        bytes(justFailed.key(), Duration.ofDays(8));
        // As complete leaves it (EvidenceUploads, transaction 2), with the app's clock.
        tx.executeWithoutResult(status -> uploads.moveToFailed(justFailed.id(), Instant.now()));

        cleanup.runOnce();

        assertThat(state(justFailed)).isEqualTo("FAILED");
        assertThat(object(justFailed.key())).exists();
    }

    @Test
    void a_failed_upload_with_no_failure_time_is_left_alone() throws Exception {
        // Failed by code before V21 after the migration ran: when is not known, so nothing is removed.
        Upload unknown = upload("FAILED", Duration.ofDays(30), null);
        bytes(unknown.key(), Duration.ofDays(30));

        cleanup.runOnce();

        assertThat(state(unknown)).isEqualTo("FAILED");
        assertThat(object(unknown.key())).exists();
    }

    @Test
    void ready_and_quarantined_bytes_are_never_touched_however_old() throws Exception {
        Upload ready = upload("READY", Duration.ofDays(30));
        bytes(ready.key(), Duration.ofDays(30));
        Upload quarantined = upload("QUARANTINED", Duration.ofDays(30));
        bytes(quarantined.key(), Duration.ofDays(30));

        cleanup.runOnce();

        assertThat(state(ready)).isEqualTo("READY");
        assertThat(object(ready.key())).exists();
        assertThat(state(quarantined)).isEqualTo("QUARANTINED");
        assertThat(object(quarantined.key())).exists();
    }

    @Test
    void an_upload_linked_to_a_profile_version_is_never_deleted_whatever_its_state() throws Exception {
        Upload linked = upload("READY", Duration.ofDays(30));
        bytes(linked.key(), Duration.ofDays(30));
        link(linked);
        // Not a state the service produces: this checks the link alone protects the bytes.
        jdbc.sql("update tutor_evidence_uploads set state = 'FAILED', failed_at = now() - interval '30 days' where id = ?")
                .param(linked.id()).update();

        cleanup.runOnce();

        assertThat(state(linked)).isEqualTo("FAILED");
        assertThat(object(linked.key())).exists();
    }

    @Test
    void old_orphan_bytes_are_removed_and_recent_ones_kept() throws Exception {
        String noRow = UUID.randomUUID().toString();
        bytes(noRow, DAY.plusHours(1));
        Upload deleted = upload("DELETED", Duration.ofDays(30));
        bytes(deleted.key(), DAY.plusHours(1));
        // Bytes of a PUT the database refused a moment ago, or one still on its way to its row.
        String fresh = UUID.randomUUID().toString();
        bytes(fresh, Duration.ofHours(1));
        // A name this service never minted is not its to remove.
        Path foreign = Files.writeString(dir.resolve("objects").resolve("notes.txt"), "x");
        Files.setLastModifiedTime(foreign, FileTime.from(Instant.now().minus(Duration.ofDays(30))));

        assertThat(cleanup.runOnce().orphansDeleted()).isEqualTo(2);

        assertThat(object(noRow)).doesNotExist();
        assertThat(object(deleted.key())).doesNotExist();
        assertThat(object(fresh)).exists();
        assertThat(foreign).exists();
    }

    @Test
    void old_staged_files_are_removed_and_one_being_written_is_kept() throws Exception {
        Path tmp = dir.resolve("tmp");
        Path dead = Files.writeString(tmp.resolve(UUID.randomUUID().toString()), "x");
        Files.setLastModifiedTime(dead, FileTime.from(Instant.now().minus(DAY.plusHours(1))));
        Path writing = Files.writeString(tmp.resolve(UUID.randomUUID().toString()), "x");

        assertThat(cleanup.runOnce().stagedDeleted()).isEqualTo(1);

        assertThat(dead).doesNotExist();
        assertThat(writing).exists();
    }

    @Test
    void a_run_stopped_between_its_commit_and_the_removal_is_finished_by_the_next_run() throws Exception {
        Upload old = upload("UPLOADING", DAY.plusHours(1));
        bytes(old.key(), DAY.plusHours(2));
        // What step 1 leaves when the worker dies after its commit: the row DELETED, the bytes still there.
        tx.executeWithoutResult(status ->
                assertThat(uploads.markUnusedDeleted(Instant.now().minus(DAY), Instant.now().minus(Duration.ofDays(7)),
                        1000)).contains(old.key()));
        assertThat(object(old.key())).exists();

        cleanup.runOnce();

        assertThat(state(old)).isEqualTo("DELETED");
        assertThat(object(old.key())).doesNotExist();
    }

    @Test
    void an_upload_another_transaction_holds_is_skipped_without_waiting_and_deleted_next_time()
            throws Exception {
        Upload held = upload("UPLOADING", DAY.plusHours(1));
        bytes(held.key(), DAY.plusHours(2));
        try (Connection other = DriverManager.getConnection(
                PostgresTestSupport.jdbcUrl(), "tutorbridge_app", PostgresTestSupport.APP_PASSWORD)) {
            other.setAutoCommit(false);
            // What a PUT or a complete holds while it decides (EvidenceUploads, upload FOR UPDATE).
            try (var hold = other.prepareStatement(
                    "select 1 from tutorbridge.tutor_evidence_uploads where id = ? for update")) {
                hold.setObject(1, held.id());
                hold.executeQuery();
            }
            // Bounded: a run that waited on the row would fail here instead of hanging.
            CompletableFuture.runAsync(cleanup::runOnce).get(10, TimeUnit.SECONDS);
            assertThat(state(held)).isEqualTo("UPLOADING");
            assertThat(object(held.key())).exists();
            other.rollback();
        }

        cleanup.runOnce();

        assertThat(state(held)).isEqualTo("DELETED");
        assertThat(object(held.key())).doesNotExist();
    }

    @Test
    void two_runs_at_once_delete_each_upload_once() throws Exception {
        java.util.List<Upload> old = new java.util.ArrayList<>();
        for (int i = 0; i < 6; i++) {
            Upload upload = upload("UPLOADING", DAY.plusHours(1));
            bytes(upload.key(), DAY.plusHours(2));
            old.add(upload);
        }
        EvidenceCleanup small = cleanup(2);

        var first = CompletableFuture.supplyAsync(small::runOnce);
        var second = CompletableFuture.supplyAsync(small::runOnce);
        CompletableFuture.allOf(first, second).get(20, TimeUnit.SECONDS);

        assertThat(old).allSatisfy(upload -> {
            assertThat(state(upload)).isEqualTo("DELETED");
            assertThat(object(upload.key())).doesNotExist();
        });
        assertThat(first.get().failures() + second.get().failures()).isZero();
    }

    @Test
    void more_due_uploads_than_a_batch_are_all_deleted_in_one_run() throws Exception {
        java.util.List<Upload> old = new java.util.ArrayList<>();
        for (int i = 0; i < 5; i++) {
            old.add(upload("UPLOADING", DAY.plusHours(1)));
        }

        cleanup(2).runOnce();

        assertThat(old).allSatisfy(upload -> assertThat(state(upload)).isEqualTo("DELETED"));
    }

    @Test
    void keep_times_must_be_positive() {
        for (Duration keep : new Duration[] {Duration.ZERO, Duration.ofHours(-1)}) {
            assertThatThrownBy(() -> new EvidenceCleanup(uploads, store, tx, Clock.systemUTC(), 100, 20, keep,
                    Duration.ofDays(7), Duration.ofHours(24))).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void only_the_worker_role_schedules_the_job() {
        // Tests run as the API role (tutorbridge.worker.enabled is off), as an API instance does.
        assertThat(context.getBeansOfType(EvidenceCleanup.Runner.class)).isEmpty();
        assertThat(context.getBeansOfType(EvidenceCleanup.class)).hasSize(1);
    }
}
