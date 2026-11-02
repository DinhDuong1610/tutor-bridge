-- TutorBridge / ADR-0006 / CR-009. Flyway V6 after V1-V5, which stay unchanged.
-- Requires PostgreSQL 17 with the pgvector extension available.
BEGIN;
CREATE EXTENSION IF NOT EXISTS vector;
SET search_path = tutorbridge, public;

-- Hard limits become configuration. Column defaults keep the R0 values from NFR13-14.
ALTER TABLE ai_policies
 DROP CONSTRAINT ai_policies_class_day_limit_check,
 DROP CONSTRAINT ai_policies_tutor_day_limit_check,
 DROP CONSTRAINT ai_policies_global_running_limit_check,
 DROP CONSTRAINT ai_policies_tutor_running_limit_check,
 DROP CONSTRAINT ai_policies_queue_limit_check,
 DROP CONSTRAINT ai_policies_timeout_seconds_check,
 ADD CONSTRAINT ai_policies_limits_positive CHECK(class_day_limit>0 AND tutor_day_limit>0 AND global_running_limit>0
  AND tutor_running_limit>0 AND queue_limit>0 AND timeout_seconds>0);
ALTER TABLE worker_slots DROP CONSTRAINT worker_slots_slot_no_check, ADD CONSTRAINT worker_slots_slot_no_positive CHECK(slot_no>0);
DO $$
DECLARE c record;
BEGIN
 FOR c IN SELECT conname FROM pg_constraint WHERE conrelid='tutorbridge.ai_jobs'::regclass AND contype='c'
  AND pg_get_constraintdef(oid) LIKE '%deadline_at%120%' LOOP
  EXECUTE format('ALTER TABLE tutorbridge.ai_jobs DROP CONSTRAINT %I',c.conname);
 END LOOP;
END $$;
ALTER TABLE ai_jobs ADD CONSTRAINT ai_jobs_deadline_after_created CHECK(deadline_at>created_at);

-- Market segment is product scope enforced by services, not data correctness.
ALTER TABLE students DROP CONSTRAINT students_grade_check, ADD CONSTRAINT students_grade_range CHECK(grade BETWEEN 1 AND 12);
ALTER TABLE tutor_profiles DROP CONSTRAINT tutor_profiles_subject_check, ADD CONSTRAINT tutor_profiles_subject_format CHECK(subject~'^[A-Z0-9_]{1,64}$');

-- Receipts and outbox rows are short-lived; business rows keep the id as a plain reference.
DO $$
DECLARE c record;
BEGIN
 FOR c IN SELECT conrelid::regclass AS tbl,conname FROM pg_constraint WHERE contype='f'
  AND confrelid IN ('tutorbridge.command_receipts'::regclass,'tutorbridge.outbox_events'::regclass) LOOP
  EXECUTE format('ALTER TABLE %s DROP CONSTRAINT %I',c.tbl,c.conname);
 END LOOP;
END $$;
CREATE INDEX command_receipts_expiry ON command_receipts(expires_at);
CREATE INDEX outbox_delivered ON outbox_events(delivered_at) WHERE delivered_at IS NOT NULL;

-- BR03 ranks a hard-filtered candidate set with exact cosine, so no ANN index is needed yet.
CREATE TABLE embedding_configs (
 version varchar(100) PRIMARY KEY, model text NOT NULL, dimensions integer NOT NULL CHECK(dimensions>0),
 active boolean NOT NULL DEFAULT false, created_at timestamptz NOT NULL DEFAULT now());
CREATE UNIQUE INDEX one_active_embedding_config ON embedding_configs(active) WHERE active;
CREATE TABLE tutor_profile_embeddings (
 profile_id uuid NOT NULL REFERENCES tutor_profiles, config_version varchar(100) NOT NULL REFERENCES embedding_configs,
 content_sha256 char(64) NOT NULL CHECK(content_sha256~'^[0-9a-f]{64}$'), embedding vector NOT NULL,
 created_at timestamptz NOT NULL DEFAULT now(), PRIMARY KEY(profile_id,config_version));
CREATE TABLE need_version_embeddings (
 need_version_id uuid NOT NULL REFERENCES need_versions, config_version varchar(100) NOT NULL REFERENCES embedding_configs,
 content_sha256 char(64) NOT NULL CHECK(content_sha256~'^[0-9a-f]{64}$'), embedding vector NOT NULL,
 created_at timestamptz NOT NULL DEFAULT now(), PRIMARY KEY(need_version_id,config_version));
CREATE FUNCTION check_embedding_dimensions() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE expected integer;
BEGIN
 SELECT dimensions INTO expected FROM tutorbridge.embedding_configs WHERE version=NEW.config_version;
 IF vector_dims(NEW.embedding) IS DISTINCT FROM expected THEN
  RAISE EXCEPTION 'embedding dimensions differ from config' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER tutor_embedding_dimensions BEFORE INSERT OR UPDATE ON tutor_profile_embeddings
 FOR EACH ROW EXECUTE FUNCTION check_embedding_dimensions();
CREATE TRIGGER need_embedding_dimensions BEFORE INSERT OR UPDATE ON need_version_embeddings
 FOR EACH ROW EXECUTE FUNCTION check_embedding_dimensions();
REVOKE ALL ON embedding_configs,tutor_profile_embeddings,need_version_embeddings FROM PUBLIC;
COMMIT;
