-- TB G3 07 v0.1. Candidate additive migration; apply only after 001..004.
-- Does not create service accounts, implement HTTP authorization or claim production readiness.
BEGIN;
SET search_path=tutorbridge,public;
ALTER TABLE accounts ADD COLUMN display_name varchar(100);
ALTER TABLE accounts ADD COLUMN email_ciphertext bytea;
ALTER TABLE accounts ADD COLUMN auth_epoch bigint NOT NULL DEFAULT 1 CHECK(auth_epoch>0);
ALTER TABLE accounts ADD COLUMN email_verified_at timestamptz;
ALTER TABLE accounts ADD COLUMN revision bigint NOT NULL DEFAULT 1 CHECK(revision>0);
ALTER TABLE auth_sessions ADD COLUMN issued_epoch bigint NOT NULL DEFAULT 1 CHECK(issued_epoch>0);
ALTER TABLE auth_sessions ADD COLUMN created_at timestamptz NOT NULL DEFAULT now();
ALTER TABLE auth_sessions ADD COLUMN last_seen_at timestamptz NOT NULL DEFAULT now();
ALTER TABLE auth_sessions ADD COLUMN admin_mfa_verified_at timestamptz;
-- Existing expires_at is never extended by this migration. Service also enforces idle and absolute limits.
ALTER TABLE students ADD COLUMN short_goal varchar(2000) NOT NULL DEFAULT '';
ALTER TABLE tutor_profiles ADD COLUMN revision bigint NOT NULL DEFAULT 1 CHECK(revision>0);
ALTER TABLE tutor_verifications ADD COLUMN reason varchar(2000);
ALTER TABLE tutor_verifications ADD COLUMN reviewed_at timestamptz;
ALTER TABLE tutor_verifications ADD COLUMN assigned_to uuid REFERENCES accounts;
ALTER TABLE tutor_verifications ADD COLUMN revision bigint NOT NULL DEFAULT 1 CHECK(revision>0);
CREATE TABLE account_credentials (
 account_id uuid PRIMARY KEY REFERENCES accounts, password_hash text NOT NULL CHECK(length(password_hash)>=20),
 hash_policy varchar(100) NOT NULL, changed_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE tutor_evidence_uploads (
 id uuid PRIMARY KEY, owner_id uuid NOT NULL REFERENCES accounts, profile_id uuid REFERENCES tutor_profiles,
 object_key text NOT NULL UNIQUE, media_type text NOT NULL CHECK(media_type IN ('application/pdf','image/jpeg','image/png')),
 size_bytes integer NOT NULL CHECK(size_bytes BETWEEN 1 AND 5242880), sha256 char(64) NOT NULL CHECK(sha256~'^[0-9a-f]{64}$'),
 state text NOT NULL CHECK(state IN ('UPLOADING','QUARANTINED','READY','FAILED','DELETED')),
 expires_at timestamptz NOT NULL, created_at timestamptz NOT NULL DEFAULT now(),
 CHECK(expires_at>created_at));
CREATE TABLE auth_challenges (
 id uuid PRIMARY KEY, account_id uuid NOT NULL REFERENCES accounts,
 purpose text NOT NULL CHECK(purpose IN ('VERIFY_EMAIL','RESET_PASSWORD','REAUTH','ADMIN_MFA')),
 token_digest char(64) NOT NULL UNIQUE CHECK(token_digest~'^[0-9a-f]{64}$'),
 created_at timestamptz NOT NULL DEFAULT now(), expires_at timestamptz NOT NULL, consumed_at timestamptz,
 CHECK(expires_at>created_at),
 CHECK(expires_at<=created_at+CASE WHEN purpose='VERIFY_EMAIL' THEN interval '30 minutes' ELSE interval '15 minutes' END),
 CHECK(consumed_at IS NULL OR (consumed_at>=created_at AND consumed_at<expires_at)));
CREATE TABLE admin_assignments (
 account_id uuid NOT NULL REFERENCES accounts, capability text NOT NULL CHECK(capability IN ('VERIFY','SUPPORT','PRIVACY','AUDIT','MANAGE_SUPPORT','CONFIGURE_AI')),
 granted_by uuid NOT NULL REFERENCES accounts, granted_at timestamptz NOT NULL DEFAULT now(), revoked_at timestamptz,
 PRIMARY KEY(account_id,capability));
-- MFA enrollment belongs in a secret store; this table retains only an opaque handle.
CREATE TABLE admin_mfa_enrollments (
 account_id uuid PRIMARY KEY REFERENCES accounts, secret_handle text NOT NULL UNIQUE,
 enrolled_at timestamptz NOT NULL, revoked_at timestamptz);
CREATE TABLE student_invitations (
 id uuid PRIMARY KEY, student_id uuid NOT NULL REFERENCES students, parent_id uuid NOT NULL REFERENCES accounts,
 target_email_digest text NOT NULL, token_digest char(64) NOT NULL UNIQUE CHECK(token_digest~'^[0-9a-f]{64}$'),
 created_at timestamptz NOT NULL DEFAULT now(), expires_at timestamptz NOT NULL,
 state text NOT NULL CHECK(state IN ('PENDING','ACCEPTED','REVOKED','EXPIRED')),
 accepted_by uuid REFERENCES accounts, accepted_at timestamptz, revision bigint NOT NULL DEFAULT 1 CHECK(revision>0),
 CHECK(expires_at>created_at AND expires_at<=created_at+interval '24 hours'),
 CHECK((state='ACCEPTED')=(accepted_by IS NOT NULL AND accepted_at IS NOT NULL)),
 CHECK(accepted_at IS NULL OR (accepted_at>=created_at AND accepted_at<expires_at)));
CREATE UNIQUE INDEX one_pending_student_invitation ON student_invitations(student_id) WHERE state='PENDING';
CREATE TABLE student_link_events (
 id uuid PRIMARY KEY, student_id uuid NOT NULL REFERENCES students, account_id uuid NOT NULL REFERENCES accounts,
 actor_id uuid NOT NULL REFERENCES accounts, event_kind text NOT NULL CHECK(event_kind IN ('LINKED','REVOKED')),
 link_revision bigint NOT NULL CHECK(link_revision>0), occurred_at timestamptz NOT NULL DEFAULT now(),
 UNIQUE(student_id,link_revision));
CREATE TABLE support_cases (
 id uuid PRIMARY KEY, reporter_id uuid NOT NULL REFERENCES accounts, class_id uuid REFERENCES classes,
 category varchar(100) NOT NULL, description varchar(2000) NOT NULL CHECK(length(trim(description))>0),
 state text NOT NULL DEFAULT 'NEW' CHECK(state IN ('NEW','IN_PROGRESS','WAITING_INFO','RESOLVED','CLOSED')),
 assignee_id uuid REFERENCES accounts, resolution varchar(2000), revision bigint NOT NULL DEFAULT 1 CHECK(revision>0),
 created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now(),
 CHECK(state='NEW' OR assignee_id IS NOT NULL),
 CHECK(state NOT IN ('RESOLVED','CLOSED') OR (resolution IS NOT NULL AND length(trim(resolution))>0)));
CREATE TABLE support_messages (
 id uuid PRIMARY KEY, case_id uuid NOT NULL REFERENCES support_cases, author_id uuid NOT NULL REFERENCES accounts,
 audience text NOT NULL CHECK(audience IN ('REPORTER_AND_SUPPORT','INTERNAL_SUPPORT')),
 body varchar(2000) NOT NULL CHECK(length(trim(body))>0), created_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE support_case_events (
 id uuid PRIMARY KEY, case_id uuid NOT NULL REFERENCES support_cases, actor_id uuid NOT NULL REFERENCES accounts,
 from_state text NOT NULL, to_state text NOT NULL, reason varchar(2000) NOT NULL,
 occurred_at timestamptz NOT NULL DEFAULT now(), revision bigint NOT NULL CHECK(revision>0), UNIQUE(case_id,revision));
CREATE TABLE support_grant_requests (
 id uuid PRIMARY KEY, case_id uuid NOT NULL REFERENCES support_cases, requester_id uuid NOT NULL REFERENCES accounts,
 material_version_id uuid NOT NULL REFERENCES material_versions, purpose varchar(500) NOT NULL CHECK(length(trim(purpose))>0),
 requested_at timestamptz NOT NULL DEFAULT now(), requested_until timestamptz NOT NULL,
 state text NOT NULL CHECK(state IN ('PENDING','APPROVED','REJECTED','CANCELLED')),
 revision bigint NOT NULL DEFAULT 1 CHECK(revision>0),
 CHECK(requested_until>requested_at AND requested_until<=requested_at+interval '24 hours'));
-- R0 conservative delegation: source owner grants VIEW_ORIGINAL only. Wider grants require a reviewed change.
CREATE TABLE support_access_grants (
 id uuid PRIMARY KEY, request_id uuid NOT NULL UNIQUE REFERENCES support_grant_requests,
 grantee_id uuid NOT NULL REFERENCES accounts, approved_by uuid NOT NULL REFERENCES accounts,
 capability text NOT NULL CHECK(capability='VIEW_ORIGINAL'), granted_at timestamptz NOT NULL DEFAULT now(),
 expires_at timestamptz NOT NULL, revoked_at timestamptz, revision bigint NOT NULL DEFAULT 1 CHECK(revision>0),
 CHECK(approved_by<>grantee_id), CHECK(expires_at>granted_at AND expires_at<=granted_at+interval '24 hours'),
 CHECK(revoked_at IS NULL OR revoked_at>=granted_at));
CREATE TABLE privacy_requests (
 id uuid PRIMARY KEY, requester_id uuid NOT NULL REFERENCES accounts,
 scope text NOT NULL CHECK(scope IN ('ACCOUNT','STUDENT','MATERIAL_VERSION')),
 subject_id uuid NOT NULL, state text NOT NULL CHECK(state IN ('BLOCKED_PENDING_PURGE','PURGING','PARTIAL_FAILURE','ACTIVE_STORES_PURGED','CLOSED')),
 requested_at timestamptz NOT NULL DEFAULT now(), blocked_at timestamptz NOT NULL, active_due_at timestamptz NOT NULL,
 deletion_ledger_id uuid NOT NULL UNIQUE REFERENCES deletion_ledger,
 revision bigint NOT NULL DEFAULT 1 CHECK(revision>0), backup_expires_at timestamptz, closed_at timestamptz,
 CHECK(blocked_at>=requested_at AND active_due_at>=requested_at AND active_due_at<=requested_at+interval '7 days'));
CREATE TABLE privacy_store_tasks (
 request_id uuid NOT NULL REFERENCES privacy_requests,
 store text NOT NULL CHECK(store IN ('DB_CONTENT','OBJECTS_AND_VERSIONS','SEGMENTS','RETRIEVAL_INDEX','CACHE','DERIVATIVES')),
 state text NOT NULL CHECK(state IN ('PENDING','RUNNING','SUCCEEDED','FAILED','HELD_MINIMAL')),
 attempts integer NOT NULL DEFAULT 0 CHECK(attempts>=0), last_error_code varchar(100), evidence_ref varchar(500),
 completed_at timestamptz, PRIMARY KEY(request_id,store),
 CHECK(state<>'SUCCEEDED' OR (completed_at IS NOT NULL AND evidence_ref IS NOT NULL)));
CREATE TABLE privacy_minimal_holds (
 id uuid PRIMARY KEY, request_id uuid NOT NULL REFERENCES privacy_requests, approved_by uuid NOT NULL REFERENCES accounts,
 minimal_scope varchar(1000) NOT NULL CHECK(length(trim(minimal_scope))>0), reason varchar(2000) NOT NULL CHECK(length(trim(reason))>0),
 created_at timestamptz NOT NULL DEFAULT now(), review_at timestamptz NOT NULL, notified_at timestamptz NOT NULL,
 released_at timestamptz, CHECK(review_at>created_at AND review_at<=created_at+interval '30 days'));
CREATE TABLE privacy_ledger_exports (
 id uuid PRIMARY KEY, deletion_ledger_id uuid NOT NULL REFERENCES deletion_ledger,
 state text NOT NULL CHECK(state IN ('PENDING','CONFIRMED','FAILED')),
 exported_at timestamptz, external_receipt varchar(500),
 CHECK(state<>'CONFIRMED' OR (exported_at IS NOT NULL AND external_receipt IS NOT NULL)),
 UNIQUE(deletion_ledger_id));

CREATE FUNCTION bump_auth_epoch() RETURNS trigger LANGUAGE plpgsql SET search_path=tutorbridge,pg_temp AS $$
BEGIN
 IF NEW.status IS DISTINCT FROM OLD.status THEN
  NEW.auth_epoch:=OLD.auth_epoch+1;
  UPDATE auth_sessions SET revoked_at=COALESCE(revoked_at,statement_timestamp()) WHERE account_id=NEW.id;
 END IF;
 IF NEW.auth_epoch<OLD.auth_epoch THEN RAISE EXCEPTION 'auth epoch cannot decrease' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER account_status_revoke BEFORE UPDATE ON accounts FOR EACH ROW EXECUTE FUNCTION bump_auth_epoch();

CREATE FUNCTION valid_session(p_token_digest text) RETURNS boolean LANGUAGE sql STABLE SET search_path=tutorbridge,pg_temp AS $$
 SELECT EXISTS(SELECT 1 FROM auth_sessions s JOIN accounts a ON a.id=s.account_id
 WHERE s.token_digest=p_token_digest AND s.revoked_at IS NULL AND a.status='ACTIVE' AND s.issued_epoch=a.auth_epoch
 AND s.expires_at>statement_timestamp() AND s.created_at<=statement_timestamp() AND s.last_seen_at<=statement_timestamp()
 AND s.created_at>statement_timestamp()-CASE WHEN a.role='ADMIN' THEN interval '12 hours' ELSE interval '7 days' END
 AND s.last_seen_at>statement_timestamp()-CASE WHEN a.role='ADMIN' THEN interval '30 minutes' ELSE interval '24 hours' END
 AND (a.role<>'ADMIN' OR (s.admin_mfa_verified_at IS NOT NULL AND EXISTS(
 SELECT 1 FROM admin_mfa_enrollments e WHERE e.account_id=a.id AND e.revoked_at IS NULL))))
$$;
-- Token hashing, password policy and email sending are application responsibilities.
CREATE FUNCTION consume_auth_challenge(p_digest text,p_purpose text) RETURNS uuid LANGUAGE plpgsql SET search_path=tutorbridge,pg_temp AS $$
DECLARE subject uuid;
BEGIN
 UPDATE auth_challenges SET consumed_at=statement_timestamp()
 WHERE token_digest=p_digest AND purpose=p_purpose AND consumed_at IS NULL AND expires_at>statement_timestamp()
 RETURNING account_id INTO subject;
 RETURN subject;
END $$;
CREATE FUNCTION guard_invitation() RETURNS trigger LANGUAGE plpgsql SET search_path=tutorbridge,pg_temp AS $$
DECLARE owner uuid; target accounts%ROWTYPE;
BEGIN
 SELECT parent_id INTO owner FROM students WHERE id=NEW.student_id FOR UPDATE;
 IF owner IS DISTINCT FROM NEW.parent_id OR NOT EXISTS(SELECT 1 FROM accounts WHERE id=owner AND role='PARENT' AND status='ACTIVE')
 THEN RAISE EXCEPTION 'invitation requires current parent owner' USING ERRCODE='23514'; END IF;
 IF TG_OP='UPDATE' AND (OLD.student_id,OLD.parent_id,OLD.target_email_digest,OLD.token_digest,OLD.created_at,OLD.expires_at)
 IS DISTINCT FROM (NEW.student_id,NEW.parent_id,NEW.target_email_digest,NEW.token_digest,NEW.created_at,NEW.expires_at)
 THEN RAISE EXCEPTION 'invitation identity immutable' USING ERRCODE='23514'; END IF;
 IF TG_OP='UPDATE' AND OLD.state<>'PENDING' AND NEW IS DISTINCT FROM OLD THEN
 RAISE EXCEPTION 'invitation terminal' USING ERRCODE='23514'; END IF;
 IF NEW.state='ACCEPTED' THEN
  SELECT * INTO target FROM accounts WHERE id=NEW.accepted_by;
  IF target.role IS DISTINCT FROM 'STUDENT' OR target.status IS DISTINCT FROM 'ACTIVE'
  OR target.email_verified_at IS NULL OR target.email_digest IS DISTINCT FROM NEW.target_email_digest
  OR NEW.expires_at<=statement_timestamp() THEN RAISE EXCEPTION 'wrong invite recipient or expired invitation' USING ERRCODE='23514'; END IF;
 END IF; RETURN NEW;
END $$;
CREATE TRIGGER student_invitation_guard BEFORE INSERT OR UPDATE ON student_invitations FOR EACH ROW EXECUTE FUNCTION guard_invitation();
CREATE FUNCTION guard_verification() RETURNS trigger LANGUAGE plpgsql SET search_path=tutorbridge,pg_temp AS $$
DECLARE owner uuid;
BEGIN
 IF NEW.status='VERIFIED' THEN
  SELECT tutor_id INTO owner FROM tutor_profiles WHERE id=NEW.profile_id;
  IF NEW.reviewed_by IS NULL OR NEW.reviewed_by=owner OR NEW.assigned_to IS DISTINCT FROM NEW.reviewed_by
  OR NEW.reviewed_at IS NULL OR NOT EXISTS(SELECT 1 FROM admin_assignments g JOIN accounts a ON a.id=g.account_id
  WHERE g.account_id=NEW.reviewed_by AND g.capability='VERIFY' AND g.revoked_at IS NULL AND a.role='ADMIN' AND a.status='ACTIVE')
  THEN RAISE EXCEPTION 'verification needs assigned independent reviewer' USING ERRCODE='23514'; END IF;
 END IF; RETURN NEW;
END $$;
CREATE TRIGGER verification_guard BEFORE INSERT OR UPDATE ON tutor_verifications FOR EACH ROW EXECUTE FUNCTION guard_verification();
CREATE FUNCTION guard_support_transition() RETURNS trigger LANGUAGE plpgsql SET search_path=tutorbridge,pg_temp AS $$
BEGIN
 IF NEW.state<>OLD.state AND NOT (
 (OLD.state='NEW' AND NEW.state='IN_PROGRESS') OR
 (OLD.state='IN_PROGRESS' AND NEW.state IN ('WAITING_INFO','RESOLVED')) OR
 (OLD.state='WAITING_INFO' AND NEW.state='IN_PROGRESS') OR
 (OLD.state='RESOLVED' AND NEW.state IN ('CLOSED','IN_PROGRESS')) OR
 (OLD.state='CLOSED' AND NEW.state='IN_PROGRESS')) THEN
 RAISE EXCEPTION 'invalid support transition' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER support_transition BEFORE UPDATE ON support_cases FOR EACH ROW EXECUTE FUNCTION guard_support_transition();
CREATE FUNCTION guard_support_grant() RETURNS trigger LANGUAGE plpgsql SET search_path=tutorbridge,pg_temp AS $$
DECLARE rq support_grant_requests%ROWTYPE; owner uuid; assigned uuid; mvstate text;
BEGIN
 IF TG_OP='UPDATE' THEN
  IF (NEW.id,NEW.request_id,NEW.grantee_id,NEW.approved_by,NEW.capability,NEW.granted_at,NEW.expires_at)
  IS DISTINCT FROM (OLD.id,OLD.request_id,OLD.grantee_id,OLD.approved_by,OLD.capability,OLD.granted_at,OLD.expires_at)
  OR (OLD.revoked_at IS NOT NULL AND NEW.revoked_at IS DISTINCT FROM OLD.revoked_at)
  THEN RAISE EXCEPTION 'grant immutable except first revoke' USING ERRCODE='23514'; END IF;
  RETURN NEW;
 END IF;
 SELECT * INTO rq FROM support_grant_requests WHERE id=NEW.request_id FOR UPDATE;
 SELECT assignee_id INTO assigned FROM support_cases WHERE id=rq.case_id FOR UPDATE;
 SELECT m.owner_id,v.state INTO owner,mvstate FROM material_versions v JOIN materials m ON m.id=v.material_id WHERE v.id=rq.material_version_id FOR UPDATE OF v;
 IF rq.state IS DISTINCT FROM 'APPROVED' OR rq.requester_id IS DISTINCT FROM NEW.grantee_id OR assigned IS DISTINCT FROM NEW.grantee_id
 OR owner IS DISTINCT FROM NEW.approved_by OR mvstate IN ('REVOKED','DELETED') OR NEW.expires_at>rq.requested_until
 OR rq.requested_until<=statement_timestamp() OR NOT EXISTS(SELECT 1 FROM accounts WHERE id=owner AND status='ACTIVE')
 OR NOT EXISTS(SELECT 1 FROM admin_assignments g JOIN accounts a ON a.id=g.account_id WHERE g.account_id=NEW.grantee_id AND g.capability='SUPPORT' AND g.revoked_at IS NULL AND a.role='ADMIN' AND a.status='ACTIVE')
 THEN RAISE EXCEPTION 'invalid support grant scope or approval' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER support_grant_guard BEFORE INSERT OR UPDATE ON support_access_grants FOR EACH ROW EXECUTE FUNCTION guard_support_grant();
CREATE FUNCTION valid_support_grant(p_grant_id uuid,p_actor_id uuid) RETURNS boolean LANGUAGE sql STABLE SET search_path=tutorbridge,pg_temp AS $$
 SELECT EXISTS(SELECT 1 FROM support_access_grants g
 JOIN support_grant_requests rq ON rq.id=g.request_id JOIN support_cases c ON c.id=rq.case_id
 JOIN accounts a ON a.id=g.grantee_id JOIN material_versions v ON v.id=rq.material_version_id
 JOIN materials m ON m.id=v.material_id JOIN accounts owner ON owner.id=m.owner_id
 WHERE g.id=p_grant_id AND g.grantee_id=p_actor_id AND g.revoked_at IS NULL AND g.expires_at>statement_timestamp()
 AND g.granted_at<=statement_timestamp() AND rq.state='APPROVED' AND c.assignee_id=p_actor_id
 AND c.state IN ('IN_PROGRESS','WAITING_INFO') AND a.role='ADMIN' AND a.status='ACTIVE' AND owner.status='ACTIVE'
 AND v.state NOT IN ('REVOKED','DELETED') AND EXISTS(SELECT 1 FROM admin_assignments x
 WHERE x.account_id=p_actor_id AND x.capability='SUPPORT' AND x.revoked_at IS NULL))
$$;
CREATE FUNCTION guard_privacy_completion() RETURNS trigger LANGUAGE plpgsql SET search_path=tutorbridge,pg_temp AS $$
BEGIN
 IF NEW.state IN ('ACTIVE_STORES_PURGED','CLOSED') AND
 (SELECT count(*) FROM privacy_store_tasks WHERE request_id=NEW.id AND state='SUCCEEDED')<>6
 THEN RAISE EXCEPTION 'all six active stores require purge evidence' USING ERRCODE='23514'; END IF;
 IF NEW.state='CLOSED' AND (NEW.closed_at IS NULL OR NEW.backup_expires_at IS NULL
 OR NEW.backup_expires_at>NEW.closed_at OR NEW.closed_at>statement_timestamp()
 OR NOT EXISTS(SELECT 1 FROM privacy_ledger_exports e WHERE e.deletion_ledger_id=NEW.deletion_ledger_id AND e.state='CONFIRMED'))
 THEN RAISE EXCEPTION 'close requires expired backup and durable ledger export evidence' USING ERRCODE='23514'; END IF;
 -- Holds are reported separately; a held store is not reported as fully purged.
 RETURN NEW;
END $$;
CREATE TRIGGER privacy_completion BEFORE INSERT OR UPDATE ON privacy_requests FOR EACH ROW EXECUTE FUNCTION guard_privacy_completion();
REVOKE ALL ON ALL TABLES IN SCHEMA tutorbridge FROM PUBLIC;
REVOKE ALL ON ALL FUNCTIONS IN SCHEMA tutorbridge FROM PUBLIC;
COMMIT;
