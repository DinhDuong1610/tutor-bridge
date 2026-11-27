-- TutorBridge / TB-18. Number V18 assigned by Đính (25/11/2026, #18 Q7).
-- Additive only: code running on V17 keeps working. Owned by the classes module.
SET search_path = tutorbridge, public;

-- listOffers pages offers newest first with the keyset (sent_at, id): a tutor's received offers, and a
-- parent's sent offers through the needs of their children. Until now only PENDING pairs had an index
-- (one_pending_offer_per_pair), so both lists scanned the table. A plain CREATE INDEX like V10–V17: the
-- table is small in R0.
CREATE INDEX class_offers_tutor_order ON class_offers(tutor_id, sent_at DESC, id DESC);
CREATE INDEX class_offers_need_order ON class_offers(need_id, sent_at DESC, id DESC);

-- The expiry job (TB-18 Q1) takes, a batch at a time, the PENDING offers whose deadline has passed. Only
-- PENDING rows are in it, so it stays as small as the offers still waiting for an answer.
CREATE INDEX class_offers_pending_expiry ON class_offers(expires_at) WHERE state = 'PENDING';
