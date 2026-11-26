-- TutorBridge / #189. Number V14 assigned by Đính (OP-28, 21/11/2026, #98; shape (A) chosen on #189, 23/11/2026).
-- Additive only: code running on V13 keeps working. Owned by the identity module.
SET search_path = tutorbridge, public;

-- The verifications of one profile version, by status. Until now every such read scanned the table:
-- PENDING in needsInformation and in lockPendingVerifications, which runs while submitTutorProfile holds
-- the account and profile locks (INV-16); VERIFIED for the scopes of TutorPublicRepository, once per tutor
-- on each page that getTutorPublic and matching read. A version has only a few verifications, so scope is
-- read from the table, not kept in the index. A plain CREATE INDEX like V10–V13: the table is small in R0.
CREATE INDEX tutor_verifications_profile_status ON tutor_verifications(profile_id, status);
