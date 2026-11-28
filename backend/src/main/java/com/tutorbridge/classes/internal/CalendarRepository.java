package com.tutorbridge.classes.internal;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code calendars} and {@code availability_windows} (module classes). A calendar version is never
 * changed after it is written: a replacement adds the next version and moves {@code effective} to it,
 * so need versions, candidates and offers that point at an older version keep what they saw.
 */
@Repository
class CalendarRepository {

    record Version(UUID id, long version) {}

    record Window(Instant start, Instant end, String kind) {}

    private final JdbcClient jdbc;
    private final JdbcTemplate batch;

    CalendarRepository(JdbcClient jdbc, JdbcTemplate batch) {
        this.jdbc = jdbc;
        this.batch = batch;
    }

    Optional<Version> findEffective(String ownerKind, UUID ownerId) {
        return jdbc.sql("select id, version from calendars where owner_kind = ? and owner_id = ? and effective")
                .params(ownerKind, ownerId).query(Version.class).optional();
    }

    record Owner(String kind, UUID id) {}

    /** Whose calendar a version is, for the command status lookup (TB-22). */
    Optional<Owner> findOwner(UUID calendarId) {
        return jdbc.sql("select owner_kind, owner_id from calendars where id = ?").param(calendarId)
                .query((rs, n) -> new Owner(rs.getString(1), rs.getObject(2, UUID.class))).optional();
    }

    Optional<Version> findVersion(UUID calendarId, String ownerKind, UUID ownerId) {
        return jdbc.sql("select id, version from calendars where id = ? and owner_kind = ? and owner_id = ?")
                .params(calendarId, ownerKind, ownerId).query(Version.class).optional();
    }

    /** The newest version, effective or not, under a row lock; the resource lock is already held. */
    @Transactional(propagation = Propagation.MANDATORY)
    Optional<Version> lockLatest(String ownerKind, UUID ownerId) {
        return jdbc.sql("""
                select id, version from calendars where owner_kind = ? and owner_id = ?
                order by version desc limit 1 for update
                """).params(ownerKind, ownerId).query(Version.class).optional();
    }

    /** At most {@code CalendarService.MAX_WINDOWS}, as written; ordered for a stable answer. */
    List<Window> windows(UUID calendarId) {
        return jdbc.sql("""
                select lower(during), upper(during), kind from availability_windows
                where calendar_id = ? order by lower(during), upper(during), kind, id
                """).param(calendarId)
                .query((rs, n) -> new Window(rs.getObject(1, OffsetDateTime.class).toInstant(),
                        rs.getObject(2, OffsetDateTime.class).toInstant(), rs.getString(3)))
                .list();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    void replace(Optional<Version> previous, UUID id, String ownerKind, UUID ownerId, long version,
            List<Window> windows, List<UUID> windowIds) {
        previous.ifPresent(old -> jdbc.sql("update calendars set effective = false where id = ? and effective")
                .param(old.id()).update());
        jdbc.sql("""
                insert into calendars(id, owner_kind, owner_id, version, effective, timezone)
                values (?, ?, ?, ?, true, 'Asia/Ho_Chi_Minh')
                """).params(id, ownerKind, ownerId, version).update();
        if (windows.isEmpty()) {
            return;
        }
        // By position, not by value: two identical windows are allowed and need their own ids. One batch for
        // every window, as Spring 7.1 asks in place of the chunked batchUpdate (at most 200 windows, OP-26).
        batch.batchUpdate("""
                insert into availability_windows(id, calendar_id, during, kind)
                values (?, ?, tstzrange(?, ?, '[)'), ?)
                """, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement statement, int position) throws SQLException {
                Window window = windows.get(position);
                statement.setObject(1, windowIds.get(position));
                statement.setObject(2, id);
                statement.setObject(3, OffsetDateTime.ofInstant(window.start(), ZoneOffset.UTC));
                statement.setObject(4, OffsetDateTime.ofInstant(window.end(), ZoneOffset.UTC));
                statement.setString(5, window.kind());
            }

            @Override
            public int getBatchSize() {
                return windows.size();
            }
        });
    }
}
