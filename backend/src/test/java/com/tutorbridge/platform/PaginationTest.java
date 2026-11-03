package com.tutorbridge.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tutorbridge.IntegrationTest;
import com.tutorbridge.platform.api.ApiException;
import com.tutorbridge.platform.api.Cursor;
import com.tutorbridge.platform.api.Page;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PaginationTest extends IntegrationTest {

    private record Row(UUID id, Instant createdAt) {}

    private Page<Row> fetch(String marker, Integer requestedLimit, String cursor) {
        int limit = Page.limit(requestedLimit);
        Cursor position = Cursor.decode(cursor);
        String after = position == null ? "" : "and (created_at, id) < (?, ?)";
        var query = jdbc.sql("""
                select id, created_at from audit_events
                where event_type = ? %s
                order by created_at desc, id desc
                limit ?
                """.formatted(after)).param(marker);
        if (position != null) {
            query = query.param(java.sql.Timestamp.from(position.at())).param(position.id());
        }
        List<Row> rows = query.param(limit + 1).query(Row.class).list();
        return Page.of(rows, limit, row -> new Cursor(row.createdAt(), row.id()));
    }

    @Test
    void INV_81_walks_every_row_once_even_when_timestamps_tie() {
        String marker = "page-" + UUID.randomUUID();
        Instant tied = Instant.parse("2026-11-03T01:02:03.123456Z");
        for (int i = 0; i < 25; i++) {
            Instant at = i % 2 == 0 ? tied : tied.plusSeconds(i);
            jdbc.sql("""
                    insert into audit_events(id, event_type, object_type, object_id, outcome, trace_id, created_at)
                    values (?, ?, 'TEST', ?, 'OK', 't', ?)
                    """).params(UUID.randomUUID(), marker, UUID.randomUUID(), java.sql.Timestamp.from(at)).update();
        }

        List<UUID> seen = new ArrayList<>();
        String cursor = null;
        int pages = 0;
        do {
            Page<Row> page = fetch(marker, 10, cursor);
            page.items().forEach(row -> seen.add(row.id()));
            cursor = page.nextCursor();
            pages++;
        } while (cursor != null);

        assertThat(seen).hasSize(25).doesNotHaveDuplicates();
        assertThat(pages).isEqualTo(3);
    }

    @Test
    void exact_last_page_has_no_next_cursor() {
        Page<String> page = Page.of(List.of("a", "b"), 2, row -> new Cursor(Instant.EPOCH, UUID.randomUUID()));

        assertThat(page.items()).containsExactly("a", "b");
        assertThat(page.nextCursor()).isNull();
    }

    @Test
    void cursor_keeps_microseconds_and_fits_the_contract() {
        Cursor cursor = new Cursor(Instant.parse("2026-11-03T01:02:03.123456Z"), UUID.randomUUID());

        assertThat(Cursor.decode(cursor.encode())).isEqualTo(cursor);
        assertThat(cursor.encode()).matches("[A-Za-z0-9_-]+").hasSizeLessThanOrEqualTo(500);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "!!!", "djF8MXw", "djJ8MXwwMDAwMDAwMC0wMDAwLTAwMDAtMDAwMC0wMDAwMDAwMDAwMDA"})
    void malformed_cursor_is_a_422(String cursor) {
        assertInvalid(() -> Cursor.decode(cursor), "CURSOR_INVALID");
    }

    @Test
    void cursor_with_a_non_numeric_time_is_a_422() {
        String forged = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(("v1|soon|" + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8));

        assertInvalid(() -> Cursor.decode(forged), "CURSOR_INVALID");
    }

    @Test
    void limit_defaults_to_20_and_stays_within_1_to_50() {
        assertThat(Page.limit(null)).isEqualTo(20);
        assertThat(Page.limit(1)).isEqualTo(1);
        assertThat(Page.limit(50)).isEqualTo(50);
        assertInvalid(() -> Page.limit(0), "LIMIT_INVALID");
        assertInvalid(() -> Page.limit(51), "LIMIT_INVALID");
    }

    private static void assertInvalid(Runnable call, String code) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.status().value()).isEqualTo(422);
            assertThat(e.code()).isEqualTo(code);
        });
    }
}
