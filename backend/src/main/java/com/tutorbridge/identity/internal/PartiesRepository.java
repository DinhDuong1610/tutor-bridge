package com.tutorbridge.identity.internal;

import com.tutorbridge.identity.api.Parties;
import java.util.Collection;
import java.util.List;
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
