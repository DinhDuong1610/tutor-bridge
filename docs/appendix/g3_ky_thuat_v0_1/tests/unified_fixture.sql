-- Synthetic only; no real email, password, MFA secret or learning data.
SET search_path=tutorbridge,public;
INSERT INTO accounts(id,role,status,email_digest,email_verified_at) VALUES
(pg_temp.tid('student-account'),'STUDENT','ACTIVE','student-email-digest',now()),
(pg_temp.tid('parent-other'),'PARENT','ACTIVE','other-parent-digest',now()),
(pg_temp.tid('support'),'ADMIN','ACTIVE','support-digest',now()),
(pg_temp.tid('verifier'),'ADMIN','ACTIVE','verifier-digest',now());
INSERT INTO admin_assignments(account_id,capability,granted_by) VALUES
(pg_temp.tid('support'),'SUPPORT',pg_temp.tid('verifier')),
(pg_temp.tid('verifier'),'VERIFY',pg_temp.tid('support'));
