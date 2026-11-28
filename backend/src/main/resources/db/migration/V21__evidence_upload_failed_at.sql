-- TutorBridge / TB-182. Number V21 assigned by Đính (28/11/2026, #268), merged after #258 (V20).
-- Additive only: code running on V20 keeps working, it never reads or writes this column. Owned by the
-- identity module.
SET search_path = tutorbridge, public;

-- When an upload became FAILED, so evidence cleanup keeps a failure seven days from then (#182). expires_at
-- cannot stand in for it: complete goes on with a QUARANTINED upload after its ticket expired (a PROCESSING
-- receipt replayed after a crash), and only then moves it to FAILED (Đính on #262, 28/11/2026). The
-- receipt's created_at is the first attempt, and retention removes receipts. Written by the app with its
-- clock when it moves an upload to FAILED; kept when cleanup moves it on to DELETED.
ALTER TABLE tutor_evidence_uploads ADD COLUMN failed_at timestamptz;

-- When each upload failed before V21 is not known: count them from now, so cleanup keeps every one of them
-- at least seven days more instead of removing one that failed just now. The table is small in R0; the
-- ALTER takes a short ACCESS EXCLUSIVE lock and the UPDATE touches only FAILED rows.
UPDATE tutor_evidence_uploads SET failed_at = now() WHERE state = 'FAILED';

-- A failure time on an upload that never failed means nothing. FAILED without a time stays allowed while
-- code on V20 still fails uploads without it; a later migration can require it.
ALTER TABLE tutor_evidence_uploads ADD CONSTRAINT tutor_evidence_uploads_failed_at_needs_failure
 CHECK(failed_at IS NULL OR state IN ('FAILED', 'DELETED'));
