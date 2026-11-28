package com.tutorbridge.identity.internal;

import com.tutorbridge.identity.api.Parties;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
class PartiesRepository implements Parties {

    private final JdbcClient jdbc;
    private final TutorPublicRepository tutors;

    PartiesRepository(JdbcClient jdbc, TutorPublicRepository tutors) {
        this.jdbc = jdbc;
        this.tutors = tutors;
    }

    @Override
    public List<PublicTutor> matchableTutors(int maxRateVnd60, UUID afterTutorId, int limit) {
        return tutors.listMatchable(maxRateVnd60, afterTutorId, limit);
    }

    @Override
    public List<PublicTutor> publicTutorsByProfile(Collection<UUID> profileIds) {
        return tutors.listByProfile(profileIds);
    }

    @Override
    public Optional<PublicTutor> currentPublicTutor(UUID tutorId) {
        return tutors.findCurrent(tutorId);
    }

    @Override
    public List<UUID> ownStudentIds(UUID parentId) {
        List<UUID> ids = jdbc.sql("""
                select s.id from students s join accounts a on a.id = s.parent_id
                where s.parent_id = ? and a.role = 'PARENT' and a.status = 'ACTIVE'
                order by s.id
                limit ?
                """).params(parentId, MAX_OWN_STUDENTS + 1).query(UUID.class).list();
        if (ids.size() > MAX_OWN_STUDENTS) {
            throw new IllegalStateException("A parent has more than " + MAX_OWN_STUDENTS + " students");
        }
        return ids;
    }

    @Override
    public Optional<UUID> parentOf(UUID studentId) {
        return jdbc.sql("select parent_id from students where id = ?").param(studentId).query(UUID.class).optional();
    }

    @Override
    public Map<UUID, String> studentDisplayNames(Collection<UUID> studentIds) {
        if (studentIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, String> names = new HashMap<>();
        jdbc.sql("select id, display_name from students where id = any(?)")
                .param(studentIds.toArray(UUID[]::new))
                .query((rs, n) -> names.put(rs.getObject("id", UUID.class), rs.getString("display_name")))
                .list();
        return names;
    }

    @Override
    public boolean isActiveTutor(UUID accountId) {
        return jdbc.sql("select count(*) from accounts where id = ? and role = 'TUTOR' and status = 'ACTIVE'")
                .param(accountId).query(Integer.class).single() == 1;
    }

    @Override
    public boolean isOwnStudent(UUID parentId, UUID studentId) {
        return jdbc.sql("""
                select count(*) from students s join accounts a on a.id = s.parent_id
                where s.id = ? and s.parent_id = ? and a.role = 'PARENT' and a.status = 'ACTIVE'
                """).params(studentId, parentId).query(Integer.class).single() == 1;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean lockOwnStudent(UUID parentId, UUID studentId) {
        boolean student = jdbc.sql("select 1 from students where id = ? and parent_id = ? for share")
                .params(studentId, parentId).query((rs, n) -> 1).optional().isPresent();
        return student && lockActive(parentId, "PARENT");
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean lockActiveTutor(UUID tutorId) {
        return lockActive(tutorId, "TUTOR");
    }

    private boolean lockActive(UUID accountId, String role) {
        return jdbc.sql("select status from accounts where id = ? and role = ? for share")
                .params(accountId, role).query(String.class).optional().filter("ACTIVE"::equals).isPresent();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<UUID> lockAgreementParties(UUID tutorId, UUID profileId, int rateVnd60, UUID studentId) {
        Optional<UUID> parentId = jdbc.sql("select parent_id from students where id = ? for share")
                .param(studentId).query(UUID.class).optional();
        if (parentId.isEmpty()) {
            return Optional.empty();
        }

        List<String> active = jdbc.sql("""
                select role from accounts
                where id in (?, ?) and status = 'ACTIVE'
                order by id
                for share
                """).params(tutorId, parentId.get()).query(String.class).list();
        boolean profileCurrent = jdbc.sql("""
                select id from tutor_profiles
                where id = ? and tutor_id = ? and status = 'APPROVED' and current and rate_vnd_60 = ?
                for share
                """).params(profileId, tutorId, rateVnd60).query(UUID.class).optional().isPresent();

        boolean eligible = active.contains("TUTOR") && active.contains("PARENT") && profileCurrent;
        return eligible ? parentId : Optional.empty();
    }
}
