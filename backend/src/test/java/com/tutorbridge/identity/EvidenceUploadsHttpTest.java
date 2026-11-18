package com.tutorbridge.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tutorbridge.Concurrently;
import com.tutorbridge.Contract;
import com.tutorbridge.HttpTest;
import com.tutorbridge.PostgresTestSupport;
import com.tutorbridge.platform.api.Actor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** UC03 evidence, TB-12b1: createEvidenceUpload and uploadEvidenceContent with the local ObjectStore. */
class EvidenceUploadsHttpTest extends HttpTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String UPLOADS = "/api/v1/me/tutor-profile/evidence-uploads";
    private static final int FIVE_MIB = 5 * 1024 * 1024;
    private static final String PDF = "application/pdf";

    // ---------------------------------------------------------------- helpers

    private static byte[] bytes(int size) {
        byte[] bytes = new byte[size];
        new Random(size).nextBytes(bytes);
        return bytes;
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String input(String mediaType, long sizeBytes, String sha256) {
        return """
                {"mediaType":"%s","sizeBytes":%d,"sha256":"%s"}
                """.formatted(mediaType, sizeBytes, sha256);
    }

    private static String inputFor(byte[] content) {
        return input(PDF, content.length, sha256(content));
    }

    private static String key() {
        return UUID.randomUUID().toString();
    }

    private ResultActions create(Actor actor, String key, String json) throws Exception {
        MockHttpServletRequestBuilder request = post(UPLOADS).contentType(MediaType.APPLICATION_JSON).content(json);
        if (actor != null) {
            request.with(sessions().as(actor));
        }
        if (key != null) {
            request.header("Idempotency-Key", key);
        }
        return mvc.perform(request).andExpect(Contract.matches());
    }

    private UUID createdFor(Actor tutor, byte[] content) throws Exception {
        return id(create(tutor, key(), inputFor(content)).andExpect(status().isCreated()));
    }

    private ResultActions upload(Actor actor, UUID uploadId, String key, String contentType, byte[] body)
            throws Exception {
        MockHttpServletRequestBuilder request = put(UPLOADS + "/" + uploadId + "/content").content(body);
        if (contentType != null) {
            request.contentType(contentType);
        }
        if (actor != null) {
            request.with(sessions().as(actor));
        }
        if (key != null) {
            request.header("Idempotency-Key", key);
        }
        return mvc.perform(request).andExpect(Contract.matches());
    }

    private static JsonNode json(ResultActions result) throws Exception {
        return JSON.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private static UUID id(ResultActions result) throws Exception {
        JsonNode body = json(result);
        return UUID.fromString((body.has("uploadId") ? body.get("uploadId") : body.get("id")).asString());
    }

    private int uploadsOf(Actor actor) {
        return jdbc.sql("select count(*) from tutor_evidence_uploads where owner_id = ?").param(actor.accountId())
                .query(Integer.class).single();
    }

    private String objectKey(UUID uploadId) {
        return jdbc.sql("select object_key from tutor_evidence_uploads where id = ?").param(uploadId)
                .query(String.class).single();
    }

    private String state(UUID uploadId) {
        return jdbc.sql("select state from tutor_evidence_uploads where id = ?").param(uploadId)
                .query(String.class).single();
    }

    private Path stored(UUID uploadId) {
        return evidenceDir().resolve("objects").resolve(objectKey(uploadId));
    }

    private int receiptsOf(Actor actor) {
        return jdbc.sql("select count(*) from command_receipts where actor_id = ?").param(actor.accountId())
                .query(Integer.class).single();
    }

    /** An upload whose ticket ran out, as one created 20 minutes ago would be. */
    private UUID expiredUpload(Actor tutor, byte[] content) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                insert into tutor_evidence_uploads(id, owner_id, object_key, media_type, size_bytes, sha256, state,
                                                   expires_at, created_at)
                values (?, ?, ?, 'application/pdf', ?, ?, 'UPLOADING', now() - interval '5 minutes',
                        now() - interval '20 minutes')
                """).params(id, tutor.accountId(), UUID.randomUUID().toString(), content.length, sha256(content))
                .update();
        return id;
    }

    private void moveTo(UUID uploadId, String state) {
        jdbc.sql("update tutor_evidence_uploads set state = ? where id = ?").params(state, uploadId).update();
    }

    // ---------------------------------------------------------------- createEvidenceUpload

    @Test
    void UC03_a_tutor_gets_a_ticket_owned_by_the_session_that_expires_in_15_minutes() throws Exception {
        Actor tutor = newActor(Actor.Role.TUTOR);
        byte[] content = bytes(1000);
        Instant before = Instant.now();

        JsonNode ticket = json(create(tutor, key(), inputFor(content))
                .andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", "private, no-store")));

        UUID id = UUID.fromString(ticket.get("uploadId").asString());
        assertThat(ticket.get("uploadUrl").asString())
                .isEqualTo("http://localhost" + UPLOADS + "/" + id + "/content");
        Instant expiresAt = Instant.parse(ticket.get("expiresAt").asString());
        assertThat(expiresAt).isBetween(before.plus(Duration.ofMinutes(15)).minusSeconds(5),
                Instant.now().plus(Duration.ofMinutes(15)).plusSeconds(5));
        assertThat(jdbc.sql("""
                select concat_ws('|', owner_id, media_type, size_bytes, sha256, state, profile_id is null)
                from tutor_evidence_uploads where id = ?
                """).param(id).query(String.class).single())
                .isEqualTo(String.join("|", tutor.accountId().toString(), PDF, "1000", sha256(content),
                        "UPLOADING", "t"));
        // AC-UC03-03: the storage location never leaves the server.
        assertThat(ticket.toString()).doesNotContain(objectKey(id));
    }

    @Test
    void G3_D07_04_pdf_jpeg_and_png_up_to_5_mib_are_accepted() throws Exception {
        Actor tutor = newActor(Actor.Role.TUTOR);
        String sha = sha256(bytes(1));
        for (String json : new String[] {
            input("application/pdf", 1, sha), input("image/jpeg", 1, sha), input("image/png", FIVE_MIB, sha),
        }) {
            create(tutor, key(), json).andExpect(status().isCreated());
        }
        assertThat(uploadsOf(tutor)).isEqualTo(3);
    }

    @Test
    void G3_D07_04_another_type_an_empty_or_too_large_file_or_a_bad_sha256_is_refused() throws Exception {
        Actor tutor = newActor(Actor.Role.TUTOR);
        String sha = sha256(bytes(1));
        for (String json : new String[] {
            input("image/gif", 1, sha),
            input("application/octet-stream", 1, sha),
            input(PDF, 0, sha),
            input(PDF, FIVE_MIB + 1, sha),
            input(PDF, 1, sha.toUpperCase()),
            input(PDF, 1, sha.substring(1)),
            input(PDF, 1, sha + "0"),
            "{\"mediaType\":\"application/pdf\",\"sizeBytes\":1}",
            // A fraction is refused, not cut to 1 (#169, Đính's review of #181).
            "{\"mediaType\":\"application/pdf\",\"sizeBytes\":1.5,\"sha256\":\"" + sha + "\"}",
        }) {
            create(tutor, key(), json)
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }
        assertThat(uploadsOf(tutor)).isZero();
    }

    @Test
    void INV_03_an_owner_or_a_profile_sent_by_the_app_is_refused_not_used() throws Exception {
        Actor tutor = newActor(Actor.Role.TUTOR);
        Actor other = newActor(Actor.Role.TUTOR);
        String sha = sha256(bytes(1));
        for (String json : new String[] {
            "{\"mediaType\":\"application/pdf\",\"sizeBytes\":1,\"sha256\":\"" + sha + "\",\"ownerId\":\""
                    + other.accountId() + "\"}",
            "{\"mediaType\":\"application/pdf\",\"sizeBytes\":1,\"sha256\":\"" + sha + "\",\"profileId\":\""
                    + UUID.randomUUID() + "\"}",
        }) {
            create(tutor, key(), json).andExpect(status().isUnprocessableContent());
        }
        assertThat(uploadsOf(tutor) + uploadsOf(other)).isZero();
    }

    @Test
    void UC03_only_an_active_tutor_creates_an_upload() throws Exception {
        String json = inputFor(bytes(10));
        Actor parent = newActor(Actor.Role.PARENT);
        create(parent, key(), json).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_UNAVAILABLE"));
        create(null, key(), json).andExpect(status().isUnauthorized());

        Actor locked = newActor(Actor.Role.TUTOR);
        jdbc.sql("update accounts set status = 'LOCKED' where id = ?").param(locked.accountId()).update();
        create(locked, key(), json).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_UNAVAILABLE"));

        assertThat(uploadsOf(parent) + uploadsOf(locked)).isZero();
    }

    @Test
    void INV_20_create_without_a_key_is_400_and_the_same_key_replays_one_ticket() throws Exception {
        Actor tutor = newActor(Actor.Role.TUTOR);
        byte[] content = bytes(10);
        create(tutor, null, inputFor(content)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));
        assertThat(uploadsOf(tutor)).isZero();

        String key = key();
        String first = create(tutor, key, inputFor(content)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String again = create(tutor, key, inputFor(content)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        create(tutor, key, inputFor(bytes(11))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));

        assertThat(again).isEqualTo(first);
        assertThat(uploadsOf(tutor)).isEqualTo(1);
    }

    // ---------------------------------------------------------------- uploadEvidenceContent

    @Test
    void UC03_the_declared_bytes_are_stored_privately_and_the_upload_stays_uploading() throws Exception {
        Actor tutor = newActor(Actor.Role.TUTOR);
        byte[] content = bytes(4096);
        UUID id = createdFor(tutor, content);

        String body = upload(tutor, id, key(), PDF, content)
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.state").value("UPLOADING"))
                .andReturn().getResponse().getContentAsString();

        assertThat(Files.readAllBytes(stored(id))).isEqualTo(content);
        assertThat(state(id)).isEqualTo("UPLOADING");
        assertThat(body).doesNotContain(objectKey(id)).doesNotContain(evidenceDir().toString());
    }

    @Test
    void UC03_content_type_compares_type_and_subtype_only() throws Exception {
        Actor tutor = newActor(Actor.Role.TUTOR);
        byte[] content = bytes(100);
        UUID id = createdFor(tutor, content);

        upload(tutor, id, key(), "Application/PDF; name=minh-chung.pdf", content).andExpect(status().isOk());
        assertThat(Files.readAllBytes(stored(id))).isEqualTo(content);
    }

    @Test
    void UC03_a_full_5_mib_file_is_stored() throws Exception {
        Actor tutor = newActor(Actor.Role.TUTOR);
        byte[] content = bytes(FIVE_MIB);
        UUID id = createdFor(tutor, content);

        upload(tutor, id, key(), PDF, content).andExpect(status().isOk());
        assertThat(Files.size(stored(id))).isEqualTo(FIVE_MIB);
    }

    @Test
    void UC03_bytes_other_than_declared_are_422_and_nothing_is_stored() throws Exception {
        Actor tutor = newActor(Actor.Role.TUTOR);
        byte[] content = bytes(2048);
        UUID id = createdFor(tutor, content);
        byte[] flipped = content.clone();
        flipped[100] ^= 1;

        for (ResultActions result : List.of(
                upload(tutor, id, key(), "image/png", content),
                upload(tutor, id, key(), null, content),
                upload(tutor, id, key(), "not a media type", content),
                upload(tutor, id, key(), PDF, java.util.Arrays.copyOf(content, 2047)),
                upload(tutor, id, key(), PDF, java.util.Arrays.copyOf(content, 2049)),
                upload(tutor, id, key(), PDF, flipped))) {
            result.andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }

        assertThat(stored(id)).doesNotExist();
        assertThat(receiptsOf(tutor)).isEqualTo(1);
        // Huy's note on #163: the SHA-256 check comes before the receipt, so a reused key with other bytes is 422.
        String key = key();
        upload(tutor, id, key, PDF, content).andExpect(status().isOk());
        upload(tutor, id, key, PDF, flipped).andExpect(status().isUnprocessableContent());
    }

    @Test
    void UC03_a_body_past_5_mib_is_refused_without_reading_it_all() throws Exception {
        Actor tutor = newActor(Actor.Role.TUTOR);
        byte[] content = bytes(FIVE_MIB);
        UUID id = createdFor(tutor, content);

        upload(tutor, id, key(), PDF, bytes(FIVE_MIB + 1024))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        assertThat(stored(id)).doesNotExist();
    }

    @Test
    void UC03_an_expired_upload_is_409_and_nothing_is_stored() throws Exception {
        Actor tutor = newActor(Actor.Role.TUTOR);
        byte[] content = bytes(100);
        UUID id = expiredUpload(tutor, content);

        upload(tutor, id, key(), PDF, content)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EVIDENCE_UPLOAD_EXPIRED"));
        assertThat(stored(id)).doesNotExist();
        assertThat(receiptsOf(tutor)).isZero();
    }

    @Test
    void UC03_an_upload_no_longer_uploading_is_409_closed() throws Exception {
        Actor tutor = newActor(Actor.Role.TUTOR);
        byte[] content = bytes(100);
        for (String state : new String[] {"QUARANTINED", "READY", "FAILED", "DELETED"}) {
            UUID id = createdFor(tutor, content);
            moveTo(id, state);
            upload(tutor, id, key(), PDF, content)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("EVIDENCE_UPLOAD_CLOSED"));
            assertThat(stored(id)).doesNotExist();
            assertThat(state(id)).isEqualTo(state);
        }
    }

    @Test
    void INV_06_another_tutors_upload_is_404_like_an_unknown_id_and_nothing_is_stored() throws Exception {
        Actor owner = newActor(Actor.Role.TUTOR);
        Actor other = newActor(Actor.Role.TUTOR);
        byte[] content = bytes(100);
        UUID id = createdFor(owner, content);

        String foreign = upload(other, id, key(), PDF, content)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EVIDENCE_UPLOAD_NOT_FOUND"))
                .andReturn().getResponse().getContentAsString();
        String unknown = upload(other, UUID.randomUUID(), key(), PDF, content)
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();

        assertThat(JSON.readTree(foreign).get("title")).isEqualTo(JSON.readTree(unknown).get("title"));
        assertThat(stored(id)).doesNotExist();
        assertThat(receiptsOf(other)).isZero();
    }

    @Test
    void UC03_only_an_active_tutor_puts_content() throws Exception {
        Actor tutor = newActor(Actor.Role.TUTOR);
        byte[] content = bytes(100);
        UUID id = createdFor(tutor, content);

        upload(newActor(Actor.Role.PARENT), id, key(), PDF, content).andExpect(status().isForbidden());
        upload(null, id, key(), PDF, content).andExpect(status().isUnauthorized());
        jdbc.sql("update accounts set status = 'LOCKED' where id = ?").param(tutor.accountId()).update();
        upload(tutor, id, key(), PDF, content).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_UNAVAILABLE"));

        assertThat(stored(id)).doesNotExist();
    }

    @Test
    void INV_20_put_without_a_key_is_400_the_same_key_replays_and_another_upload_with_it_is_409() throws Exception {
        Actor tutor = newActor(Actor.Role.TUTOR);
        byte[] content = bytes(100);
        UUID id = createdFor(tutor, content);
        UUID second = createdFor(tutor, content);

        upload(tutor, id, null, PDF, content).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));
        assertThat(stored(id)).doesNotExist();

        String key = key();
        upload(tutor, id, key, PDF, content).andExpect(status().isOk());
        // The same request after the upload moved on replays the record as it is now (INV-22), not 409 closed.
        moveTo(id, "READY");
        upload(tutor, id, key, PDF, content).andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("READY"));
        upload(tutor, second, key, PDF, content).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
        // The bytes move into place before the command is recorded (INV-18), so the declared bytes of the
        // second upload may be there; nothing records them and the upload is unchanged.
        assertThat(state(second)).isEqualTo("UPLOADING");
        assertThat(receiptsOf(tutor)).isEqualTo(3);
    }

    @Test
    void INV_22_a_replay_rechecks_that_the_caller_still_may_write() throws Exception {
        Actor tutor = newActor(Actor.Role.TUTOR);
        byte[] content = bytes(100);
        UUID id = createdFor(tutor, content);
        String key = key();
        upload(tutor, id, key, PDF, content).andExpect(status().isOk());

        jdbc.sql("update accounts set status = 'LOCKED' where id = ?").param(tutor.accountId()).update();
        upload(tutor, id, key, PDF, content).andExpect(status().isForbidden());
    }

    @Test
    void UC03_a_new_key_while_uploading_stores_the_same_bytes_again() throws Exception {
        Actor tutor = newActor(Actor.Role.TUTOR);
        byte[] content = bytes(100);
        UUID id = createdFor(tutor, content);

        upload(tutor, id, key(), PDF, content).andExpect(status().isOk());
        upload(tutor, id, key(), PDF, content).andExpect(status().isOk());
        assertThat(Files.readAllBytes(stored(id))).isEqualTo(content);
        assertThat(state(id)).isEqualTo("UPLOADING");
    }

    // ---------------------------------------------------------------- concurrency

    /** Waits until {@code count} commands are blocked on the row lock of the upload. */
    private void awaitWaitingOnTheUpload(int count) {
        await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(20)).until(() -> jdbc.sql("""
                select count(*) from pg_stat_activity
                where wait_event_type = 'Lock' and query like '%from tutor_evidence_uploads where id = $1 for update'
                """).query(Integer.class).single() == count);
    }

    private Connection gate() throws Exception {
        Connection gate = DriverManager.getConnection(
                PostgresTestSupport.jdbcUrl(), "tutorbridge_app", PostgresTestSupport.APP_PASSWORD);
        gate.setAutoCommit(false);
        return gate;
    }

    private static void lockRow(Connection gate, UUID uploadId) throws Exception {
        try (var hold = gate.prepareStatement("select id from tutor_evidence_uploads where id = ? for update")) {
            hold.setObject(1, uploadId);
            hold.execute();
        }
    }

    private interface Call {
        ResultActions run() throws Exception;
    }

    private static int statusOf(Call call) {
        try {
            return call.run().andReturn().getResponse().getStatus();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void two_puts_at_once_both_store_the_one_declared_file() throws Exception {
        Actor tutor = newActor(Actor.Role.TUTOR);
        byte[] content = bytes(64 * 1024);
        UUID id = createdFor(tutor, content);
        CompletableFuture<Integer> first;
        CompletableFuture<Integer> second;
        try (Connection gate = gate()) {
            lockRow(gate, id);
            first = Concurrently.supply(() -> statusOf(() -> upload(tutor, id, key(), PDF, content)));
            second = Concurrently.supply(() -> statusOf(() -> upload(tutor, id, key(), PDF, content)));
            awaitWaitingOnTheUpload(2);
            gate.commit();
        }

        assertThat(List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS)))
                .containsExactly(200, 200);
        assertThat(Files.readAllBytes(stored(id))).isEqualTo(content);
        assertThat(receiptsOf(tutor)).isEqualTo(3);
    }

    @Test
    void a_put_waiting_while_the_upload_closes_is_409_after_the_recheck_and_leaves_no_receipt() throws Exception {
        Actor tutor = newActor(Actor.Role.TUTOR);
        byte[] content = bytes(100);
        UUID id = createdFor(tutor, content);
        CompletableFuture<Integer> waiting;
        try (Connection gate = gate()) {
            lockRow(gate, id);
            waiting = Concurrently.supply(() -> statusOf(() -> upload(tutor, id, key(), PDF, content)));
            awaitWaitingOnTheUpload(1);
            try (var close = gate.prepareStatement(
                    "update tutor_evidence_uploads set state = 'QUARANTINED' where id = ?")) {
                close.setObject(1, id);
                close.executeUpdate();
            }
            gate.commit();
        }

        assertThat(waiting.get(30, TimeUnit.SECONDS)).isEqualTo(409);
        assertThat(state(id)).isEqualTo("QUARANTINED");
        assertThat(receiptsOf(tutor)).isEqualTo(1);
    }

    @Test
    void a_tutor_locked_while_the_put_waits_is_refused_at_the_recheck_and_leaves_no_receipt() throws Exception {
        Actor tutor = newActor(Actor.Role.TUTOR);
        byte[] content = bytes(100);
        UUID id = createdFor(tutor, content);
        CompletableFuture<Integer> waiting;
        try (Connection gate = gate()) {
            // The PUT reads the account as ACTIVE before any lock, then waits here for FOR SHARE.
            try (var hold = gate.prepareStatement("select id from accounts where id = ? for update")) {
                hold.setObject(1, tutor.accountId());
                hold.execute();
            }
            waiting = Concurrently.supply(() -> statusOf(() -> upload(tutor, id, key(), PDF, content)));
            await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(20)).until(() -> jdbc.sql("""
                    select count(*) from pg_stat_activity
                    where wait_event_type = 'Lock' and query like '%from accounts where id = $1%for share'
                    """).query(Integer.class).single() == 1);
            try (var lock = gate.prepareStatement("update accounts set status = 'LOCKED' where id = ?")) {
                lock.setObject(1, tutor.accountId());
                lock.executeUpdate();
            }
            gate.commit();
        }

        assertThat(waiting.get(30, TimeUnit.SECONDS)).isEqualTo(403);
        assertThat(state(id)).isEqualTo("UPLOADING");
        assertThat(receiptsOf(tutor)).isEqualTo(1);
    }

    // ---------------------------------------------------------------- completeEvidenceUpload (TB-12b2)

    /** Real leading bytes of each type, then filler: the structural check reads only what R0 needs. */
    private static byte[] file(String kind, int size) {
        byte[] head = switch (kind) {
            case "pdf" -> "%PDF-1.7\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
            case "jpeg" -> new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
            case "png" -> new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
            case "exe" -> new byte[] {'M', 'Z', (byte) 0x90, 0x00};
            default -> throw new IllegalArgumentException(kind);
        };
        byte[] bytes = bytes(size);
        System.arraycopy(head, 0, bytes, 0, head.length);
        return bytes;
    }

    /** An upload whose declared bytes were PUT, ready to complete. */
    private UUID uploaded(Actor tutor, String mediaType, byte[] content) throws Exception {
        UUID id = id(create(tutor, key(), input(mediaType, content.length, sha256(content)))
                .andExpect(status().isCreated()));
        upload(tutor, id, key(), mediaType, content).andExpect(status().isOk());
        return id;
    }

    private static String completion(long sizeBytes, String sha256) {
        return "{\"sizeBytes\":%d,\"sha256\":\"%s\"}".formatted(sizeBytes, sha256);
    }

    private static String completionFor(byte[] content) {
        return completion(content.length, sha256(content));
    }

    private ResultActions complete(Actor actor, UUID uploadId, String key, String json) throws Exception {
        MockHttpServletRequestBuilder request = post(UPLOADS + "/" + uploadId + "/complete")
                .contentType(MediaType.APPLICATION_JSON).content(json);
        if (actor != null) {
            request.with(sessions().as(actor));
        }
        if (key != null) {
            request.header("Idempotency-Key", key);
        }
        return mvc.perform(request).andExpect(Contract.matches());
    }

    @Test
    void UC03_real_pdf_jpeg_and_png_bytes_become_ready() throws Exception {
        Actor tutor = newActor(Actor.Role.TUTOR);
        for (String[] kind : new String[][] {{"pdf", PDF}, {"jpeg", "image/jpeg"}, {"png", "image/png"}}) {
            byte[] content = file(kind[0], 3000);
            UUID id = uploaded(tutor, kind[1], content);

            String body = complete(tutor, id, key(), completionFor(content))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control", "private, no-store"))
                    .andExpect(jsonPath("$.id").value(id.toString()))
                    .andExpect(jsonPath("$.state").value("READY"))
                    .andReturn().getResponse().getContentAsString();

            assertThat(state(id)).isEqualTo("READY");
            assertThat(body).doesNotContain(objectKey(id)).doesNotContain(evidenceDir().toString());
        }
    }

    @Test
    void UC03_bytes_of_another_type_than_declared_are_failed_and_the_upload_is_closed() throws Exception {
        Actor tutor = newActor(Actor.Role.TUTOR);
        for (String[] fake : new String[][] {{"png", PDF}, {"exe", PDF}, {"jpeg", "image/png"}, {"pdf", "image/jpeg"}}) {
            byte[] content = file(fake[0], 500);
            UUID id = uploaded(tutor, fake[1], content);

            complete(tutor, id, key(), completionFor(content))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.state").value("FAILED"));
            assertThat(state(id)).isEqualTo("FAILED");

            // FAILED is final (Huy, TB-12b plan): another key cannot retry it, nor can a PUT reopen it.
            complete(tutor, id, key(), completionFor(content)).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("EVIDENCE_UPLOAD_CLOSED"));
            upload(tutor, id, key(), fake[1], content).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("EVIDENCE_UPLOAD_CLOSED"));
        }
    }

    @Test
    void UC03_input_other_than_declared_is_422_and_changes_nothing() throws Exception {
        Actor tutor = newActor(Actor.Role.TUTOR);
        byte[] content = file("pdf", 500);
        UUID id = uploaded(tutor, PDF, content);
        int receipts = receiptsOf(tutor);
        String sha = sha256(content);

        for (String json : new String[] {
            completion(499, sha),
            completion(500, sha256(bytes(500))),
            completion(500, sha.toUpperCase()),
            completion(500, sha.substring(1)),
            "{\"sizeBytes\":500}",
            "{\"sizeBytes\":500.5,\"sha256\":\"" + sha + "\"}",
            "{\"sizeBytes\":500,\"sha256\":\"" + sha + "\",\"mediaType\":\"application/pdf\"}",
        }) {
            complete(tutor, id, key(), json)
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }
        assertThat(state(id)).isEqualTo("UPLOADING");
        assertThat(receiptsOf(tutor)).isEqualTo(receipts);
    }

    @Test
    void UC03_complete_before_any_bytes_is_409_and_the_upload_stays_uploading() throws Exception {
        Actor tutor = newActor(Actor.Role.TUTOR);
        byte[] content = file("pdf", 500);
        UUID id = id(create(tutor, key(), inputFor(content)).andExpect(status().isCreated()));

        complete(tutor, id, key(), completionFor(content))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EVIDENCE_CONTENT_MISSING"));
        assertThat(state(id)).isEqualTo("UPLOADING");

        // The bytes can still come, and then complete works.
        upload(tutor, id, key(), PDF, content).andExpect(status().isOk());
        complete(tutor, id, key(), completionFor(content)).andExpect(jsonPath("$.state").value("READY"));
    }

    @Test
    void UC03_an_expired_upload_cannot_complete() throws Exception {
        Actor tutor = newActor(Actor.Role.TUTOR);
        byte[] content = file("pdf", 100);
        UUID id = expiredUpload(tutor, content);

        complete(tutor, id, key(), completionFor(content))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EVIDENCE_UPLOAD_EXPIRED"));
        assertThat(state(id)).isEqualTo("UPLOADING");
        assertThat(receiptsOf(tutor)).isZero();
    }

    @Test
    void INV_06_another_tutors_upload_is_404_like_an_unknown_id_for_complete() throws Exception {
        Actor owner = newActor(Actor.Role.TUTOR);
        Actor other = newActor(Actor.Role.TUTOR);
        byte[] content = file("pdf", 100);
        UUID id = uploaded(owner, PDF, content);

        String foreign = complete(other, id, key(), completionFor(content))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EVIDENCE_UPLOAD_NOT_FOUND"))
                .andReturn().getResponse().getContentAsString();
        String unknown = complete(other, UUID.randomUUID(), key(), completionFor(content))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();

        assertThat(JSON.readTree(foreign).get("title")).isEqualTo(JSON.readTree(unknown).get("title"));
        assertThat(state(id)).isEqualTo("UPLOADING");
        assertThat(receiptsOf(other)).isZero();
    }

    @Test
    void UC03_only_an_active_tutor_completes() throws Exception {
        Actor tutor = newActor(Actor.Role.TUTOR);
        byte[] content = file("pdf", 100);
        UUID id = uploaded(tutor, PDF, content);

        complete(newActor(Actor.Role.PARENT), id, key(), completionFor(content)).andExpect(status().isForbidden());
        complete(null, id, key(), completionFor(content)).andExpect(status().isUnauthorized());
        jdbc.sql("update accounts set status = 'LOCKED' where id = ?").param(tutor.accountId()).update();
        complete(tutor, id, key(), completionFor(content)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_UNAVAILABLE"));

        assertThat(state(id)).isEqualTo("UPLOADING");
    }

    @Test
    void INV_20_complete_without_a_key_is_400_the_same_key_replays_and_another_upload_with_it_is_409()
            throws Exception {
        Actor tutor = newActor(Actor.Role.TUTOR);
        byte[] content = file("png", 300);
        UUID id = uploaded(tutor, "image/png", content);
        UUID second = uploaded(tutor, "image/png", content);

        complete(tutor, id, null, completionFor(content)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));
        assertThat(state(id)).isEqualTo("UPLOADING");

        String key = key();
        String first = complete(tutor, id, key, completionFor(content)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String again = complete(tutor, id, key, completionFor(content)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        complete(tutor, second, key, completionFor(content)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));

        assertThat(again).isEqualTo(first);
        assertThat(JSON.readTree(first).get("state").asString()).isEqualTo("READY");
        assertThat(state(second)).isEqualTo("QUARANTINED");
    }

    @Test
    void a_complete_that_died_after_quarantine_goes_on_when_called_again() throws Exception {
        Actor tutor = newActor(Actor.Role.TUTOR);
        byte[] content = file("jpeg", 300);
        UUID id = uploaded(tutor, "image/jpeg", content);
        // As left by a process that committed the first transaction and stopped before the second.
        moveTo(id, "QUARANTINED");

        complete(tutor, id, key(), completionFor(content)).andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("READY"));
        assertThat(state(id)).isEqualTo("READY");
    }

    @Test
    void quarantined_bytes_that_are_gone_are_failed() throws Exception {
        Actor tutor = newActor(Actor.Role.TUTOR);
        byte[] content = file("pdf", 300);
        UUID id = uploaded(tutor, PDF, content);
        moveTo(id, "QUARANTINED");
        Files.delete(stored(id));

        complete(tutor, id, key(), completionFor(content)).andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("FAILED"));
    }

    @Test
    void bytes_changed_in_the_store_after_the_put_are_failed() throws Exception {
        Actor tutor = newActor(Actor.Role.TUTOR);
        byte[] content = file("pdf", 300);
        UUID id = uploaded(tutor, PDF, content);
        byte[] changed = content.clone();
        changed[200] ^= 1;
        Files.write(stored(id), changed);

        complete(tutor, id, key(), completionFor(content)).andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("FAILED"));
    }

    @Test
    void two_completes_at_once_change_the_state_once_and_the_other_is_409_closed() throws Exception {
        Actor tutor = newActor(Actor.Role.TUTOR);
        byte[] content = file("pdf", 64 * 1024);
        UUID id = uploaded(tutor, PDF, content);
        int receipts = receiptsOf(tutor);
        CompletableFuture<Integer> first;
        CompletableFuture<Integer> second;
        try (Connection gate = gate()) {
            lockRow(gate, id);
            first = Concurrently.supply(() -> statusOf(() -> complete(tutor, id, key(), completionFor(content))));
            second = Concurrently.supply(() -> statusOf(() -> complete(tutor, id, key(), completionFor(content))));
            awaitWaitingOnTheUpload(2);
            gate.commit();
        }

        assertThat(List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS)))
                .containsExactlyInAnyOrder(200, 409);
        assertThat(state(id)).isEqualTo("READY");
        assertThat(receiptsOf(tutor)).isEqualTo(receipts + 1);
    }

    @Test
    void a_tutor_locked_while_complete_waits_is_refused_and_the_upload_stays_uploading() throws Exception {
        Actor tutor = newActor(Actor.Role.TUTOR);
        byte[] content = file("pdf", 100);
        UUID id = uploaded(tutor, PDF, content);
        int receipts = receiptsOf(tutor);
        CompletableFuture<Integer> waiting;
        try (Connection gate = gate()) {
            try (var hold = gate.prepareStatement("select id from accounts where id = ? for update")) {
                hold.setObject(1, tutor.accountId());
                hold.execute();
            }
            waiting = Concurrently.supply(() -> statusOf(() -> complete(tutor, id, key(), completionFor(content))));
            await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(20)).until(() -> jdbc.sql("""
                    select count(*) from pg_stat_activity
                    where wait_event_type = 'Lock' and query like '%from accounts where id = $1%for share'
                    """).query(Integer.class).single() == 1);
            try (var lock = gate.prepareStatement("update accounts set status = 'LOCKED' where id = ?")) {
                lock.setObject(1, tutor.accountId());
                lock.executeUpdate();
            }
            gate.commit();
        }

        assertThat(waiting.get(30, TimeUnit.SECONDS)).isEqualTo(403);
        assertThat(state(id)).isEqualTo("UPLOADING");
        assertThat(receiptsOf(tutor)).isEqualTo(receipts);
    }
}
