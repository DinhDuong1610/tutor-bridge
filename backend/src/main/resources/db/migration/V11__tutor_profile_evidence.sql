-- TutorBridge / TB-12c. Number V11 assigned by Đính for TB-12 (#12 Q3, 15/11/2026).
-- Additive only: code running on V10 keeps working. Tables are owned by the identity module.
SET search_path = tutorbridge, public;

-- #12 Q3: the evidence a profile version is submitted with. READY evidence is reused by later versions
-- without a new upload, so one upload may belong to many versions. tutor_verifications.evidence_key keeps
-- the SHA-256 of this set's sorted upload ids, so a verification names the evidence it was asked about.
CREATE TABLE tutor_profile_evidence (
 profile_id uuid NOT NULL REFERENCES tutor_profiles,
 upload_id uuid NOT NULL REFERENCES tutor_evidence_uploads,
 PRIMARY KEY(profile_id,upload_id));
CREATE INDEX tutor_profile_evidence_upload ON tutor_profile_evidence(upload_id);

-- Only READY evidence of the version's own tutor, and only while the version is a DRAFT: once submitted,
-- its evidence is frozen like the rest of the version (INV-54). Checked here as well as in the service
-- (Huy, TB-12c plan, 19/11/2026).
-- The rows are read FOR SHARE, the version before the upload (INV-16): a transaction that is moving the
-- version out of DRAFT, or the upload out of READY, holds them until it commits, and the check then reads
-- what it committed instead of the state before it (Codex review of #186).
CREATE FUNCTION guard_profile_evidence() RETURNS trigger LANGUAGE plpgsql SET search_path=tutorbridge,pg_temp AS $$
DECLARE profile tutor_profiles%ROWTYPE; upload tutor_evidence_uploads%ROWTYPE;
BEGIN
 IF TG_OP IN ('UPDATE','DELETE') THEN
  SELECT * INTO profile FROM tutor_profiles WHERE id=OLD.profile_id FOR SHARE;
  IF profile.status IS DISTINCT FROM 'DRAFT' THEN
   RAISE EXCEPTION 'evidence of a submitted profile version is frozen' USING ERRCODE='23514';
  END IF;
 END IF;
 IF TG_OP='DELETE' THEN RETURN OLD; END IF;
 SELECT * INTO profile FROM tutor_profiles WHERE id=NEW.profile_id FOR SHARE;
 SELECT * INTO upload FROM tutor_evidence_uploads WHERE id=NEW.upload_id FOR SHARE;
 IF profile.status IS DISTINCT FROM 'DRAFT' OR upload.state IS DISTINCT FROM 'READY'
 OR upload.owner_id IS DISTINCT FROM profile.tutor_id THEN
  RAISE EXCEPTION 'a draft version takes only ready evidence of the profile''s own tutor' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER profile_evidence_guard BEFORE INSERT OR UPDATE OR DELETE ON tutor_profile_evidence
 FOR EACH ROW EXECUTE FUNCTION guard_profile_evidence();

-- OP-24 (Đính, 17/11/2026): when a tutor submits a new version over one that needs information, the old
-- version becomes RETIRED and its verification stays exactly as the verifier left it. From then on no
-- verification of a RETIRED version is opened or changed, so a late decision cannot land on it. A
-- verification also stays with the version it was opened for, so it cannot be moved off a RETIRED one.
-- The version is read FOR SHARE, so a decision racing the submit that retires it waits and then sees
-- RETIRED. Callers lock the version before its verification (INV-16), so this never waits on them.
-- The V5 rule for VERIFIED is kept as it was.
CREATE OR REPLACE FUNCTION guard_verification() RETURNS trigger LANGUAGE plpgsql SET search_path=tutorbridge,pg_temp AS $$
DECLARE owner uuid; profile_status text;
BEGIN
 IF TG_OP='UPDATE' AND NEW.profile_id IS DISTINCT FROM OLD.profile_id THEN
  RAISE EXCEPTION 'a verification stays with its profile version' USING ERRCODE='23514';
 END IF;
 SELECT tutor_id, status INTO owner, profile_status FROM tutor_profiles WHERE id=NEW.profile_id FOR SHARE;
 IF profile_status='RETIRED' THEN
  RAISE EXCEPTION 'the verification of a retired profile version is closed' USING ERRCODE='23514';
 END IF;
 IF NEW.status='VERIFIED' THEN
  IF NEW.reviewed_by IS NULL OR NEW.reviewed_by=owner OR NEW.assigned_to IS DISTINCT FROM NEW.reviewed_by
  OR NEW.reviewed_at IS NULL OR NOT EXISTS(SELECT 1 FROM admin_assignments g JOIN accounts a ON a.id=g.account_id
  WHERE g.account_id=NEW.reviewed_by AND g.capability='VERIFY' AND g.revoked_at IS NULL AND a.role='ADMIN' AND a.status='ACTIVE')
  THEN RAISE EXCEPTION 'verification needs assigned independent reviewer' USING ERRCODE='23514'; END IF;
 END IF; RETURN NEW;
END $$;
