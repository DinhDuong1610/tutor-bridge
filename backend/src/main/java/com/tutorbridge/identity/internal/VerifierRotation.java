package com.tutorbridge.identity.internal;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Who gets the next verification (OP-28 (4), Đính 21/11/2026 on #98): the ADMINs that are ACTIVE and hold
 * VERIFY, by turns. The turn goes to the one assigned least recently, one never assigned first, ties by
 * account id; for a fixed group of verifiers that is plain round robin. The time is V17's assigned_at
 * (Đính 24/11/2026 on #13), not submitted_at: admin-setup gives out records long after they were sent.
 *
 * <p>The VERIFY rows are locked FOR UPDATE, last in the lock order of the verification commands (INV-16;
 * see docs/handoff/TB-13.md), so two assignments run in turn and the second sees the first. The times are
 * read by a second statement after the lock, so they include what the transaction it waited for committed.
 */
@Component
class VerifierRotation {

    /** The verifiers in turn order. {@link #next} walks them round and round, so one batch shares fairly. */
    static final class Turns {

        private final List<UUID> order;
        private int position;

        Turns(List<UUID> order) {
            this.order = order;
        }

        /** The next verifier who is not {@code tutorId} (INV-05), or null when there is none (decision 4). */
        UUID next(UUID tutorId) {
            for (int tried = 0; tried < order.size(); tried++) {
                UUID candidate = order.get(position);
                position = (position + 1) % order.size();
                if (!candidate.equals(tutorId)) {
                    return candidate;
                }
            }
            return null;
        }
    }

    private final JdbcClient jdbc;

    VerifierRotation(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    Turns lock() {
        List<UUID> verifiers = jdbc.sql("""
                select g.account_id from admin_assignments g join accounts a on a.id = g.account_id
                where g.capability = 'VERIFY' and g.revoked_at is null and a.role = 'ADMIN' and a.status = 'ACTIVE'
                order by g.account_id
                for update of g""").query(UUID.class).list();
        if (verifiers.isEmpty()) {
            return new Turns(List.of());
        }
        Map<UUID, Instant> last = new HashMap<>();
        jdbc.sql("""
                select v.assigned_to, max(v.assigned_at) from tutor_verifications v
                where v.assigned_to in (:verifiers) and v.assigned_at is not null group by v.assigned_to""")
                .param("verifiers", verifiers)
                .query(rs -> {
                    last.put(rs.getObject(1, UUID.class), rs.getObject(2, OffsetDateTime.class).toInstant());
                });
        // Ties by the text of the id, which is PostgreSQL's uuid order; UUID.compareTo is signed and differs.
        List<UUID> order = new ArrayList<>(verifiers);
        order.sort(Comparator.<UUID, Instant>comparing(last::get, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(UUID::toString));
        return new Turns(order);
    }
}
