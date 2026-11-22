-- TutorBridge / TB-17. Number V15 assigned by Đính (23/11/2026, #17 Q3), after V13 (#187) and V14 (#189).
-- Additive only: code running on V14 keeps working, it never reads or writes this table. Owned by the
-- classes module.
SET search_path = tutorbridge, public;

-- createMatchingRun (UC05) shows each candidate's common schedule: 4–12 concrete sessions of D minutes,
-- `frequency` in each 7 day block of the 28 day cycle (BR04). They are stored, not computed again on
-- getMatchingRun, because a later booking would change them, and sendOffer (TB-18) copies them into
-- offer_sessions. A candidate's rows are written in the same transaction as the candidate.
CREATE TABLE matching_candidate_sessions (
 run_id uuid NOT NULL,
 tutor_id uuid NOT NULL,
 ordinal smallint NOT NULL CHECK(ordinal BETWEEN 1 AND 12),
 during tstzrange NOT NULL,
 PRIMARY KEY(run_id,tutor_id,ordinal),
 FOREIGN KEY(run_id,tutor_id) REFERENCES matching_candidates(run_id,tutor_id),
 -- The same shape as offer_sessions: a finite, half-open, non-empty range.
 CHECK(NOT isempty(during) AND NOT lower_inf(during) AND NOT upper_inf(during) AND lower_inc(during) AND NOT upper_inc(during)),
 EXCLUDE USING gist(run_id WITH =,tutor_id WITH =,during WITH &&));
