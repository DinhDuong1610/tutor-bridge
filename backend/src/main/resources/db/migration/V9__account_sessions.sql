-- TutorBridge / TB-08. Number V9 assigned by Đính on PR #39 (08/11/2026).
-- Additive only: code running on V8 keeps working. Tables are owned by the identity module.
SET search_path = tutorbridge, public;

-- SessionMetadata.deviceLabel. Derived by the server at login; empty for sessions issued before V9.
ALTER TABLE auth_sessions ADD COLUMN device_label varchar(100) NOT NULL DEFAULT '';

-- RegistrationInput.termsVersion (F01: versioned acceptance of the terms). One row per version accepted.
CREATE TABLE terms_acceptances (
 account_id uuid NOT NULL REFERENCES accounts,
 terms_version varchar(100) NOT NULL CHECK(length(terms_version)>=1),
 accepted_at timestamptz NOT NULL DEFAULT now(),
 PRIMARY KEY(account_id,terms_version));

-- Attempt limits per account and per source (INV-80: not in memory). subject_digest is a keyed
-- digest of the email or of the source address, never the raw value. Limits live in configuration.

-- LOGIN: one row per failed attempt, counted over a sliding window (UC01: five failures in the last
-- 15 minutes). A fixed window would let failures on both sides of a window edge go uncounted.
CREATE TABLE auth_login_failures (
 id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 scope text NOT NULL CHECK(scope IN ('ACCOUNT','SOURCE')),
 subject_digest char(64) NOT NULL CHECK(subject_digest~'^[0-9a-f]{64}$'),
 failed_at timestamptz NOT NULL);
CREATE INDEX auth_login_failures_subject ON auth_login_failures(scope,subject_digest,failed_at);
-- Lets a cleanup job remove failures older than the window in batches.
CREATE INDEX auth_login_failures_age ON auth_login_failures(failed_at);

-- Other commands keep a fixed window while their limits are provisional (Đính, 10/11/2026).
CREATE TABLE auth_rate_counters (
 action text NOT NULL CHECK(action IN ('REGISTER','RESEND_VERIFICATION','RECOVERY')),
 scope text NOT NULL CHECK(scope IN ('ACCOUNT','SOURCE')),
 subject_digest char(64) NOT NULL CHECK(subject_digest~'^[0-9a-f]{64}$'),
 window_started_at timestamptz NOT NULL,
 attempt_count integer NOT NULL CHECK(attempt_count>0),
 blocked_until timestamptz,
 PRIMARY KEY(action,scope,subject_digest),
 CHECK(blocked_until IS NULL OR blocked_until>window_started_at));
-- Lets a cleanup job remove expired windows in batches.
CREATE INDEX auth_rate_counters_window ON auth_rate_counters(window_started_at);
