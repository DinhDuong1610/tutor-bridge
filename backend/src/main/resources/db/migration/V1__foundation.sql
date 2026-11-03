-- TutorBridge / TB G3 03 v0.1 / PostgreSQL 17 candidate
-- Design baseline: service authorization and transaction contracts remain mandatory.
BEGIN;
CREATE EXTENSION IF NOT EXISTS btree_gist;
CREATE SCHEMA IF NOT EXISTS tutorbridge;
SET search_path = tutorbridge, public;
CREATE TABLE accounts (
 id uuid PRIMARY KEY, role text NOT NULL CHECK(role IN ('PARENT','TUTOR','STUDENT','ADMIN')),
 status text NOT NULL CHECK(status IN ('ACTIVE','UNVERIFIED','LOCKED','DELETED')),
 email_digest text UNIQUE, created_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE auth_sessions (
 id uuid PRIMARY KEY, account_id uuid NOT NULL REFERENCES accounts, token_digest text NOT NULL UNIQUE,
 expires_at timestamptz NOT NULL, revoked_at timestamptz);
CREATE TABLE students (
 id uuid PRIMARY KEY, parent_id uuid NOT NULL REFERENCES accounts,
 display_name varchar(100) NOT NULL, grade smallint NOT NULL DEFAULT 8 CHECK(grade=8), revision bigint NOT NULL DEFAULT 1 CHECK(revision>0));
CREATE TABLE student_links (
 student_id uuid PRIMARY KEY REFERENCES students, account_id uuid NOT NULL UNIQUE REFERENCES accounts,
 status text NOT NULL CHECK(status IN ('INVITED','ACTIVE','REVOKED')), expires_at timestamptz, revision bigint NOT NULL DEFAULT 1);
CREATE TABLE tutor_profiles (
 id uuid PRIMARY KEY, tutor_id uuid NOT NULL REFERENCES accounts, version integer NOT NULL CHECK(version>0),
 status text NOT NULL CHECK(status IN ('DRAFT','PENDING','APPROVED','REJECTED','RETIRED')),
 description varchar(3000) NOT NULL CHECK(length(description)>=50), experience_months integer NOT NULL CHECK(experience_months BETWEEN 0 AND 600),
 rate_vnd_60 integer NOT NULL CHECK(rate_vnd_60>0), subject text NOT NULL DEFAULT 'MATH8_ALGEBRA' CHECK(subject='MATH8_ALGEBRA'),
 current boolean NOT NULL DEFAULT false, UNIQUE(tutor_id,version), CHECK(NOT current OR status='APPROVED'));
CREATE UNIQUE INDEX one_current_tutor_profile ON tutor_profiles(tutor_id) WHERE current;
CREATE TABLE tutor_verifications (
 id uuid PRIMARY KEY, profile_id uuid NOT NULL REFERENCES tutor_profiles, scope text NOT NULL,
 status text NOT NULL CHECK(status IN ('PENDING','VERIFIED','REJECTED')), evidence_key text NOT NULL, reviewed_by uuid REFERENCES accounts);
CREATE TABLE calendars (
 id uuid PRIMARY KEY, owner_kind text NOT NULL CHECK(owner_kind IN ('STUDENT','TUTOR')), owner_id uuid NOT NULL,
 version bigint NOT NULL CHECK(version>0), effective boolean NOT NULL DEFAULT false,
 timezone text NOT NULL DEFAULT 'Asia/Ho_Chi_Minh' CHECK(timezone='Asia/Ho_Chi_Minh'), UNIQUE(owner_kind,owner_id,version));
CREATE UNIQUE INDEX one_effective_calendar ON calendars(owner_kind,owner_id) WHERE effective;
-- Materialize weekly rules/exceptions into bounded UTC windows before matching. Never accept missing calendar data.
CREATE TABLE availability_windows (
 id uuid PRIMARY KEY, calendar_id uuid NOT NULL REFERENCES calendars, during tstzrange NOT NULL,
 kind text NOT NULL CHECK(kind IN ('AVAILABLE','PREFERRED','BLOCKED')),
 CHECK(NOT isempty(during) AND NOT lower_inf(during) AND NOT upper_inf(during) AND lower_inc(during) AND NOT upper_inc(during)));
CREATE INDEX availability_lookup ON availability_windows USING gist(during);
CREATE TABLE learning_needs (
 id uuid PRIMARY KEY, student_id uuid NOT NULL REFERENCES students, revision bigint NOT NULL DEFAULT 1 CHECK(revision>0),
 status text NOT NULL CHECK(status IN ('DRAFT','CONFIRMED','CLOSED')), created_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE need_versions (
 id uuid PRIMARY KEY, need_id uuid NOT NULL REFERENCES learning_needs, version integer NOT NULL CHECK(version>0),
 goal varchar(2000), budget_vnd_session integer CHECK(budget_vnd_session>0), duration_minutes integer CHECK(duration_minutes IN (60,90,120)),
 frequency integer CHECK(frequency BETWEEN 1 AND 3), start_date date, confirmed_at timestamptz,
 student_calendar_id uuid REFERENCES calendars, UNIQUE(need_id,version), UNIQUE(id,need_id),
 CHECK(confirmed_at IS NULL OR (goal IS NOT NULL AND length(trim(goal)) BETWEEN 20 AND 2000 AND budget_vnd_session IS NOT NULL AND duration_minutes IS NOT NULL AND frequency IS NOT NULL AND start_date IS NOT NULL AND student_calendar_id IS NOT NULL)));
ALTER TABLE learning_needs ADD COLUMN current_version_id uuid;
ALTER TABLE learning_needs ADD CONSTRAINT current_need_version FOREIGN KEY(current_version_id,id) REFERENCES need_versions(id,need_id) DEFERRABLE INITIALLY DEFERRED;
CREATE TABLE matching_runs (
 id uuid PRIMARY KEY, need_version_id uuid NOT NULL REFERENCES need_versions, mode text NOT NULL CHECK(mode IN ('S','B')),
 algorithm_version varchar(100) NOT NULL, config_version varchar(100) NOT NULL,
 status text NOT NULL CHECK(status IN ('RUNNING','COMPLETED','FAILED')), created_at timestamptz NOT NULL DEFAULT now(), expires_at timestamptz NOT NULL);
CREATE TABLE matching_candidates (
 run_id uuid NOT NULL REFERENCES matching_runs, tutor_id uuid NOT NULL REFERENCES accounts, profile_id uuid NOT NULL REFERENCES tutor_profiles,
 tutor_calendar_id uuid NOT NULL REFERENCES calendars, rank smallint NOT NULL CHECK(rank BETWEEN 1 AND 5),
 s numeric(7,6) CHECK(s BETWEEN 0 AND 1), e numeric(7,6) NOT NULL CHECK(e BETWEEN 0 AND 1), a numeric(7,6) NOT NULL CHECK(a BETWEEN 0 AND 1),
 score numeric(7,6) NOT NULL CHECK(score BETWEEN 0 AND 1), quoted_price integer NOT NULL CHECK(quoted_price>0), hard_filters_passed boolean NOT NULL CHECK(hard_filters_passed),
 PRIMARY KEY(run_id,tutor_id), UNIQUE(run_id,rank));
CREATE TABLE class_offers (
 id uuid PRIMARY KEY, need_id uuid NOT NULL REFERENCES learning_needs, need_version_id uuid NOT NULL,
 tutor_id uuid NOT NULL REFERENCES accounts, profile_id uuid NOT NULL REFERENCES tutor_profiles,
 tutor_calendar_id uuid NOT NULL REFERENCES calendars, student_calendar_id uuid NOT NULL REFERENCES calendars,
 run_id uuid NOT NULL REFERENCES matching_runs, revision bigint NOT NULL DEFAULT 1 CHECK(revision>0),
 state text NOT NULL CHECK(state IN ('PENDING','ACCEPTED','REJECTED','CANCELLED','EXPIRED')),
 goal_snapshot varchar(2000) NOT NULL CHECK(length(trim(goal_snapshot))>=20),
 duration_minutes integer NOT NULL CHECK(duration_minutes IN (60,90,120)), frequency integer NOT NULL CHECK(frequency BETWEEN 1 AND 3),
 cycle_start_date date NOT NULL, rate_vnd_60 integer NOT NULL CHECK(rate_vnd_60>0), price_vnd_session integer NOT NULL CHECK(price_vnd_session>0),
 sent_at timestamptz NOT NULL, expires_at timestamptz NOT NULL, concluded_at timestamptz, reason_code text,
 FOREIGN KEY(need_version_id,need_id) REFERENCES need_versions(id,need_id),
 CHECK(price_vnd_session=ceil(rate_vnd_60::numeric*duration_minutes/60)), CHECK(expires_at>sent_at AND expires_at<=sent_at+interval '48 hours'));
CREATE UNIQUE INDEX one_pending_offer_per_pair ON class_offers(need_id,tutor_id) WHERE state='PENDING';
CREATE TABLE offer_sessions (
 offer_id uuid NOT NULL REFERENCES class_offers, ordinal smallint NOT NULL CHECK(ordinal BETWEEN 1 AND 12), during tstzrange NOT NULL,
 PRIMARY KEY(offer_id,ordinal), CHECK(NOT isempty(during) AND NOT lower_inf(during) AND NOT upper_inf(during) AND lower_inc(during) AND NOT upper_inc(during)),
 EXCLUDE USING gist(offer_id WITH =,during WITH &&));
CREATE TABLE classes (
 id uuid PRIMARY KEY, originating_need_id uuid NOT NULL UNIQUE REFERENCES learning_needs,
 accepted_offer_id uuid NOT NULL UNIQUE REFERENCES class_offers, student_id uuid NOT NULL REFERENCES students, tutor_id uuid NOT NULL REFERENCES accounts,
 status text NOT NULL CHECK(status IN ('WAITING','ACTIVE','ENDED')), revision bigint NOT NULL DEFAULT 1 CHECK(revision>0),
 duration_minutes integer NOT NULL CHECK(duration_minutes IN (60,90,120)), price_vnd_session integer NOT NULL CHECK(price_vnd_session>0),
 ended_at timestamptz, created_at timestamptz NOT NULL DEFAULT now(), CHECK((status='ENDED')=(ended_at IS NOT NULL)));
CREATE TABLE class_goal_versions (
 id uuid PRIMARY KEY, class_id uuid NOT NULL REFERENCES classes, version integer NOT NULL CHECK(version>0),
 goal varchar(2000) NOT NULL CHECK(length(trim(goal))>=20), status text NOT NULL CHECK(status IN ('PROPOSED','EFFECTIVE','SUPERSEDED','REJECTED')),
 proposed_by uuid NOT NULL REFERENCES accounts, parent_approved_at timestamptz, tutor_approved_at timestamptz,
 UNIQUE(class_id,version), UNIQUE(id,class_id), CHECK(status<>'EFFECTIVE' OR (parent_approved_at IS NOT NULL AND tutor_approved_at IS NOT NULL)));
CREATE UNIQUE INDEX one_effective_goal ON class_goal_versions(class_id) WHERE status='EFFECTIVE';
CREATE TABLE lesson_sessions (
 id uuid PRIMARY KEY, class_id uuid NOT NULL REFERENCES classes, ordinal integer NOT NULL CHECK(ordinal>0), during tstzrange NOT NULL,
 state text NOT NULL CHECK(state IN ('SCHEDULED','COMPLETED','CANCELLED','ABSENT')), revision bigint NOT NULL DEFAULT 1 CHECK(revision>0),
 makeup_for_id uuid REFERENCES lesson_sessions, UNIQUE(class_id,ordinal), UNIQUE(id,class_id),
 CHECK(NOT isempty(during) AND NOT lower_inf(during) AND NOT upper_inf(during) AND lower_inc(during) AND NOT upper_inc(during)));
CREATE TABLE resource_bookings (
 session_id uuid NOT NULL REFERENCES lesson_sessions, resource_kind text NOT NULL CHECK(resource_kind IN ('STUDENT','TUTOR')),
 resource_id uuid NOT NULL, during tstzrange NOT NULL, active boolean NOT NULL,
 PRIMARY KEY(session_id,resource_kind), CHECK(NOT isempty(during) AND NOT lower_inf(during) AND NOT upper_inf(during) AND lower_inc(during) AND NOT upper_inc(during)),
 CONSTRAINT booking_no_overlap EXCLUDE USING gist(resource_kind WITH =,resource_id WITH =,during WITH &&) WHERE(active));
CREATE TABLE lesson_log_versions (
 id uuid PRIMARY KEY, session_id uuid NOT NULL, class_id uuid NOT NULL, version integer NOT NULL CHECK(version>0),
 professional_feedback varchar(4000) NOT NULL, tutor_confirmed_at timestamptz, parent_attendance_confirmed_at timestamptz,
 disputed boolean NOT NULL DEFAULT false, UNIQUE(session_id,version), UNIQUE(id,class_id), FOREIGN KEY(session_id,class_id) REFERENCES lesson_sessions(id,class_id));
CREATE TABLE materials (
 id uuid PRIMARY KEY, class_id uuid NOT NULL REFERENCES classes, owner_id uuid NOT NULL REFERENCES accounts, title varchar(200) NOT NULL, UNIQUE(id,class_id));
CREATE TABLE material_versions (
 id uuid PRIMARY KEY, material_id uuid NOT NULL, class_id uuid NOT NULL, version integer NOT NULL CHECK(version>0),
 object_key text NOT NULL UNIQUE, media_type text NOT NULL CHECK(media_type IN ('application/pdf','application/vnd.openxmlformats-officedocument.wordprocessingml.document')),
 size_bytes integer NOT NULL CHECK(size_bytes BETWEEN 1 AND 10485760), sha256 char(64) NOT NULL CHECK(sha256~'^[0-9a-f]{64}$'),
 revision bigint NOT NULL DEFAULT 1 CHECK(revision>0),
 state text NOT NULL CHECK(state IN ('UPLOADING','PROCESSING','REVIEW','READY','FAILED','REVOKED','DELETED')),
 pdf_pages integer CHECK(pdf_pages BETWEEN 1 AND 100), extracted_characters integer CHECK(extracted_characters BETWEEN 0 AND 200000),
 extraction_version varchar(100), teacher_reviewed_at timestamptz, access_epoch bigint NOT NULL DEFAULT 1 CHECK(access_epoch>0),
 UNIQUE(material_id,version), UNIQUE(id,class_id), FOREIGN KEY(material_id,class_id) REFERENCES materials(id,class_id),
 CHECK(state<>'READY' OR (teacher_reviewed_at IS NOT NULL AND extracted_characters>0 AND (media_type<>'application/pdf' OR pdf_pages IS NOT NULL))));
CREATE TABLE source_segments (
 id uuid PRIMARY KEY, material_version_id uuid NOT NULL, class_id uuid NOT NULL, ordinal integer NOT NULL CHECK(ordinal>0),
 locator varchar(300) NOT NULL, text_content text NOT NULL CHECK(length(text_content) BETWEEN 1 AND 200000),
 UNIQUE(material_version_id,ordinal), UNIQUE(id,material_version_id,class_id), FOREIGN KEY(material_version_id,class_id) REFERENCES material_versions(id,class_id));
CREATE TABLE material_grants (
 id uuid PRIMARY KEY, material_version_id uuid NOT NULL, class_id uuid NOT NULL,
 audience text NOT NULL CHECK(audience IN ('OWNER','CLASS_TUTOR','CLASS_MEMBERS','ACCOUNT')), account_id uuid REFERENCES accounts,
 capability text NOT NULL CHECK(capability IN ('VIEW_ORIGINAL','DOWNLOAD_ORIGINAL','USE_AI','READ_DERIVATIVE')),
 basis varchar(500) NOT NULL, granted_by uuid NOT NULL REFERENCES accounts, expires_at timestamptz, revoked_at timestamptz,
 FOREIGN KEY(material_version_id,class_id) REFERENCES material_versions(id,class_id), CHECK((audience='ACCOUNT')=(account_id IS NOT NULL)));
CREATE INDEX grants_lookup ON material_grants(material_version_id,capability) WHERE revoked_at IS NULL;
CREATE TABLE preparation_contexts (
 id uuid PRIMARY KEY, class_id uuid NOT NULL REFERENCES classes, session_id uuid NOT NULL, goal_version_id uuid NOT NULL,
 topic varchar(500) NOT NULL CHECK(length(trim(topic))>0), duration_minutes integer NOT NULL CHECK(duration_minutes IN (60,90,120)),
 config_version varchar(100) NOT NULL, created_by uuid NOT NULL REFERENCES accounts, created_at timestamptz NOT NULL DEFAULT now(),
 UNIQUE(id,class_id), FOREIGN KEY(session_id,class_id) REFERENCES lesson_sessions(id,class_id), FOREIGN KEY(goal_version_id,class_id) REFERENCES class_goal_versions(id,class_id));
CREATE TABLE context_sources (
 context_id uuid NOT NULL, material_version_id uuid NOT NULL, class_id uuid NOT NULL, source_epoch bigint NOT NULL CHECK(source_epoch>0),
 PRIMARY KEY(context_id,material_version_id), FOREIGN KEY(context_id,class_id) REFERENCES preparation_contexts(id,class_id), FOREIGN KEY(material_version_id,class_id) REFERENCES material_versions(id,class_id));
CREATE TABLE context_logs (
 context_id uuid NOT NULL, log_version_id uuid NOT NULL, class_id uuid NOT NULL,
 PRIMARY KEY(context_id,log_version_id), FOREIGN KEY(context_id,class_id) REFERENCES preparation_contexts(id,class_id), FOREIGN KEY(log_version_id,class_id) REFERENCES lesson_log_versions(id,class_id));
CREATE TABLE ai_policies (
 version varchar(100) PRIMARY KEY, enabled boolean NOT NULL DEFAULT false, provider text, model text,
 money_cap_vnd bigint CHECK(money_cap_vnd>0), class_day_limit integer NOT NULL DEFAULT 5 CHECK(class_day_limit=5),
 tutor_day_limit integer NOT NULL DEFAULT 20 CHECK(tutor_day_limit=20), global_running_limit integer NOT NULL DEFAULT 2 CHECK(global_running_limit=2),
 tutor_running_limit integer NOT NULL DEFAULT 1 CHECK(tutor_running_limit=1), queue_limit integer NOT NULL DEFAULT 20 CHECK(queue_limit=20),
 timeout_seconds integer NOT NULL DEFAULT 120 CHECK(timeout_seconds=120), CHECK(NOT enabled OR (provider IS NOT NULL AND model IS NOT NULL AND money_cap_vnd IS NOT NULL)));
INSERT INTO ai_policies(version,enabled) VALUES('UNCONFIGURED',false);
CREATE TABLE ai_jobs (
 id uuid PRIMARY KEY, class_id uuid NOT NULL REFERENCES classes, context_id uuid NOT NULL, tutor_id uuid NOT NULL REFERENCES accounts,
 policy_version varchar(100) NOT NULL REFERENCES ai_policies, state text NOT NULL CHECK(state IN ('QUEUED','RUNNING','COMPLETED','FAILED','CANCELLED','TIMED_OUT')),
 base_plan_version_id uuid, base_plan_revision bigint CHECK(base_plan_revision>0), result_plan_version_id uuid, created_at timestamptz NOT NULL DEFAULT now(), deadline_at timestamptz NOT NULL,
 fencing_token bigint NOT NULL DEFAULT 0 CHECK(fencing_token>=0), lease_until timestamptz, worker_id text, failure_code text,
 UNIQUE(id,class_id), FOREIGN KEY(context_id,class_id) REFERENCES preparation_contexts(id,class_id), CHECK(deadline_at=created_at+interval '120 seconds'), CHECK((base_plan_version_id IS NULL)=(base_plan_revision IS NULL)));
CREATE INDEX ai_queue ON ai_jobs(created_at) WHERE state='QUEUED';
CREATE TABLE ai_attempts (
 id uuid PRIMARY KEY, job_id uuid NOT NULL REFERENCES ai_jobs, attempt_number integer NOT NULL CHECK(attempt_number>0),
 fencing_token bigint NOT NULL, provider_request_id text, state text NOT NULL CHECK(state IN ('RESERVED','SENT','SUCCEEDED','FAILED','UNKNOWN')),
 estimated_cost_vnd bigint NOT NULL CHECK(estimated_cost_vnd>=0), actual_cost_vnd bigint CHECK(actual_cost_vnd>=0), UNIQUE(job_id,attempt_number));
CREATE TABLE quota_counters (
 scope text NOT NULL CHECK(scope IN ('CLASS_DAY','TUTOR_DAY')), subject_id uuid NOT NULL, local_date date NOT NULL,
 used integer NOT NULL CHECK(used>=0), PRIMARY KEY(scope,subject_id,local_date));
CREATE TABLE budget_ledgers (
 policy_version varchar(100) PRIMARY KEY REFERENCES ai_policies, reserved_vnd bigint NOT NULL DEFAULT 0 CHECK(reserved_vnd>=0),
 spent_vnd bigint NOT NULL DEFAULT 0 CHECK(spent_vnd>=0));
CREATE TABLE ai_reservations (
 attempt_id uuid PRIMARY KEY REFERENCES ai_attempts, class_id uuid NOT NULL REFERENCES classes, tutor_id uuid NOT NULL REFERENCES accounts,
 local_date date NOT NULL, policy_version varchar(100) NOT NULL REFERENCES ai_policies, reserved_vnd bigint NOT NULL CHECK(reserved_vnd>=0),
 state text NOT NULL CHECK(state IN ('HELD','SETTLED','RELEASED','UNKNOWN')));
CREATE TABLE worker_slots (
 slot_no smallint PRIMARY KEY CHECK(slot_no BETWEEN 1 AND 2), job_id uuid UNIQUE REFERENCES ai_jobs,
 tutor_id uuid UNIQUE REFERENCES accounts, fencing_token bigint NOT NULL DEFAULT 0, lease_until timestamptz,
 CHECK((job_id IS NULL)=(tutor_id IS NULL)));
INSERT INTO worker_slots(slot_no) VALUES(1),(2);
CREATE TABLE lesson_plans (
 id uuid PRIMARY KEY, class_id uuid NOT NULL REFERENCES classes, session_id uuid NOT NULL, revision bigint NOT NULL DEFAULT 1 CHECK(revision>0),
 UNIQUE(session_id), UNIQUE(id,class_id), FOREIGN KEY(session_id,class_id) REFERENCES lesson_sessions(id,class_id));
CREATE TABLE plan_versions (
 id uuid PRIMARY KEY, plan_id uuid NOT NULL, class_id uuid NOT NULL, version integer NOT NULL CHECK(version>0),
 context_id uuid, origin text NOT NULL CHECK(origin IN ('AI','MANUAL','MIXED')), status text NOT NULL CHECK(status IN ('DRAFT','APPROVED','SUPERSEDED')),
 revision bigint NOT NULL DEFAULT 1 CHECK(revision>0), title varchar(200) NOT NULL, goal varchar(2000) NOT NULL CHECK(length(trim(goal))>0), homework text NOT NULL DEFAULT '',
 warnings_resolved boolean NOT NULL DEFAULT false, created_by uuid NOT NULL REFERENCES accounts, created_at timestamptz NOT NULL DEFAULT now(),
 approved_by uuid REFERENCES accounts, approved_at timestamptz, UNIQUE(plan_id,version), UNIQUE(id,class_id), UNIQUE(id,plan_id,class_id),
 FOREIGN KEY(plan_id,class_id) REFERENCES lesson_plans(id,class_id), FOREIGN KEY(context_id,class_id) REFERENCES preparation_contexts(id,class_id),
 CHECK(status<>'APPROVED' OR (approved_by IS NOT NULL AND approved_at IS NOT NULL AND warnings_resolved)));
ALTER TABLE ai_jobs ADD CONSTRAINT ai_base_plan_same_class FOREIGN KEY(base_plan_version_id,class_id) REFERENCES plan_versions(id,class_id);
ALTER TABLE ai_jobs ADD CONSTRAINT ai_result_plan_same_class FOREIGN KEY(result_plan_version_id,class_id) REFERENCES plan_versions(id,class_id);
CREATE TABLE plan_activities (
 id uuid PRIMARY KEY, plan_version_id uuid NOT NULL, class_id uuid NOT NULL, ordinal integer NOT NULL CHECK(ordinal>0),
 kind text NOT NULL CHECK(kind IN ('REVIEW','DISCOVERY','PRACTICE','APPLICATION','SUMMARY')), content text NOT NULL,
 minutes integer NOT NULL CHECK(minutes>=0), UNIQUE(plan_version_id,ordinal), UNIQUE(id,plan_version_id,class_id),
 FOREIGN KEY(plan_version_id,class_id) REFERENCES plan_versions(id,class_id));
CREATE TABLE plan_dependencies (
 plan_version_id uuid NOT NULL, material_version_id uuid NOT NULL, class_id uuid NOT NULL, source_epoch bigint NOT NULL CHECK(source_epoch>0),
 PRIMARY KEY(plan_version_id,material_version_id), FOREIGN KEY(plan_version_id,class_id) REFERENCES plan_versions(id,class_id), FOREIGN KEY(material_version_id,class_id) REFERENCES material_versions(id,class_id));
CREATE TABLE plan_citations (
 id uuid PRIMARY KEY, plan_version_id uuid NOT NULL, material_version_id uuid NOT NULL, class_id uuid NOT NULL, segment_id uuid NOT NULL,
 activity_id uuid, cited_excerpt varchar(2000) NOT NULL,
 FOREIGN KEY(plan_version_id,material_version_id) REFERENCES plan_dependencies(plan_version_id,material_version_id),
 FOREIGN KEY(segment_id,material_version_id,class_id) REFERENCES source_segments(id,material_version_id,class_id),
 FOREIGN KEY(activity_id,plan_version_id,class_id) REFERENCES plan_activities(id,plan_version_id,class_id));
CREATE TABLE private_plan_answers (
 plan_version_id uuid PRIMARY KEY, class_id uuid NOT NULL, answer_text text NOT NULL, UNIQUE(plan_version_id,class_id),
 FOREIGN KEY(plan_version_id,class_id) REFERENCES plan_versions(id,class_id));
CREATE TABLE plan_publications (
 plan_id uuid PRIMARY KEY, class_id uuid NOT NULL, plan_version_id uuid NOT NULL, revision bigint NOT NULL DEFAULT 1 CHECK(revision>0),
 published_by uuid NOT NULL REFERENCES accounts, published_at timestamptz NOT NULL,
 FOREIGN KEY(plan_version_id,plan_id,class_id) REFERENCES plan_versions(id,plan_id,class_id));
CREATE TABLE answer_releases (
 plan_version_id uuid NOT NULL, class_id uuid NOT NULL, released_by uuid NOT NULL REFERENCES accounts, released_at timestamptz NOT NULL,
 homework_closed_at timestamptz NOT NULL, PRIMARY KEY(plan_version_id), FOREIGN KEY(plan_version_id,class_id) REFERENCES private_plan_answers(plan_version_id,class_id),
 CHECK(released_at>=homework_closed_at));
CREATE TABLE command_receipts (
 id uuid PRIMARY KEY, actor_id uuid NOT NULL REFERENCES accounts, command_type varchar(100) NOT NULL,
 idempotency_key varchar(128) NOT NULL CHECK(length(idempotency_key)>=16), payload_sha256 char(64) NOT NULL CHECK(payload_sha256~'^[0-9a-f]{64}$'),
 state text NOT NULL CHECK(state IN ('PROCESSING','SUCCEEDED','FAILED')), result_type varchar(100), result_id uuid, error_code text,
 created_at timestamptz NOT NULL DEFAULT now(), expires_at timestamptz NOT NULL,
 UNIQUE(actor_id,command_type,idempotency_key), CHECK(expires_at>=created_at+interval '7 days'));
CREATE TABLE outbox_events (
 id uuid PRIMARY KEY, aggregate_type varchar(100) NOT NULL, aggregate_id uuid NOT NULL, event_type varchar(100) NOT NULL,
 payload jsonb NOT NULL CHECK(jsonb_typeof(payload)='object'), dedupe_key text NOT NULL UNIQUE,
 created_at timestamptz NOT NULL DEFAULT now(), available_at timestamptz NOT NULL DEFAULT now(), delivered_at timestamptz,
 attempts integer NOT NULL DEFAULT 0 CHECK(attempts>=0), lease_until timestamptz);
CREATE INDEX outbox_due ON outbox_events(available_at) WHERE delivered_at IS NULL;
CREATE TABLE notifications (
 id uuid PRIMARY KEY, recipient_id uuid NOT NULL REFERENCES accounts, event_id uuid NOT NULL REFERENCES outbox_events,
 template_code varchar(100) NOT NULL, target_type varchar(100), target_id uuid, read_at timestamptz, UNIQUE(recipient_id,event_id));
CREATE TABLE audit_events (
 id uuid PRIMARY KEY, actor_id uuid REFERENCES accounts, command_id uuid REFERENCES command_receipts,
 event_type varchar(100) NOT NULL, object_type varchar(100) NOT NULL, object_id uuid NOT NULL,
 outcome varchar(100) NOT NULL, trace_id varchar(100) NOT NULL, created_at timestamptz NOT NULL DEFAULT now());
CREATE INDEX audit_object_time ON audit_events(object_type,object_id,created_at);
CREATE TABLE revocation_ledger (
 id uuid PRIMARY KEY, material_version_id uuid NOT NULL REFERENCES material_versions, new_epoch bigint NOT NULL,
 reason_code varchar(100) NOT NULL, revoked_by uuid NOT NULL REFERENCES accounts, revoked_at timestamptz NOT NULL DEFAULT now(),
 UNIQUE(material_version_id,new_epoch));
CREATE TABLE deletion_ledger (
 id uuid PRIMARY KEY, subject_type varchar(100) NOT NULL, subject_digest char(64) NOT NULL,
 requested_at timestamptz NOT NULL, active_due_at timestamptz NOT NULL, completed_at timestamptz,
 CHECK(active_due_at<=requested_at+interval '7 days'));
REVOKE ALL ON SCHEMA tutorbridge FROM PUBLIC;
REVOKE ALL ON ALL TABLES IN SCHEMA tutorbridge FROM PUBLIC;
COMMIT;
