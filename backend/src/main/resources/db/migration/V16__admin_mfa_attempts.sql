-- TutorBridge / TB-13. Number V16 assigned by Đính (23/11/2026, #13).
-- Additive only: code running on V15 keeps working, it never reads these columns. Owned by the identity
-- module.
SET search_path = tutorbridge, public;

-- completeAdminMfa: an ADMIN_MFA challenge stops after five wrong codes (Đính, #13, 23/11/2026), so a
-- challenge from one correct password cannot be used to try every code. Other purposes never use it.
ALTER TABLE auth_challenges ADD COLUMN failed_attempts smallint NOT NULL DEFAULT 0 CHECK(failed_attempts>=0);

-- RFC 6238 time step of the last accepted TOTP code; a code of that step or an earlier one is refused,
-- so a code seen once cannot be used again (Đính, #13). Null until the first sign-in.
ALTER TABLE admin_mfa_enrollments ADD COLUMN last_used_step bigint CHECK(last_used_step>0);
