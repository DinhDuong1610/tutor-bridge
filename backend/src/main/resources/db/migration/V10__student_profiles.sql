-- TutorBridge / TB-11. Number V10 assigned by Đính (11/11/2026).
-- Additive only: code running on V9 keeps working. Tables are owned by the identity module.
SET search_path = tutorbridge, public;

-- listStudents pages with the shared keyset Cursor (time, id); ids are random, so the list needs a
-- creation time to keep children in the order the parent added them. Rows from before V10 get the
-- migration time and fall back to id order among themselves.
ALTER TABLE students ADD COLUMN created_at timestamptz NOT NULL DEFAULT now();
CREATE INDEX students_parent_order ON students(parent_id,created_at,id);

-- UC02 step 3: editing a profile keeps the goal history. One row each time short_goal takes a value,
-- keyed by the students.revision that value became current at. The service only inserts here.
CREATE TABLE student_goal_versions (
 student_id uuid NOT NULL REFERENCES students,
 revision bigint NOT NULL CHECK(revision>0),
 short_goal varchar(2000) NOT NULL,
 changed_by uuid NOT NULL REFERENCES accounts,
 changed_at timestamptz NOT NULL DEFAULT now(),
 PRIMARY KEY(student_id,revision));

-- Backfill: the current goal of every existing profile is its first history row, set by its parent.
INSERT INTO student_goal_versions(student_id,revision,short_goal,changed_by)
SELECT id,revision,short_goal,parent_id FROM students;
