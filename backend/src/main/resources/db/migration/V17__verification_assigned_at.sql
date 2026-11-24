-- TutorBridge / TB-13. Number V17 assigned by Đính (24/11/2026, #13).
-- Additive only: code running on V16 keeps working, it never reads or writes this column. Owned by the
-- identity module.
SET search_path = tutorbridge, public;

-- When a verification was given to its verifier, so the next one goes to the verifier assigned least
-- recently (OP-28 (4), round robin). submitted_at cannot stand in for it: admin-setup assigns records
-- that waited for a verifier long after they were sent (decision 4, Đính 24/11/2026 on #13). Written with
-- clock_timestamp() on each assignment, so one batch still has an order.
ALTER TABLE tutor_verifications ADD COLUMN assigned_at timestamptz;

-- Rows assigned before V17 get the best time known for them. Only test data on dev: no code assigned a
-- verification before TB-13.
-- verification_guard (V5, replaced in V11) checks every updated row against today's state, so it refuses
-- rows that were valid when written and are closed now: a verification of a RETIRED version (OP-24), and a
-- VERIFIED one whose reviewer lost VERIFY or is no longer ACTIVE (Codex and Huy on #223). This backfill
-- changes no decision field (status, reviewer, version), only the new column, so only this trigger is
-- off, and only around it. Flyway runs the script in one transaction: the trigger is on again in the same
-- commit, and a failure rolls both back. The ALTER takes a short ACCESS EXCLUSIVE lock; the table is small
-- in R0.
ALTER TABLE tutor_verifications DISABLE TRIGGER verification_guard;
UPDATE tutor_verifications SET assigned_at = submitted_at WHERE assigned_to IS NOT NULL;
ALTER TABLE tutor_verifications ENABLE TRIGGER verification_guard;

-- A time without a verifier means nothing. The reverse is allowed, since code on V16 still assigns
-- without the time.
ALTER TABLE tutor_verifications ADD CONSTRAINT tutor_verifications_assigned_at_needs_verifier
 CHECK(assigned_at IS NULL OR assigned_to IS NOT NULL);
