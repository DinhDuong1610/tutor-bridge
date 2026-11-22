-- TutorBridge / #187. Number V13 assigned by Đính (OP-28, 21/11/2026, #98).
-- Additive only: code running on V12 keeps working. Owned by the classes module.
SET search_path = tutorbridge, public;

-- listNeeds pages the needs of one child newest first with the keyset (created_at, id). The same
-- student_id prefix serves the "already has an open need" check of createNeedDraft, which runs under the
-- child's resource lock (INV-16): a child has only a few needs, so no separate index for status <> 'CLOSED'
-- (Đính, 23/11/2026). A plain CREATE INDEX like V10–V12: the table is small in R0.
CREATE INDEX learning_needs_student_order ON learning_needs(student_id, created_at DESC, id DESC);
