-- Adds Google Meet as an alternative call provider alongside the existing Jitsi bot. Per-teacher
-- OAuth: each host connects their own Google account once (see docs/google-meet-setup.md), since
-- Google Meet only lets the meeting CREATOR admit guests who join without a Google account - a
-- shared single account couldn't do that for a class it isn't actually present in.
ALTER TABLE call_log ADD COLUMN provider VARCHAR(20) NOT NULL DEFAULT 'JITSI';
ALTER TABLE scheduled_call ADD COLUMN provider VARCHAR(20) NOT NULL DEFAULT 'JITSI';

CREATE TABLE teacher_google_credential (
    id UUID PRIMARY KEY,
    school_id UUID NOT NULL,
    employee_id UUID NOT NULL,
    google_email VARCHAR(255) NOT NULL,
    encrypted_refresh_token TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_teacher_google_credential_employee UNIQUE (employee_id),
    CONSTRAINT fk_teacher_google_credential_employee FOREIGN KEY (employee_id) REFERENCES employee(id)
);
