-- TB G3 05 v0.1. Candidate DDL only; transaction service is specified separately.
-- Apply after original 001/002 and correction 003. No source data or production connection.
BEGIN;
SET search_path=tutorbridge,public;
ALTER TABLE lesson_sessions ADD CONSTRAINT makeup_same_class FOREIGN KEY(makeup_for_id,class_id) REFERENCES lesson_sessions(id,class_id);
ALTER TABLE lesson_sessions ADD CONSTRAINT makeup_not_self CHECK(makeup_for_id IS DISTINCT FROM id);
CREATE TABLE schedule_change_requests (
 id uuid PRIMARY KEY, class_id uuid NOT NULL REFERENCES classes, original_session_id uuid NOT NULL,
 kind text NOT NULL CHECK(kind IN ('RESCHEDULE','MAKEUP')), proposer_id uuid NOT NULL REFERENCES accounts,
 expected_session_revision bigint NOT NULL CHECK(expected_session_revision>0), proposed_during tstzrange NOT NULL,
 reason varchar(500) NOT NULL CHECK(length(trim(reason))>0),
 state text NOT NULL CHECK(state IN ('PENDING','APPLIED','REJECTED','CANCELLED','EXPIRED','NOT_APPLICABLE')),
 created_at timestamptz NOT NULL, expires_at timestamptz NOT NULL, responded_by uuid REFERENCES accounts,
 responded_at timestamptz, result_session_id uuid, failure_code varchar(100), revision bigint NOT NULL DEFAULT 1 CHECK(revision>0),
 UNIQUE(id,class_id), FOREIGN KEY(original_session_id,class_id) REFERENCES lesson_sessions(id,class_id),
 FOREIGN KEY(result_session_id,class_id) REFERENCES lesson_sessions(id,class_id),
 CHECK(NOT isempty(proposed_during) AND NOT lower_inf(proposed_during) AND NOT upper_inf(proposed_during) AND lower_inc(proposed_during) AND NOT upper_inc(proposed_during)),
 CHECK(expires_at>created_at AND expires_at<=created_at+interval '48 hours'));
CREATE UNIQUE INDEX one_pending_request_per_proposer ON schedule_change_requests(original_session_id,kind,proposer_id) WHERE state='PENDING';
CREATE TABLE session_schedule_history (
 id uuid PRIMARY KEY, session_id uuid NOT NULL, class_id uuid NOT NULL, request_id uuid,
 from_revision bigint NOT NULL CHECK(from_revision>0), to_revision bigint NOT NULL CHECK(to_revision>from_revision),
 before_during tstzrange NOT NULL, after_during tstzrange NOT NULL, actor_id uuid NOT NULL REFERENCES accounts,
 reason varchar(500) NOT NULL, occurred_at timestamptz NOT NULL, command_id uuid NOT NULL REFERENCES command_receipts,
 UNIQUE(session_id,to_revision), FOREIGN KEY(session_id,class_id) REFERENCES lesson_sessions(id,class_id),
 FOREIGN KEY(request_id,class_id) REFERENCES schedule_change_requests(id,class_id));
CREATE TABLE class_end_records (
 class_id uuid PRIMARY KEY REFERENCES classes, actor_id uuid NOT NULL REFERENCES accounts,
 reason varchar(500) NOT NULL CHECK(length(trim(reason))>0), ended_at timestamptz NOT NULL,
 ended_revision bigint NOT NULL CHECK(ended_revision>0), command_id uuid NOT NULL UNIQUE REFERENCES command_receipts);
-- Existing E11 carries a compatibility projection; exact decisions remain append-only below.
ALTER TABLE lesson_log_versions ALTER COLUMN professional_feedback TYPE varchar(5000);
ALTER TABLE lesson_log_versions ADD COLUMN status text NOT NULL DEFAULT 'DRAFT' CHECK(status IN ('DRAFT','PUBLISHED'));
ALTER TABLE lesson_log_versions ADD COLUMN reported_attendance text CHECK(reported_attendance IN ('PRESENT','ABSENT'));
ALTER TABLE lesson_log_versions ADD COLUMN actual_during tstzrange;
ALTER TABLE lesson_log_versions ADD COLUMN taught_content varchar(5000) NOT NULL DEFAULT '';
ALTER TABLE lesson_log_versions ADD COLUMN next_plan varchar(5000) NOT NULL DEFAULT '';
ALTER TABLE lesson_log_versions ADD COLUMN published_by uuid REFERENCES accounts;
ALTER TABLE lesson_log_versions ADD COLUMN published_at timestamptz;
ALTER TABLE lesson_log_versions ADD CONSTRAINT published_log_complete CHECK(status<>'PUBLISHED' OR (reported_attendance IS NOT NULL AND published_by IS NOT NULL AND published_at IS NOT NULL AND tutor_confirmed_at IS NOT NULL));
ALTER TABLE lesson_log_versions ADD CONSTRAINT present_log_actual_range CHECK(status<>'PUBLISHED' OR reported_attendance<>'PRESENT' OR (actual_during IS NOT NULL AND NOT isempty(actual_during) AND NOT lower_inf(actual_during) AND NOT upper_inf(actual_during) AND lower_inc(actual_during) AND NOT upper_inc(actual_during)));
ALTER TABLE lesson_log_versions ADD CONSTRAINT log_version_session_scope UNIQUE(id,session_id,class_id);
CREATE TABLE lesson_log_heads (
 session_id uuid PRIMARY KEY, class_id uuid NOT NULL, current_published_version_id uuid,
 revision bigint NOT NULL DEFAULT 1 CHECK(revision>0),
 FOREIGN KEY(session_id,class_id) REFERENCES lesson_sessions(id,class_id),
 FOREIGN KEY(current_published_version_id,session_id,class_id) REFERENCES lesson_log_versions(id,session_id,class_id));
CREATE TABLE attendance_decisions (
 id uuid PRIMARY KEY, log_version_id uuid NOT NULL, class_id uuid NOT NULL,
 decision_revision integer NOT NULL CHECK(decision_revision>0), actor_id uuid NOT NULL REFERENCES accounts,
 decision text NOT NULL CHECK(decision IN ('AGREE','DISPUTE')), note varchar(5000) NOT NULL DEFAULT '',
 decided_at timestamptz NOT NULL, command_id uuid NOT NULL UNIQUE REFERENCES command_receipts,
 UNIQUE(log_version_id,decision_revision), FOREIGN KEY(log_version_id,class_id) REFERENCES lesson_log_versions(id,class_id),
 CHECK(decision<>'DISPUTE' OR length(trim(note))>0));
CREATE TABLE confirmation_reminders (
 log_version_id uuid NOT NULL REFERENCES lesson_log_versions, recipient_id uuid NOT NULL REFERENCES accounts,
 reminder_kind text NOT NULL CHECK(reminder_kind='AFTER_72_HOURS'), outbox_id uuid NOT NULL UNIQUE REFERENCES outbox_events,
 created_at timestamptz NOT NULL, PRIMARY KEY(log_version_id,recipient_id,reminder_kind));
CREATE TABLE assignments (
 id uuid PRIMARY KEY, class_id uuid NOT NULL REFERENCES classes, session_id uuid NOT NULL,
 current_issued_version_id uuid, state text NOT NULL CHECK(state IN ('DRAFT','OPEN','CLOSED')),
 revision bigint NOT NULL DEFAULT 1 CHECK(revision>0), closed_at timestamptz, closure_reason varchar(100),
 UNIQUE(id,class_id), FOREIGN KEY(session_id,class_id) REFERENCES lesson_sessions(id,class_id),
 CHECK(state<>'CLOSED' OR closed_at IS NOT NULL));
CREATE TABLE assignment_versions (
 id uuid PRIMARY KEY, assignment_id uuid NOT NULL, class_id uuid NOT NULL, version integer NOT NULL CHECK(version>0),
 source_plan_version_id uuid NOT NULL, question text NOT NULL CHECK(length(trim(question))>0), due_at timestamptz,
 status text NOT NULL CHECK(status IN ('DRAFT','APPROVED')), approved_by uuid REFERENCES accounts, approved_at timestamptz,
 issued_at timestamptz, superseded_at timestamptz, UNIQUE(assignment_id,version), UNIQUE(id,class_id), UNIQUE(id,assignment_id,class_id),
 FOREIGN KEY(assignment_id,class_id) REFERENCES assignments(id,class_id), FOREIGN KEY(source_plan_version_id,class_id) REFERENCES plan_versions(id,class_id),
 CHECK(status<>'APPROVED' OR (approved_by IS NOT NULL AND approved_at IS NOT NULL)), CHECK(issued_at IS NULL OR status='APPROVED'));
ALTER TABLE assignments ADD CONSTRAINT issued_version_same_assignment FOREIGN KEY(current_issued_version_id,id,class_id) REFERENCES assignment_versions(id,assignment_id,class_id) DEFERRABLE INITIALLY DEFERRED;
CREATE TABLE assignment_private_answers (
 assignment_version_id uuid PRIMARY KEY, class_id uuid NOT NULL, answer_text text NOT NULL,
 FOREIGN KEY(assignment_version_id,class_id) REFERENCES assignment_versions(id,class_id));
CREATE TABLE assignment_answer_releases (
 assignment_version_id uuid PRIMARY KEY, class_id uuid NOT NULL, released_by uuid NOT NULL REFERENCES accounts,
 released_at timestamptz NOT NULL, closed_at_snapshot timestamptz NOT NULL, command_id uuid NOT NULL UNIQUE REFERENCES command_receipts,
 FOREIGN KEY(assignment_version_id,class_id) REFERENCES assignment_versions(id,class_id), CHECK(released_at>=closed_at_snapshot));
ALTER TABLE classes ADD CONSTRAINT class_student_scope UNIQUE(id,student_id);
CREATE TABLE submission_attempts (
 id uuid PRIMARY KEY, assignment_id uuid NOT NULL, assignment_version_id uuid NOT NULL, class_id uuid NOT NULL,
 student_id uuid NOT NULL REFERENCES students, actor_id uuid NOT NULL REFERENCES accounts, actor_role text NOT NULL CHECK(actor_role IN ('PARENT','STUDENT')),
 linked_student_id uuid REFERENCES student_links(student_id), link_revision bigint CHECK(link_revision>0), attempt_number integer NOT NULL CHECK(attempt_number>0),
 text_answer varchar(10000) NOT NULL DEFAULT '', received_at timestamptz NOT NULL, due_at_snapshot timestamptz,
 late boolean GENERATED ALWAYS AS (due_at_snapshot IS NOT NULL AND received_at>due_at_snapshot) STORED,
 command_id uuid NOT NULL UNIQUE REFERENCES command_receipts, UNIQUE(assignment_id,student_id,attempt_number), UNIQUE(id,class_id),
 FOREIGN KEY(assignment_version_id,assignment_id,class_id) REFERENCES assignment_versions(id,assignment_id,class_id),
 FOREIGN KEY(class_id,student_id) REFERENCES classes(id,student_id),
 CHECK(actor_role<>'STUDENT' OR (linked_student_id IS NOT NULL AND linked_student_id=student_id AND link_revision IS NOT NULL)));
CREATE TABLE submission_attachments (
 id uuid PRIMARY KEY, submission_id uuid NOT NULL, class_id uuid NOT NULL, ordinal smallint NOT NULL CHECK(ordinal BETWEEN 1 AND 5),
 object_key text NOT NULL UNIQUE, media_type text NOT NULL CHECK(media_type IN ('image/jpeg','image/png')),
 size_bytes integer NOT NULL CHECK(size_bytes BETWEEN 1 AND 5242880), sha256 char(64) NOT NULL CHECK(sha256~'^[0-9a-f]{64}$'),
 UNIQUE(submission_id,ordinal), FOREIGN KEY(submission_id,class_id) REFERENCES submission_attempts(id,class_id));
CREATE TABLE submission_review_versions (
 id uuid PRIMARY KEY, submission_id uuid NOT NULL, class_id uuid NOT NULL, version integer NOT NULL CHECK(version>0),
 reviewed_by uuid NOT NULL REFERENCES accounts, feedback varchar(5000) NOT NULL CHECK(length(trim(feedback))>0),
 reviewed_at timestamptz NOT NULL, command_id uuid NOT NULL UNIQUE REFERENCES command_receipts,
 UNIQUE(submission_id,version), FOREIGN KEY(submission_id,class_id) REFERENCES submission_attempts(id,class_id));
CREATE TABLE evaluation_events (
 id uuid PRIMARY KEY, outbox_event_id uuid NOT NULL REFERENCES outbox_events, event_type varchar(100) NOT NULL,
 actor_pseudonym varchar(128) NOT NULL, subject_pseudonym varchar(128) NOT NULL,
 flow_version varchar(100) NOT NULL, object_version varchar(100) NOT NULL, outcome_code varchar(100) NOT NULL,
 occurred_at timestamptz NOT NULL, UNIQUE(outbox_event_id,event_type));
REVOKE ALL ON ALL TABLES IN SCHEMA tutorbridge FROM PUBLIC;
COMMIT;
