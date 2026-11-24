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
UPDATE tutor_verifications SET assigned_at = submitted_at WHERE assigned_to IS NOT NULL;

-- A time without a verifier means nothing. The reverse is allowed, since code on V16 still assigns
-- without the time.
ALTER TABLE tutor_verifications ADD CONSTRAINT tutor_verifications_assigned_at_needs_verifier
 CHECK(assigned_at IS NULL OR assigned_to IS NOT NULL);
