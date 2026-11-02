-- TutorBridge / TB-07. V2 requires that application roles have no direct booking DML.
-- The trigger keeps writing bookings, with the rights of its owner.
SET search_path = tutorbridge, public;

ALTER FUNCTION sync_session_bookings() SECURITY DEFINER;
REVOKE INSERT, UPDATE, DELETE ON resource_bookings FROM tutorbridge_app;
