-- TutorBridge / #98 (OP-28). Number V12 assigned by Đính (21/11/2026, #98).
-- Additive only: code running on V11 keeps working. submitTutorProfile inserts without the new column
-- and the default fills it, so that code already records the right time. Owned by the identity module.
SET search_path = tutorbridge, public;

-- AD03 shows when a profile version was sent for verification. Receipts are cleaned up (TB-24) and
-- audit_events only records a replaced verification, so neither can give this time later. Rows from
-- before V12 are test data on dev and get the migration time.
ALTER TABLE tutor_verifications ADD COLUMN submitted_at timestamptz NOT NULL DEFAULT now();

-- listAssignedVerifications: one verifier's records, oldest submission first, keyset (time, id).
CREATE INDEX tutor_verifications_assigned_queue ON tutor_verifications(assigned_to,submitted_at,id);
