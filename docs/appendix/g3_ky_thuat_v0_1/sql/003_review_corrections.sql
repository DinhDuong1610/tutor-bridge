-- TB G3 04 v0.1 / corrective migration for TB G3 03 v0.1.
-- Apply after baseline/001 and baseline/002. Changes are explicit; baseline bytes retained.
BEGIN;
SET search_path=tutorbridge,public;
-- R01: SQL CHECK treats NULL as passing. READY must have an explicit non-null text count.
ALTER TABLE material_versions ADD CONSTRAINT ready_requires_text_count
 CHECK(state<>'READY' OR (extracted_characters IS NOT NULL AND extracted_characters>0));
-- R02: moving an existing session between classes leaves its resource bookings inconsistent.
CREATE FUNCTION protect_session_identity() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF (NEW.id,NEW.class_id) IS DISTINCT FROM (OLD.id,OLD.class_id) THEN
  RAISE EXCEPTION 'session identity and class are immutable' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER immutable_session_identity BEFORE UPDATE ON lesson_sessions FOR EACH ROW EXECUTE FUNCTION protect_session_identity();
-- R03: the original limit was enforced on INSERT only, allowing a source to be moved into a full context.
DROP TRIGGER context_source_limit ON context_sources;
CREATE TRIGGER context_source_limit AFTER INSERT OR UPDATE OF context_id ON context_sources
 FOR EACH ROW EXECUTE FUNCTION limit_context_sources();
REVOKE ALL ON ALL FUNCTIONS IN SCHEMA tutorbridge FROM PUBLIC;
COMMIT;
