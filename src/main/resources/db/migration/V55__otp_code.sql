-- Real OTP storage for phone login (replaces the hardcoded "1234" dummy in OtpService). Codes are
-- stored bcrypt-hashed, never in plaintext, and are single-use + time-limited via expires_at.
CREATE TABLE otp_code (
    id UUID PRIMARY KEY,
    school_id UUID NOT NULL,
    phone VARCHAR(20) NOT NULL,
    code_hash VARCHAR(255) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    consumed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_otp_code_school FOREIGN KEY (school_id) REFERENCES school(id)
);

CREATE INDEX idx_otp_code_school_phone ON otp_code (school_id, phone);
