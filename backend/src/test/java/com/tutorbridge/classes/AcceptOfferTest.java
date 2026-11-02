package com.tutorbridge.classes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tutorbridge.IntegrationTest;
import com.tutorbridge.classes.OfferFixture.Need;
import com.tutorbridge.classes.OfferFixture.Tutor;
import com.tutorbridge.classes.api.AcceptOffer;
import com.tutorbridge.classes.api.AcceptOffer.AcceptedClass;
import com.tutorbridge.platform.api.ApiException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

class AcceptOfferTest extends IntegrationTest {

    @Autowired
    AcceptOffer acceptOffer;

    @Autowired
    TransactionTemplate tx;

    OfferFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new OfferFixture(jdbc, tx);
    }

    private static String key() {
        return UUID.randomUUID().toString();
    }

    private AcceptedClass accept(Tutor tutor, UUID offer) {
        return acceptOffer.accept(tutor.id(), offer, 1, key());
    }

    private void assertRejected(ThrowingCallable call, int status, String code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.code()).isEqualTo(code);
            assertThat(e.status().value()).isEqualTo(status);
        });
    }

    private void assertNothingCreated(UUID offer, Need need) {
        assertThat(fixture.count("select count(*) from classes where originating_need_id = ?", need.id())).isZero();
        assertThat(fixture.count("""
                select count(*) from lesson_sessions s
                where not exists (select 1 from classes c where c.id = s.class_id)
                """)).isZero();
        assertThat(jdbc.sql("select state from class_offers where id = ?").param(offer).query(String.class).single())
                .isEqualTo("PENDING");
        assertThat(jdbc.sql("select status from learning_needs where id = ?").param(need.id())
                .query(String.class).single()).isEqualTo("CONFIRMED");
    }

    @Test
    void accepting_creates_class_goal_sessions_and_two_bookings_per_session() {
        Need need = fixture.need(fixture.student());
        Tutor tutor = fixture.tutor();
        UUID offer = fixture.offer(need, tutor);

        AcceptedClass created = accept(tutor, offer);

        assertThat(created.state()).isEqualTo("WAITING");
        assertThat(created.priceVndSession()).isEqualTo(OfferFixture.PRICE_90_MINUTES);
        assertThat(fixture.count("select count(*) from lesson_sessions where class_id = ?", created.id())).isEqualTo(4);
        assertThat(fixture.count("""
                select count(*) from resource_bookings b join lesson_sessions s on s.id = b.session_id
                where s.class_id = ? and b.active
                """, created.id())).isEqualTo(8);
        assertThat(fixture.count("""
                select count(*) from class_goal_versions
                where class_id = ? and status = 'EFFECTIVE' and proposed_by = ?
                """, created.id(), need.student().parentId())).isEqualTo(1);
        assertThat(fixture.count("select count(*) from class_offers where id = ? and state = 'ACCEPTED' and revision = 2",
                offer)).isEqualTo(1);
        assertThat(fixture.count("select count(*) from learning_needs where id = ? and status = 'CLOSED'", need.id()))
                .isEqualTo(1);
        assertThat(fixture.count("select count(*) from outbox_events where aggregate_id = ? and event_type = 'CLASS_CREATED'",
                created.id())).isEqualTo(1);
    }

    @Test
    void AC_UC06_01_other_tutor_gets_the_same_answer_as_a_missing_offer() {
        Need need = fixture.need(fixture.student());
        UUID offer = fixture.offer(need, fixture.tutor());
        Tutor other = fixture.tutor();

        assertRejected(() -> accept(other, offer), 404, "OFFER_NOT_FOUND");
        assertRejected(() -> accept(other, UUID.randomUUID()), 404, "OFFER_NOT_FOUND");
        assertNothingCreated(offer, need);
    }

    @Test
    void AC_UC06_01_rejected_or_cancelled_offer_creates_no_class() {
        for (String state : new String[] {"REJECTED", "CANCELLED"}) {
            Need need = fixture.need(fixture.student());
            Tutor tutor = fixture.tutor();
            UUID offer = fixture.offer(need, tutor);
            jdbc.sql("update class_offers set state = ?, concluded_at = now() where id = ?").params(state, offer).update();

            assertRejected(() -> accept(tutor, offer), 409, "SCOPE_CHANGED");
            assertThat(fixture.count("select count(*) from classes where accepted_offer_id = ?", offer)).isZero();
        }
    }

    @Test
    void AC_UC06_01_expired_offer_creates_no_class_even_before_the_expiry_job_runs() {
        Need need = fixture.need(fixture.student());
        Tutor tutor = fixture.tutor();
        UUID offer = fixture.expiredOffer(need, tutor);

        assertRejected(() -> accept(tutor, offer), 409, "OFFER_EXPIRED");
        assertNothingCreated(offer, need);
    }

    @Test
    void AC_UC06_03_second_offer_of_a_need_is_cancelled_with_a_reason() {
        Need need = fixture.need(fixture.student());
        Tutor first = fixture.tutor();
        Tutor second = fixture.tutor();
        UUID accepted = fixture.offer(need, first);
        UUID competing = fixture.offer(need, second);

        accept(first, accepted);

        assertThat(fixture.count("""
                select count(*) from class_offers
                where id = ? and state = 'CANCELLED' and reason_code = 'NEED_CLOSED' and revision = 2
                """, competing)).isEqualTo(1);
        assertThat(fixture.count("select count(*) from outbox_events where aggregate_id = ?", competing)).isEqualTo(1);
        assertRejected(() -> accept(second, competing), 409, "SCOPE_CHANGED");
        assertThat(fixture.count("select count(*) from classes where originating_need_id = ?", need.id())).isEqualTo(1);
    }

    @Test
    void AC_UC06_04_same_key_replays_without_new_effect() {
        Need need = fixture.need(fixture.student());
        Tutor tutor = fixture.tutor();
        UUID offer = fixture.offer(need, tutor);
        String key = key();

        AcceptedClass first = acceptOffer.accept(tutor.id(), offer, 1, key);
        AcceptedClass replay = acceptOffer.accept(tutor.id(), offer, 1, key);

        assertThat(replay).isEqualTo(first);
        assertThat(fixture.count("select count(*) from classes where originating_need_id = ?", need.id())).isEqualTo(1);
        assertThat(fixture.count("select count(*) from outbox_events where aggregate_id = ?", first.id())).isEqualTo(1);
    }

    @Test
    void AC_UC06_04_same_key_with_other_payload_is_a_409() {
        Need need = fixture.need(fixture.student());
        Tutor tutor = fixture.tutor();
        UUID offer = fixture.offer(need, tutor);
        String key = key();
        acceptOffer.accept(tutor.id(), offer, 1, key);

        assertRejected(() -> acceptOffer.accept(tutor.id(), offer, 2, key), 409, "IDEMPOTENCY_KEY_REUSED");
    }

    @Test
    void INV_25_new_key_after_receipt_cleanup_cannot_create_a_second_class() {
        Need need = fixture.need(fixture.student());
        Tutor tutor = fixture.tutor();
        UUID offer = fixture.offer(need, tutor);
        AcceptedClass created = accept(tutor, offer);
        jdbc.sql("delete from command_receipts where id = ?").param(created.operationId()).update();

        assertRejected(() -> accept(tutor, offer), 409, "SCOPE_CHANGED");
        assertThat(fixture.count("select count(*) from classes where originating_need_id = ?", need.id())).isEqualTo(1);
    }

    @Test
    void INV_22_replay_rechecks_that_the_actor_is_still_active() {
        Need need = fixture.need(fixture.student());
        Tutor tutor = fixture.tutor();
        UUID offer = fixture.offer(need, tutor);
        String key = key();
        acceptOffer.accept(tutor.id(), offer, 1, key);
        jdbc.sql("update accounts set status = 'LOCKED' where id = ?").param(tutor.id()).update();

        assertRejected(() -> acceptOffer.accept(tutor.id(), offer, 1, key), 403, "ACCESS_UNAVAILABLE");
    }

    @Test
    void INV_23_stale_revision_is_a_412() {
        Need need = fixture.need(fixture.student());
        Tutor tutor = fixture.tutor();
        UUID offer = fixture.offer(need, tutor);

        assertRejected(() -> acceptOffer.accept(tutor.id(), offer, 7, key()), 412, "VERSION_CONFLICT");
        assertNothingCreated(offer, need);
    }

    @Test
    void INV_10_conflict_on_the_last_session_rolls_back_everything() {
        Tutor tutor = fixture.tutor();
        OffsetDateTime morning = fixture.sessionStart(8, 0);
        OffsetDateTime collidingLast = fixture.sessionStart(18, 0).plusWeeks(3);
        Need existingNeed = fixture.need(fixture.student());
        accept(tutor, fixture.offer(existingNeed, tutor,
                List.of(morning, morning.plusWeeks(1), morning.plusWeeks(2), collidingLast)));

        Need need = fixture.need(fixture.student());
        UUID offer = fixture.offer(need, tutor);
        String key = key();
        int outboxBefore = fixture.count("select count(*) from outbox_events");

        assertRejected(() -> acceptOffer.accept(tutor.id(), offer, 1, key), 409, "SCHEDULE_CONFLICT");

        assertNothingCreated(offer, need);
        assertThat(fixture.count("select count(*) from outbox_events")).isEqualTo(outboxBefore);
        assertThat(fixture.count("select count(*) from command_receipts where idempotency_key = ?", key)).isZero();
    }

    @Test
    void INV_11_sessions_touching_at_the_boundary_are_both_valid() {
        Tutor tutor = fixture.tutor();
        Need early = fixture.need(fixture.student());
        Need late = fixture.need(fixture.student());
        UUID earlyOffer = fixture.offer(early, tutor, fixture.sessionStart(18, 0));
        UUID lateOffer = fixture.offer(late, tutor, fixture.sessionStart(19, 30));

        accept(tutor, earlyOffer);
        accept(tutor, lateOffer);

        assertThat(fixture.count("select count(*) from classes where tutor_id = ?", tutor.id())).isEqualTo(2);
    }

    @Test
    void locked_parent_or_tutor_profile_change_blocks_the_class() {
        Need lockedParent = fixture.need(fixture.student());
        Tutor tutor = fixture.tutor();
        UUID offer = fixture.offer(lockedParent, tutor);
        jdbc.sql("update accounts set status = 'LOCKED' where id = ?").param(lockedParent.student().parentId()).update();
        assertRejected(() -> accept(tutor, offer), 409, "SCOPE_CHANGED");
        assertNothingCreated(offer, lockedParent);

        Need retired = fixture.need(fixture.student());
        Tutor retiredTutor = fixture.tutor();
        UUID retiredOffer = fixture.offer(retired, retiredTutor);
        jdbc.sql("update tutor_profiles set status = 'RETIRED', current = false where id = ?")
                .param(retiredTutor.profileId()).update();
        assertRejected(() -> accept(retiredTutor, retiredOffer), 409, "SCOPE_CHANGED");
        assertNothingCreated(retiredOffer, retired);
    }

    @Test
    void locked_tutor_cannot_accept() {
        Need need = fixture.need(fixture.student());
        Tutor tutor = fixture.tutor();
        UUID offer = fixture.offer(need, tutor);
        jdbc.sql("update accounts set status = 'LOCKED' where id = ?").param(tutor.id()).update();

        assertRejected(() -> accept(tutor, offer), 403, "ACCESS_UNAVAILABLE");
        assertNothingCreated(offer, need);
    }

    @Test
    void new_need_version_invalidates_the_offer() {
        Need need = fixture.need(fixture.student());
        Tutor tutor = fixture.tutor();
        UUID offer = fixture.offer(need, tutor);
        tx.executeWithoutResult(s -> {
            UUID version = UUID.randomUUID();
            jdbc.sql("""
                    insert into need_versions(id, need_id, version, goal)
                    values (?, ?, 2, repeat('Muc tieu moi ', 5))
                    """).params(version, need.id()).update();
            jdbc.sql("update learning_needs set current_version_id = ? where id = ?").params(version, need.id()).update();
        });

        assertRejected(() -> accept(tutor, offer), 409, "SCOPE_CHANGED");
        assertNothingCreated(offer, need);
    }

    @Test
    void INV_13_a_session_blocked_after_the_offer_was_sent_is_a_conflict() {
        Need need = fixture.need(fixture.student());
        Tutor tutor = fixture.tutor();
        UUID offer = fixture.offer(need, tutor);
        OffsetDateTime last = fixture.sessionStart(18, 0).plusWeeks(3);
        jdbc.sql("""
                insert into availability_windows(id, calendar_id, during, kind)
                select ?, id, tstzrange(?, ?, '[)'), 'BLOCKED'
                from calendars where owner_kind = 'STUDENT' and owner_id = ? and effective
                """).params(UUID.randomUUID(), last.plusMinutes(30), last.plusMinutes(40), need.student().id()).update();

        assertRejected(() -> accept(tutor, offer), 409, "SCHEDULE_CONFLICT");
        assertNothingCreated(offer, need);
    }

    @Test
    void INV_13_a_session_outside_the_available_windows_is_a_conflict() {
        Need need = fixture.need(fixture.student());
        Tutor tutor = fixture.tutor();
        UUID offer = fixture.offer(need, tutor);
        jdbc.sql("""
                update availability_windows set during = tstzrange(lower(during), ?, '[)')
                where calendar_id = (select id from calendars where owner_kind = 'TUTOR' and owner_id = ? and effective)
                """).params(fixture.sessionStart(18, 0).plusWeeks(3).plusMinutes(60), tutor.id()).update();

        assertRejected(() -> accept(tutor, offer), 409, "SCHEDULE_CONFLICT");
        assertNothingCreated(offer, need);
    }

    @Test
    void application_role_cannot_write_bookings_directly() {
        assertThatThrownBy(() -> jdbc.sql("delete from resource_bookings").update())
                .rootCause().hasMessageContaining("permission denied");
    }
}
