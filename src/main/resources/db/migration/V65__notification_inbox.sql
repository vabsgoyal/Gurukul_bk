-- Per-user notification inbox: one row per recipient for every push the backend sends (whether or
-- not that recipient has a device registered), so alerts can be re-read in the app and counted as
-- unread. data holds the push's JSON payload (the same keys the app routes a tap on).
-- dedupe_key is set only for alerts that must fire at most once (absence: per child per day; fee
-- due: per assessment per reminder window). NULL keys never collide, so ordinary pushes are free
-- to repeat.
CREATE TABLE notification (
    id UUID PRIMARY KEY,
    school_id UUID NOT NULL,
    recipient_owner_type VARCHAR(20) NOT NULL,
    recipient_owner_id UUID NOT NULL,
    type VARCHAR(50) NOT NULL,
    title VARCHAR(255) NOT NULL,
    body TEXT NOT NULL,
    data TEXT,
    dedupe_key VARCHAR(200),
    read_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_notification_dedupe UNIQUE (recipient_owner_type, recipient_owner_id, dedupe_key)
);

CREATE INDEX idx_notification_recipient_time
    ON notification (school_id, recipient_owner_type, recipient_owner_id, created_at);
