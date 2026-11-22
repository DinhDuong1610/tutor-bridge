package com.tutorbridge.classes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tutorbridge.IntegrationTest;
import com.tutorbridge.classes.OfferFixture.Need;
import com.tutorbridge.classes.OfferFixture.Tutor;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * V15 (TB-17, #17 Q3): the common schedule of a matching candidate. Every rule is tried straight
 * against the database, as the application role, next to a case it lets through.
 */
class MatchingCandidateSessionsMigrationTest extends IntegrationTest {

    @Autowired
    TransactionTemplate tx;

    OfferFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new OfferFixture(jdbc, tx);
    }

    // ---------------------------------------------------------------- fixtures

    private UUID run(Need need) {
        UUID run = UUID.randomUUID();
        jdbc.sql("""
                insert into matching_runs(id, need_version_id, mode, algorithm_version, config_version, status,
                                          expires_at)
                values (?, ?, 'B', 'B-v1', 'none', 'COMPLETED', now() + interval '24 hours')
                """).params(run, need.versionId()).update();
        return run;
    }

    private void candidate(UUID run, Tutor tutor, int rank) {
        UUID calendar = jdbc.sql("select id from calendars where owner_kind = 'TUTOR' and owner_id = ? and effective")
                .param(tutor.id()).query(UUID.class).single();
        jdbc.sql("""
                insert into matching_candidates(run_id, tutor_id, profile_id, tutor_calendar_id, rank, s, e, a, score,
                                                quoted_price, hard_filters_passed)
                values (?, ?, ?, ?, ?, null, 0.4, 0.5, 0.4375, ?, true)
                """).params(run, tutor.id(), tutor.profileId(), calendar, rank, OfferFixture.PRICE_90_MINUTES).update();
    }

    private void session(UUID run, Tutor tutor, int ordinal, OffsetDateTime start, OffsetDateTime end) {
        jdbc.sql("""
                insert into matching_candidate_sessions(run_id, tutor_id, ordinal, during)
                values (?, ?, ?, tstzrange(?, ?, '[)'))
                """).params(run, tutor.id(), ordinal, start, end).update();
    }

    private void session(UUID run, Tutor tutor, int ordinal, OffsetDateTime start) {
        session(run, tutor, ordinal, start, start.plusMinutes(90));
    }

    /** As instants: the database answers in UTC, the fixture builds times at +07:00. */
    private List<Instant> starts(UUID run, Tutor tutor) {
        return jdbc.sql("""
                select lower(during) from matching_candidate_sessions where run_id = ? and tutor_id = ? order by ordinal
                """).params(run, tutor.id()).query(OffsetDateTime.class).list().stream().map(OffsetDateTime::toInstant).toList();
    }

    // ---------------------------------------------------------------- tests

    @Test
    void a_candidate_keeps_its_sessions_in_order_and_two_candidates_may_share_a_time() {
        Need need = fixture.need(fixture.student());
        Tutor first = fixture.tutor();
        Tutor second = fixture.tutor();
        UUID run = run(need);
        candidate(run, first, 1);
        candidate(run, second, 2);
        OffsetDateTime monday = fixture.sessionStart(18, 0);

        for (int week = 0; week < 4; week++) {
            session(run, first, week + 1, monday.plusWeeks(week));
            // Both tutors are free then; offers keep them apart later, not the matching result (INV-13).
            session(run, second, week + 1, monday.plusWeeks(week));
        }

        assertThat(starts(run, first)).containsExactly(monday.toInstant(), monday.plusWeeks(1).toInstant(),
                monday.plusWeeks(2).toInstant(), monday.plusWeeks(3).toInstant());
        assertThat(starts(run, second)).hasSize(4);
    }

    @Test
    void ordinals_run_from_1_to_12_and_are_not_repeated() {
        Need need = fixture.need(fixture.student());
        Tutor tutor = fixture.tutor();
        UUID run = run(need);
        candidate(run, tutor, 1);
        OffsetDateTime start = fixture.sessionStart(18, 0);

        session(run, tutor, 1, start);
        session(run, tutor, 12, start.plusDays(1));
        assertThatThrownBy(() -> session(run, tutor, 0, start.plusDays(2)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> session(run, tutor, 13, start.plusDays(3)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> session(run, tutor, 1, start.plusDays(4))).isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void sessions_of_one_candidate_never_overlap_but_may_touch() {
        Need need = fixture.need(fixture.student());
        Tutor tutor = fixture.tutor();
        UUID run = run(need);
        candidate(run, tutor, 1);
        OffsetDateTime start = fixture.sessionStart(18, 0);

        session(run, tutor, 1, start);
        // Half-open ranges: one ending at 19:30 and the next starting at 19:30 do not overlap.
        session(run, tutor, 2, start.plusMinutes(90));
        assertThatThrownBy(() -> session(run, tutor, 3, start.plusMinutes(30)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void a_session_needs_a_finite_half_open_range() {
        Need need = fixture.need(fixture.student());
        Tutor tutor = fixture.tutor();
        UUID run = run(need);
        candidate(run, tutor, 1);
        OffsetDateTime start = fixture.sessionStart(18, 0);

        for (String range : List.of("'empty'", "tstzrange(?, null, '[)')", "tstzrange(null, ?, '[)')", "tstzrange(?, ?, '[]')",
                "tstzrange(?, ?, '()')")) {
            assertThatThrownBy(() -> {
                var statement = jdbc.sql("insert into matching_candidate_sessions(run_id, tutor_id, ordinal, during) values (?, ?, 1, "
                        + range + ")").param(run).param(tutor.id());
                long placeholders = range.chars().filter(c -> c == '?').count();
                if (placeholders >= 1) statement = statement.param(start);
                if (placeholders == 2) statement = statement.param(start.plusMinutes(90));
                statement.update();
            }).as(range).isInstanceOf(DataIntegrityViolationException.class);
        }
        session(run, tutor, 1, start);
        assertThat(starts(run, tutor)).containsExactly(start.toInstant());
    }

    @Test
    void only_a_stored_candidate_has_sessions() {
        Need need = fixture.need(fixture.student());
        Tutor candidate = fixture.tutor();
        Tutor other = fixture.tutor();
        UUID run = run(need);
        candidate(run, candidate, 1);
        OffsetDateTime start = fixture.sessionStart(18, 0);

        assertThatThrownBy(() -> session(run, other, 1, start)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> session(UUID.randomUUID(), candidate, 1, start))
                .isInstanceOf(DataIntegrityViolationException.class);
        session(run, candidate, 1, start);
    }
}
