-- Long-lived login sessions: a 24h access JWT plus a rotating refresh token (7 days, sliding).
-- Only a SHA-256 hash of the token is stored; the raw value exists only on the device.
CREATE TABLE refresh_token (
    id UUID PRIMARY KEY,
    school_id UUID NOT NULL,
    credential_id UUID NOT NULL REFERENCES credential(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    revoked_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_refresh_token_hash UNIQUE (token_hash)
);

CREATE INDEX idx_refresh_token_credential ON refresh_token (credential_id);
CREATE INDEX idx_refresh_token_expires_at ON refresh_token (expires_at);
