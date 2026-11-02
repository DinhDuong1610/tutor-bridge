BEGIN;
SET search_path=tutorbridge,public;
-- A schedule row owns both bookings. Application roles must have no direct booking DML.
CREATE FUNCTION sync_session_bookings() RETURNS trigger LANGUAGE plpgsql SET search_path=tutorbridge,pg_temp AS $$
DECLARE c classes;
BEGIN
 SELECT * INTO STRICT c FROM classes WHERE id=NEW.class_id;
 IF extract(epoch FROM (upper(NEW.during)-lower(NEW.during)))/60<>c.duration_minutes THEN
  RAISE EXCEPTION 'session duration mismatch' USING ERRCODE='23514';
 END IF;
 INSERT INTO resource_bookings(session_id,resource_kind,resource_id,during,active)
 VALUES(NEW.id,'STUDENT',c.student_id,NEW.during,NEW.state<>'CANCELLED'),
       (NEW.id,'TUTOR',c.tutor_id,NEW.during,NEW.state<>'CANCELLED')
 ON CONFLICT(session_id,resource_kind) DO UPDATE SET during=EXCLUDED.during,active=EXCLUDED.active;
 RETURN NEW;
END $$;
CREATE TRIGGER sync_bookings AFTER INSERT OR UPDATE OF during,state ON lesson_sessions FOR EACH ROW EXECUTE FUNCTION sync_session_bookings();
CREATE FUNCTION protect_class_parties() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF (NEW.student_id,NEW.tutor_id,NEW.originating_need_id,NEW.accepted_offer_id,NEW.duration_minutes,NEW.price_vnd_session)
 IS DISTINCT FROM (OLD.student_id,OLD.tutor_id,OLD.originating_need_id,OLD.accepted_offer_id,OLD.duration_minutes,OLD.price_vnd_session) THEN
  RAISE EXCEPTION 'class agreement is immutable' USING ERRCODE='23514';
 END IF; RETURN NEW;
END $$;
CREATE TRIGGER protect_parties BEFORE UPDATE ON classes FOR EACH ROW EXECUTE FUNCTION protect_class_parties();
CREATE FUNCTION validate_offer_schedule() RETURNS trigger LANGUAGE plpgsql SET search_path=tutorbridge,pg_temp AS $$
DECLARE target uuid; o class_offers; cnt integer; b integer; d date;
BEGIN
 IF TG_TABLE_NAME='class_offers' THEN target=COALESCE(NEW.id,OLD.id); ELSE target=COALESCE(NEW.offer_id,OLD.offer_id); END IF;
 SELECT * INTO o FROM class_offers WHERE id=target;
 IF NOT FOUND THEN RETURN NULL; END IF;
 SELECT count(*) INTO cnt FROM offer_sessions WHERE offer_id=target;
 IF cnt<>4*o.frequency THEN RAISE EXCEPTION 'offer needs 4 blocks of sessions' USING ERRCODE='23514'; END IF;
 IF EXISTS(SELECT 1 FROM offer_sessions WHERE offer_id=target AND
  (lower(during)<=o.sent_at OR extract(epoch FROM (upper(during)-lower(during)))/60<>o.duration_minutes OR
  (lower(during) AT TIME ZONE 'Asia/Ho_Chi_Minh')::date<o.cycle_start_date OR
  (lower(during) AT TIME ZONE 'Asia/Ho_Chi_Minh')::date>=o.cycle_start_date+28)) THEN
  RAISE EXCEPTION 'invalid offer time/duration' USING ERRCODE='23514';
 END IF;
 FOR b IN 0..3 LOOP
  d=o.cycle_start_date+b*7;
  SELECT count(*) INTO cnt FROM offer_sessions WHERE offer_id=target AND
   (lower(during) AT TIME ZONE 'Asia/Ho_Chi_Minh')::date>=d AND (lower(during) AT TIME ZONE 'Asia/Ho_Chi_Minh')::date<d+7;
  IF cnt<>o.frequency THEN RAISE EXCEPTION 'frequency mismatch in block' USING ERRCODE='23514'; END IF;
 END LOOP;
 IF o.expires_at<>LEAST(o.sent_at+interval '48 hours',(SELECT min(lower(during)) FROM offer_sessions WHERE offer_id=target)) THEN
  RAISE EXCEPTION 'invalid offer expiry' USING ERRCODE='23514'; END IF;
 RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER offer_complete AFTER INSERT OR UPDATE ON class_offers DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION validate_offer_schedule();
CREATE CONSTRAINT TRIGGER offer_sessions_complete AFTER INSERT OR UPDATE OR DELETE ON offer_sessions DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION validate_offer_schedule();
CREATE FUNCTION forbid_approved_mutation() RETURNS trigger LANGUAGE plpgsql SET search_path=tutorbridge,pg_temp AS $$
DECLARE target uuid; st text;
BEGIN
 IF TG_TABLE_NAME='plan_versions' THEN
  IF OLD.status='APPROVED' THEN RAISE EXCEPTION 'approved version is immutable' USING ERRCODE='23514'; END IF;
 ELSE
  IF TG_OP='DELETE' THEN target=OLD.plan_version_id; ELSE target=NEW.plan_version_id; END IF;
  SELECT status INTO st FROM plan_versions WHERE id=target FOR UPDATE;
  IF st='APPROVED' THEN RAISE EXCEPTION 'approved content is immutable' USING ERRCODE='23514'; END IF;
  IF TG_OP='UPDATE' AND OLD.plan_version_id<>NEW.plan_version_id THEN RAISE EXCEPTION 'cannot move content' USING ERRCODE='23514'; END IF;
 END IF;
 IF TG_OP='DELETE' THEN RETURN OLD; END IF; RETURN NEW;
END $$;
CREATE TRIGGER immutable_approved_plan BEFORE UPDATE OR DELETE ON plan_versions FOR EACH ROW EXECUTE FUNCTION forbid_approved_mutation();
CREATE TRIGGER immutable_activities BEFORE INSERT OR UPDATE OR DELETE ON plan_activities FOR EACH ROW EXECUTE FUNCTION forbid_approved_mutation();
CREATE TRIGGER immutable_dependencies BEFORE INSERT OR UPDATE OR DELETE ON plan_dependencies FOR EACH ROW EXECUTE FUNCTION forbid_approved_mutation();
CREATE TRIGGER immutable_citations BEFORE INSERT OR UPDATE OR DELETE ON plan_citations FOR EACH ROW EXECUTE FUNCTION forbid_approved_mutation();
CREATE TRIGGER immutable_answers BEFORE INSERT OR UPDATE OR DELETE ON private_plan_answers FOR EACH ROW EXECUTE FUNCTION forbid_approved_mutation();
CREATE FUNCTION validate_plan_approval() RETURNS trigger LANGUAGE plpgsql SET search_path=tutorbridge,pg_temp AS $$
DECLARE expected integer; total integer;
BEGIN
 IF NEW.status<>'APPROVED' THEN RETURN NEW; END IF;
 SELECT c.duration_minutes INTO expected FROM classes c WHERE c.id=NEW.class_id;
 SELECT sum(minutes) INTO total FROM plan_activities WHERE plan_version_id=NEW.id;
 IF total IS DISTINCT FROM expected THEN RAISE EXCEPTION 'plan duration mismatch' USING ERRCODE='23514'; END IF;
 IF NOT NEW.warnings_resolved OR NEW.approved_by IS NULL OR NEW.approved_at IS NULL THEN RAISE EXCEPTION 'review required' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER plan_review BEFORE INSERT OR UPDATE OF status ON plan_versions FOR EACH ROW EXECUTE FUNCTION validate_plan_approval();
-- Serializes publication with revocation. API also reauthorizes the audience on every read.
CREATE FUNCTION validate_publication() RETURNS trigger LANGUAGE plpgsql SET search_path=tutorbridge,pg_temp AS $$
DECLARE v plan_versions; dep record;
BEGIN
 SELECT * INTO STRICT v FROM plan_versions WHERE id=NEW.plan_version_id AND plan_id=NEW.plan_id AND class_id=NEW.class_id;
 IF v.status<>'APPROVED' OR NOT EXISTS(SELECT 1 FROM classes WHERE id=NEW.class_id AND status IN ('WAITING','ACTIVE')) THEN
  RAISE EXCEPTION 'publication not allowed' USING ERRCODE='23514'; END IF;
 FOR dep IN SELECT m.* ,d.source_epoch FROM plan_dependencies d JOIN material_versions m ON m.id=d.material_version_id
 WHERE d.plan_version_id=v.id ORDER BY m.id FOR UPDATE OF m LOOP
  IF dep.state<>'READY' OR dep.access_epoch<>dep.source_epoch OR NOT EXISTS(
   SELECT 1 FROM material_grants g WHERE g.material_version_id=dep.id AND g.capability='READ_DERIVATIVE' AND g.audience='CLASS_MEMBERS'
    AND g.revoked_at IS NULL AND (g.expires_at IS NULL OR g.expires_at>clock_timestamp())) THEN
   RAISE EXCEPTION 'source rights changed' USING ERRCODE='23514'; END IF;
 END LOOP;
 RETURN NEW;
END $$;
CREATE TRIGGER publication_guard BEFORE INSERT OR UPDATE ON plan_publications FOR EACH ROW EXECUTE FUNCTION validate_publication();
CREATE FUNCTION limit_active_materials() RETURNS trigger LANGUAGE plpgsql SET search_path=tutorbridge,pg_temp AS $$
BEGIN
 PERFORM 1 FROM classes WHERE id=NEW.class_id FOR UPDATE;
 IF (SELECT count(*) FROM material_versions WHERE class_id=NEW.class_id AND state IN ('UPLOADING','PROCESSING','REVIEW','READY'))>20 THEN
  RAISE EXCEPTION 'active material version limit' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER materials_limit AFTER INSERT OR UPDATE OF state ON material_versions FOR EACH ROW EXECUTE FUNCTION limit_active_materials();
CREATE FUNCTION limit_context_sources() RETURNS trigger LANGUAGE plpgsql SET search_path=tutorbridge,pg_temp AS $$
BEGIN
 PERFORM 1 FROM preparation_contexts WHERE id=NEW.context_id FOR UPDATE;
 IF (SELECT count(*) FROM context_sources WHERE context_id=NEW.context_id)>5 THEN RAISE EXCEPTION 'source limit' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER context_source_limit AFTER INSERT ON context_sources FOR EACH ROW EXECUTE FUNCTION limit_context_sources();
-- No public execution of service-internal helper functions. Configure least-privilege roles in deployment.
REVOKE ALL ON ALL FUNCTIONS IN SCHEMA tutorbridge FROM PUBLIC;
COMMIT;
-- Additional immutable term guards.
BEGIN;
SET search_path=tutorbridge,public;
CREATE FUNCTION protect_offer_terms() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF (to_jsonb(NEW)-ARRAY['revision','state','concluded_at','reason_code']) IS DISTINCT FROM
    (to_jsonb(OLD)-ARRAY['revision','state','concluded_at','reason_code']) THEN
  RAISE EXCEPTION 'offer terms immutable; send a new proposal' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER immutable_offer_terms BEFORE UPDATE ON class_offers FOR EACH ROW EXECUTE FUNCTION protect_offer_terms();
CREATE FUNCTION protect_offer_slots() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'offer slots immutable; send a new proposal' USING ERRCODE='23514'; END $$;
CREATE TRIGGER immutable_offer_slots BEFORE UPDATE OR DELETE ON offer_sessions FOR EACH ROW EXECUTE FUNCTION protect_offer_slots();
REVOKE ALL ON ALL FUNCTIONS IN SCHEMA tutorbridge FROM PUBLIC;
COMMIT;
