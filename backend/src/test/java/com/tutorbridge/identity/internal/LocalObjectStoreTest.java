package com.tutorbridge.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The local ObjectStore on its own: limits, staging cleanup, and keys that could leave the directory. */
class LocalObjectStoreTest {

    @TempDir
    Path dir;

    private LocalObjectStore store() {
        return new LocalObjectStore(new EvidenceStoreProperties(dir.toString()));
    }

    private long staged() throws Exception {
        try (var files = Files.list(dir.resolve("tmp"))) {
            return files.count();
        }
    }

    @Test
    void the_app_refuses_to_start_without_a_directory() {
        for (String missing : new String[] {null, "", "  "}) {
            assertThatThrownBy(() -> new EvidenceStoreProperties(missing))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("TB_EVIDENCE_DIR");
        }
    }

    @Test
    void reading_stops_one_byte_past_the_limit_and_closing_removes_the_staged_bytes() throws Exception {
        LocalObjectStore store = store();
        var body = new ByteArrayInputStream(new byte[1000]);
        try (ObjectStore.Staged staged = store.stage(body, 10)) {
            assertThat(staged.size()).isEqualTo(11);
            assertThat(staged()).isEqualTo(1);
        }
        assertThat(body.available()).isEqualTo(989);
        assertThat(staged()).isZero();
    }

    @Test
    void commit_moves_the_bytes_and_replaces_what_was_there() throws Exception {
        LocalObjectStore store = store();
        String key = UUID.randomUUID().toString();
        for (byte value : new byte[] {1, 2}) {
            try (ObjectStore.Staged staged = store.stage(new ByteArrayInputStream(new byte[] {value}), 1)) {
                staged.commit(key);
            }
        }
        assertThat(Files.readAllBytes(dir.resolve("objects").resolve(key))).containsExactly(2);
        assertThat(staged()).isZero();
    }

    @Test
    void a_key_this_service_did_not_mint_is_refused() throws Exception {
        LocalObjectStore store = store();
        for (String key : new String[] {"../escape", "..\\escape", "/abs", "a", UUID.randomUUID() + "/x",
            UUID.randomUUID().toString().toUpperCase()}) {
            try (ObjectStore.Staged staged = store.stage(new ByteArrayInputStream(new byte[] {1}), 1)) {
                assertThatThrownBy(() -> staged.commit(key)).isInstanceOf(IllegalArgumentException.class);
            }
        }
        try (var files = Files.list(dir.resolve("objects"))) {
            assertThat(files).isEmpty();
        }
        assertThat(Files.exists(dir.resolve("escape"))).isFalse();
    }
}
