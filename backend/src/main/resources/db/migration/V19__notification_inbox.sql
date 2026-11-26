-- TutorBridge / TB-21. Number V19 assigned by Đính (25/11/2026, #21 Q1).
-- Code running on V18 never reads or writes notifications, so it keeps working. Owned by the platform module.
SET search_path = tutorbridge, public;

-- The Notification schema of the contract shows when a notification was made and which version of its
-- target it is about. Existing rows, if any, take the time of this migration; target_version stays null
-- for them, and the app always writes it.
ALTER TABLE notifications ADD COLUMN created_at timestamptz NOT NULL DEFAULT now();
ALTER TABLE notifications ADD COLUMN target_version varchar(100);

-- event_id has no foreign key since V6, so the retention cleanup (TB-24) may delete the delivered event
-- while the notification stays; it remains the dedupe key of TX30 through UNIQUE(recipient_id, event_id).

-- listOwnNotifications pages one recipient's notifications newest first with the keyset (created_at, id).
CREATE INDEX notifications_recipient_order ON notifications(recipient_id, created_at DESC, id DESC);
