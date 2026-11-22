package com.tutorbridge.identity.internal;

import com.tutorbridge.identity.api.Parties.PublicTutor;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * The current approved version of tutors that parents may see (BR01, BR07, INV-30). Both getTutorPublic
 * and matching read through here, so the two never disagree on who is shown.
 */
@Repository
class TutorPublicRepository {

    /** R0's only segment (INV-30); the subject column holds the code since V6. */
    static final String SUBJECT = "MATH8_ALGEBRA";

    // A tutor is shown only with an ACTIVE account, a display name and a current APPROVED profile; missing
    // data is not a pass (INV-35). Scopes are this version's VERIFIED decisions, at most 20 (TutorPublic).
    private static final String SELECT = """
            select a.id as tutor_id, p.id as profile_id, a.display_name, p.description, p.rate_vnd_60,
                   p.experience_months, p.revision,
                   array(select distinct v.scope from tutor_verifications v
                         where v.profile_id = p.id and v.status = 'VERIFIED' order by 1 limit 20) as scopes
            from accounts a
            join tutor_profiles p on p.tutor_id = a.id and p.current and p.status = 'APPROVED'
            where a.role = 'TUTOR' and a.status = 'ACTIVE' and a.display_name is not null and p.subject = ?
            """;

    private final JdbcClient jdbc;

    TutorPublicRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    Optional<PublicTutor> findCurrent(UUID tutorId) {
        return jdbc.sql(SELECT + " and a.id = ?").params(SUBJECT, tutorId).query(TutorPublicRepository::row).optional();
    }

    List<PublicTutor> listMatchable(int maxRateVnd60, UUID afterTutorId, int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be positive");
        }
        return jdbc.sql(SELECT + " and p.rate_vnd_60 <= ? and (?::uuid is null or a.id > ?) order by a.id limit ?")
                .params(SUBJECT, maxRateVnd60, afterTutorId, afterTutorId, limit)
                .query(TutorPublicRepository::row).list();
    }

    private static PublicTutor row(ResultSet rs, int n) throws SQLException {
        String[] scopes = (String[]) rs.getArray("scopes").getArray();
        return new PublicTutor(rs.getObject("tutor_id", UUID.class), rs.getObject("profile_id", UUID.class),
                rs.getString("display_name"), rs.getString("description"), rs.getInt("rate_vnd_60"),
                rs.getInt("experience_months"), List.copyOf(Arrays.asList(scopes)), rs.getLong("revision"));
    }
}
