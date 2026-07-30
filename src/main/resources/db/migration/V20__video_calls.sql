-- Video calling: scheduled calls with RSVP invitees, plus a call_log of every session that
-- actually ran (immediate calls, and the session started from a scheduled call).
CREATE TABLE scheduled_call (
    id UUID PRIMARY KEY,
    school_id UUID NOT NULL,
    host_owner_type VARCHAR(20) NOT NULL,
    host_owner_id UUID NOT NULL,
    title VARCHAR(200) NOT NULL,
    scheduled_at TIMESTAMP NOT NULL,
    room_name VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL,
    reminder_sent BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_scheduled_call_room UNIQUE (room_name),
    CONSTRAINT fk_scheduled_call_school FOREIGN KEY (school_id) REFERENCES school(id)
);

CREATE INDEX idx_scheduled_call_host ON scheduled_call(school_id, host_owner_type, host_owner_id);
CREATE INDEX idx_scheduled_call_status_time ON scheduled_call(status, scheduled_at);

CREATE TABLE call_invitee (
    id UUID PRIMARY KEY,
    school_id UUID NOT NULL,
    scheduled_call_id UUID NOT NULL,
    owner_type VARCHAR(20) NOT NULL,
    owner_id UUID NOT NULL,
    rsvp_status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_call_invitee UNIQUE (scheduled_call_id, owner_type, owner_id),
    CONSTRAINT fk_call_invitee_scheduled_call FOREIGN KEY (scheduled_call_id) REFERENCES scheduled_call(id),
    CONSTRAINT fk_call_invitee_school FOREIGN KEY (school_id) REFERENCES school(id)
);

CREATE INDEX idx_call_invitee_owner ON call_invitee(school_id, owner_type, owner_id);

-- callee_owner_type/callee_owner_id are NULL when scheduled_call_id is set - a scheduled call's
-- participant list is call_invitee, not a single callee column.
CREATE TABLE call_log (
    id UUID PRIMARY KEY,
    school_id UUID NOT NULL,
    scheduled_call_id UUID,
    caller_owner_type VARCHAR(20) NOT NULL,
    caller_owner_id UUID NOT NULL,
    callee_owner_type VARCHAR(20),
    callee_owner_id UUID,
    room_name VARCHAR(100) NOT NULL,
    started_at TIMESTAMP NOT NULL,
    ended_at TIMESTAMP,
    duration_seconds BIGINT,
    outcome VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_call_log_scheduled_call FOREIGN KEY (scheduled_call_id) REFERENCES scheduled_call(id),
    CONSTRAINT fk_call_log_school FOREIGN KEY (school_id) REFERENCES school(id)
);

CREATE INDEX idx_call_log_caller ON call_log(school_id, caller_owner_type, caller_owner_id);
CREATE INDEX idx_call_log_callee ON call_log(school_id, callee_owner_type, callee_owner_id);
