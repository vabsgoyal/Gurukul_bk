-- Push notification device tokens (Expo push tokens - see PushNotificationService). One row per
-- device a user is logged in on; the same owner can have several (phone + tablet, etc).
CREATE TABLE device_token (
    id UUID PRIMARY KEY,
    school_id UUID NOT NULL,
    owner_type VARCHAR(20) NOT NULL,
    owner_id UUID NOT NULL,
    expo_push_token VARCHAR(200) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_device_token_token UNIQUE (expo_push_token),
    CONSTRAINT fk_device_token_school FOREIGN KEY (school_id) REFERENCES school(id)
);

CREATE INDEX idx_device_token_owner ON device_token(school_id, owner_type, owner_id);
