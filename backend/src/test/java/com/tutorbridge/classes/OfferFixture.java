package com.tutorbridge.classes;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

/** Synthetic parties and pending offers. Weekly sessions start 14 days after today. */
public final class OfferFixture {

    static final int RATE = 100_000;
    static final int PRICE_90_MINUTES = 150_000;
    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");

    public record Student(UUID id, UUID parentId) {}

    public record Tutor(UUID id, UUID profileId) {}

    public record Need(UUID id, UUID versionId, Student student) {}

    private final JdbcClient jdbc;
    private final TransactionTemplate tx;

    public OfferFixture(JdbcClient jdbc, TransactionTemplate tx) {
        this.jdbc = jdbc;
        this.tx = tx;
    }

    public LocalDate cycleStart() {
        return LocalDate.now(VIETNAM).plusDays(14);
    }

    public OffsetDateTime sessionStart(int hour, int minute) {
        return cycleStart().atTime(hour, minute).atZone(VIETNAM).toOffsetDateTime();
    }

    private UUID account(String role) {
        UUID id = UUID.randomUUID();
        jdbc.sql("insert into accounts(id, role, status) values (?, ?, 'ACTIVE')").params(id, role).update();
        return id;
    }

    private void calendar(String ownerKind, UUID ownerId) {
        UUID calendar = UUID.randomUUID();
        jdbc.sql("insert into calendars(id, owner_kind, owner_id, version, effective) values (?, ?, ?, 1, true)")
                .params(calendar, ownerKind, ownerId).update();
        jdbc.sql("""
                insert into availability_windows(id, calendar_id, during, kind)
                values (?, ?, tstzrange(now(), now() + interval '60 days', '[)'), 'AVAILABLE')
                """).params(UUID.randomUUID(), calendar).update();
    }

    private UUID calendarOf(String ownerKind, UUID ownerId) {
        return jdbc.sql("select id from calendars where owner_kind = ? and owner_id = ? and effective")
                .params(ownerKind, ownerId).query(UUID.class).single();
    }

    public Student student() {
        return student(account("PARENT"));
    }

    /** A child of an existing parent account, with an effective calendar free for the next 60 days. */
    public Student student(UUID parent) {
        UUID student = UUID.randomUUID();
        jdbc.sql("insert into students(id, parent_id, display_name) values (?, ?, 'Hoc sinh thu')")
                .params(student, parent).update();
        calendar("STUDENT", student);
        return new Student(student, parent);
    }

    public Tutor tutor() {
        UUID tutor = account("TUTOR");
        UUID profile = UUID.randomUUID();
        jdbc.sql("""
                insert into tutor_profiles(id, tutor_id, version, status, description, experience_months,
                                           rate_vnd_60, current)
                values (?, ?, 1, 'APPROVED', repeat('x', 50), 24, ?, true)
                """).params(profile, tutor, RATE).update();
        calendar("TUTOR", tutor);
        return new Tutor(tutor, profile);
    }

    public Need need(Student student) {
        return tx.execute(s -> {
            UUID need = UUID.randomUUID();
            UUID version = UUID.randomUUID();
            jdbc.sql("insert into learning_needs(id, student_id, status) values (?, ?, 'CONFIRMED')")
                    .params(need, student.id()).update();
            jdbc.sql("""
                    insert into need_versions(id, need_id, version, goal, budget_vnd_session, duration_minutes,
                                              frequency, start_date, confirmed_at, student_calendar_id)
                    values (?, ?, 1, repeat('Muc tieu ', 5), 200000, 90, 1, ?, now(), ?)
                    """).params(version, need, cycleStart(), calendarOf("STUDENT", student.id())).update();
            jdbc.sql("update learning_needs set current_version_id = ? where id = ?").params(version, need).update();
            return new Need(need, version, student);
        });
    }

    public UUID offer(Need need, Tutor tutor, OffsetDateTime firstSession) {
        return offer(need, tutor, weekly(firstSession), "1 hour");
    }

    public UUID offer(Need need, Tutor tutor, List<OffsetDateTime> sessions) {
        return offer(need, tutor, sessions, "1 hour");
    }

    private static List<OffsetDateTime> weekly(OffsetDateTime first) {
        return List.of(first, first.plusWeeks(1), first.plusWeeks(2), first.plusWeeks(3));
    }

    /** Sent 49 hours ago, so the 48 hour deadline passed one hour ago. */
    public UUID expiredOffer(Need need, Tutor tutor) {
        return offer(need, tutor, weekly(sessionStart(18, 0)), "49 hours");
    }

    private UUID offer(Need need, Tutor tutor, List<OffsetDateTime> sessions, String sentAgo) {
        return tx.execute(s -> {
            UUID run = UUID.randomUUID();
            jdbc.sql("""
                    insert into matching_runs(id, need_version_id, mode, algorithm_version, config_version, status,
                                              expires_at)
                    values (?, ?, 'B', 'test', 'test', 'COMPLETED', now() + interval '1 day')
                    """).params(run, need.versionId()).update();
            UUID offer = UUID.randomUUID();
            jdbc.sql("""
                    insert into class_offers(id, need_id, need_version_id, tutor_id, profile_id, tutor_calendar_id,
                                             student_calendar_id, run_id, state, goal_snapshot, duration_minutes,
                                             frequency, cycle_start_date, rate_vnd_60, price_vnd_session, sent_at,
                                             expires_at)
                    values (?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', repeat('Muc tieu ', 5), 90, 1, ?, ?, ?,
                            date_trunc('milliseconds', now()) - cast(? as interval),
                            date_trunc('milliseconds', now()) - cast(? as interval) + interval '48 hours')
                    """).params(offer, need.id(), need.versionId(), tutor.id(), tutor.profileId(),
                            calendarOf("TUTOR", tutor.id()), calendarOf("STUDENT", need.student().id()), run,
                            cycleStart(), RATE, PRICE_90_MINUTES, sentAgo, sentAgo).update();
            for (int ordinal = 1; ordinal <= sessions.size(); ordinal++) {
                OffsetDateTime start = sessions.get(ordinal - 1);
                jdbc.sql("insert into offer_sessions(offer_id, ordinal, during) values (?, ?, tstzrange(?, ?, '[)'))")
                        .params(offer, ordinal, start, start.plusMinutes(90)).update();
            }
            return offer;
        });
    }

    public UUID offer(Need need, Tutor tutor) {
        return offer(need, tutor, sessionStart(18, 0));
    }

    public int count(String sql, Object... params) {
        return jdbc.sql(sql).params(params).query(Integer.class).single();
    }
}
