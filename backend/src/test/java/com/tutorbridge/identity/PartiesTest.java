package com.tutorbridge.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.tutorbridge.IntegrationTest;
import com.tutorbridge.identity.api.Parties;
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
}
