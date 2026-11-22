package com.tutorbridge.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.tutorbridge.IntegrationTest;
import com.tutorbridge.identity.api.Parties;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** What other modules may ask identity about student ownership (TB-15, INV-82). Fake data only. */
class PartiesTest extends IntegrationTest {

    @Autowired
    private Parties parties;

    private UUID student(UUID parentId) {
        UUID id = UUID.randomUUID();
        jdbc.sql("insert into students(id, parent_id, display_name) values (?, ?, 'Hoc sinh thu')")
                .params(id, parentId).update();
        return id;
    }

    @Test
    void a_parent_owns_only_their_own_student() {
        UUID parent = newAccount();
        UUID otherParent = newAccount();
        UUID own = student(parent);
        UUID otherFamily = student(otherParent);

        assertThat(parties.isOwnStudent(parent, own)).isTrue();
        assertThat(parties.isOwnStudent(parent, otherFamily)).isFalse();
        assertThat(parties.isOwnStudent(otherParent, own)).isFalse();
    }

    @Test
    void an_unknown_student_is_not_owned() {
        assertThat(parties.isOwnStudent(newAccount(), UUID.randomUUID())).isFalse();
    }

    @Test
    void a_parent_that_is_no_longer_active_owns_no_student() {
        UUID parent = newAccount();
        UUID own = student(parent);
        jdbc.sql("update accounts set status = 'LOCKED' where id = ?").param(parent).update();

        assertThat(parties.isOwnStudent(parent, own)).isFalse();
    }

    // ---------------------------------------------------------------- matchableTutors (TB-17, BR01)

    private UUID tutor(String displayName, String status) {
        UUID id = UUID.randomUUID();
        jdbc.sql("insert into accounts(id, role, status, display_name) values (?, 'TUTOR', ?, ?)")
                .params(id, status, displayName).update();
        return id;
    }

    private UUID profile(UUID tutorId, int version, String status, boolean current, int rate) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                insert into tutor_profiles(id, tutor_id, version, status, description, experience_months, rate_vnd_60,
                                           current)
                values (?, ?, ?, ?, 'Dạy đại số lớp 8 theo từng bước, có bài tập về nhà mỗi buổi học.', 30, ?, ?)
                """).params(id, tutorId, version, status, rate, current).update();
        return id;
    }

    /** Every page from the start, keeping only the tutors this test made (other tests share the database). */
    private List<Parties.PublicTutor> all(int maxRate, List<UUID> mine, int pageSize) {
        List<Parties.PublicTutor> found = new ArrayList<>();
        UUID after = null;
        while (true) {
            List<Parties.PublicTutor> page = parties.matchableTutors(maxRate, after, pageSize);
            assertThat(page).hasSizeLessThanOrEqualTo(pageSize);
            page.stream().filter(t -> mine.contains(t.tutorId())).forEach(found::add);
            if (page.size() < pageSize) {
                return found;
            }
            after = page.get(page.size() - 1).tutorId();
        }
    }

    @Test
    void BR01_only_active_named_tutors_with_a_current_approved_profile_are_matchable() {
        UUID ok = tutor("Cô Lan Thử", "ACTIVE");
        UUID okProfile = profile(ok, 1, "APPROVED", true, 200000);
        UUID locked = tutor("Thầy Nam Thử", "LOCKED");
        profile(locked, 1, "APPROVED", true, 200000);
        UUID nameless = tutor(null, "ACTIVE");
        profile(nameless, 1, "APPROVED", true, 200000);
        UUID waiting = tutor("Cô Mai Thử", "ACTIVE");
        profile(waiting, 1, "PENDING", false, 200000);
        UUID parent = newAccount();

        List<Parties.PublicTutor> found = all(1_000_000, List.of(ok, locked, nameless, waiting, parent), 50);

        assertThat(found).extracting(Parties.PublicTutor::tutorId).containsExactly(ok);
        Parties.PublicTutor only = found.getFirst();
        assertThat(only.profileVersionId()).isEqualTo(okProfile);
        assertThat(only.displayName()).isEqualTo("Cô Lan Thử");
        assertThat(only.rateVnd60()).isEqualTo(200000);
        assertThat(only.experienceMonths()).isEqualTo(30);
        assertThat(only.verificationScopes()).isEmpty();
    }

    @Test
    void BR02_the_rate_ceiling_is_inclusive() {
        UUID atCeiling = tutor("Cô Lan Thử", "ACTIVE");
        profile(atCeiling, 1, "APPROVED", true, 200000);
        UUID above = tutor("Thầy Minh Thử", "ACTIVE");
        profile(above, 1, "APPROVED", true, 200001);

        assertThat(all(200000, List.of(atCeiling, above), 50)).extracting(Parties.PublicTutor::tutorId)
                .containsExactly(atCeiling);
    }

    @Test
    void INV_81_pages_are_bounded_and_ordered_by_tutor_id_with_nothing_lost_or_repeated() {
        List<UUID> mine = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            UUID id = tutor("Gia sư thử " + i, "ACTIVE");
            profile(id, 1, "APPROVED", true, 150000);
            mine.add(id);
        }

        List<UUID> found = all(150000, mine, 2).stream().map(Parties.PublicTutor::tutorId).toList();

        assertThat(found).containsExactlyElementsOf(mine.stream().sorted(Comparator.comparing(UUID::toString)).toList());
    }

    // ---------------------------------------------------------------- publicTutorsByProfile (TB-17, #17 Q7)

    @Test
    void Q7_a_stored_run_reads_the_versions_it_showed_even_after_a_newer_one_is_approved() {
        UUID tutor = tutor("Cô Lan Thử", "ACTIVE");
        UUID shown = profile(tutor, 1, "RETIRED", false, 200000);
        UUID newer = profile(tutor, 2, "APPROVED", true, 300000);

        List<Parties.PublicTutor> found = parties.publicTutorsByProfile(List.of(shown));

        assertThat(found).singleElement().satisfies(t -> {
            assertThat(t.tutorId()).isEqualTo(tutor);
            assertThat(t.profileVersionId()).isEqualTo(shown);
            assertThat(t.rateVnd60()).isEqualTo(200000);
            assertThat(t.displayName()).isEqualTo("Cô Lan Thử");
        });
        assertThat(parties.publicTutorsByProfile(List.of(newer))).extracting(Parties.PublicTutor::rateVnd60)
                .containsExactly(300000);
    }

    @Test
    void Q7_unknown_ids_are_left_out_and_more_than_a_run_holds_is_refused() {
        UUID tutor = tutor("Cô Lan Thử", "ACTIVE");
        UUID shown = profile(tutor, 1, "APPROVED", true, 200000);

        assertThat(parties.publicTutorsByProfile(List.of(shown, UUID.randomUUID())))
                .extracting(Parties.PublicTutor::profileVersionId).containsExactly(shown);
        assertThat(parties.publicTutorsByProfile(List.of())).isEmpty();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> parties.publicTutorsByProfile(
                        java.util.stream.Stream.generate(UUID::randomUUID).limit(6).toList()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
