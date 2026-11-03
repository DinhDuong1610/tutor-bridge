-- TutorBridge / TB-03. tutorbridge_app is the runtime account: data access only, no DDL.
-- Roles are created outside migrations, see infra/db-init/01-roles.sh.
-- No EXECUTE grant: trigger functions fire without it and are not called directly.
SET search_path = tutorbridge, public;

GRANT USAGE ON SCHEMA tutorbridge TO tutorbridge_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA tutorbridge TO tutorbridge_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA tutorbridge TO tutorbridge_app;

ALTER DEFAULT PRIVILEGES IN SCHEMA tutorbridge
 GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO tutorbridge_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA tutorbridge
 GRANT USAGE, SELECT ON SEQUENCES TO tutorbridge_app;
