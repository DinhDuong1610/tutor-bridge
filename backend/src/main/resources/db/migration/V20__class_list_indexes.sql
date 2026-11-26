-- TutorBridge / #97 (OP-27). Number V20 assigned by Đính (27/11/2026, on #97).
-- Additive only: code running on V19 keeps working. Owned by the classes module.
SET search_path = tutorbridge, public;

-- listClasses pages classes newest first with the keyset (created_at, id): a tutor's own classes, and a
-- parent's classes through their children. Until now classes had no index on either column, so both lists
-- scanned the table. A plain CREATE INDEX like V18: the table is small in R0.
CREATE INDEX classes_tutor_order ON classes(tutor_id, created_at DESC, id DESC);
CREATE INDEX classes_student_order ON classes(student_id, created_at DESC, id DESC);
