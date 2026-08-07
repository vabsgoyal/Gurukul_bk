-- Extends the existing school_event table (V2, previously finance-only: collections/expenses
-- gated by inflow_enabled/outflow_enabled) with a participation flow: RSVP, a configurable
-- registration form, or a poll - exactly one per event, picked by participation_type. All new
-- columns are nullable so every existing row (and every finance-only create/update going forward)
-- is unaffected.
ALTER TABLE school_event ADD COLUMN category VARCHAR(20);
ALTER TABLE school_event ADD COLUMN scope VARCHAR(20);
ALTER TABLE school_event ADD COLUMN section_id UUID;
ALTER TABLE school_event ADD COLUMN class_name VARCHAR(50);
ALTER TABLE school_event ADD COLUMN venue VARCHAR(255);
ALTER TABLE school_event ADD COLUMN start_at TIMESTAMP;
ALTER TABLE school_event ADD COLUMN end_at TIMESTAMP;
ALTER TABLE school_event ADD COLUMN cancelled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE school_event ADD COLUMN participation_type VARCHAR(20);
ALTER TABLE school_event ADD COLUMN registration_fields TEXT;
ALTER TABLE school_event ADD COLUMN created_by_employee_id UUID REFERENCES employee(id);

-- One RSVP/registration/vote per (event, participant) - resubmitting updates the existing row.
CREATE TABLE event_rsvp (
    id UUID PRIMARY KEY,
    school_id UUID NOT NULL,
    event_id UUID NOT NULL REFERENCES school_event(id),
    owner_id UUID NOT NULL,
    owner_type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    UNIQUE (event_id, owner_id, owner_type)
);

CREATE TABLE event_registration (
    id UUID PRIMARY KEY,
    school_id UUID NOT NULL,
    event_id UUID NOT NULL REFERENCES school_event(id),
    owner_id UUID NOT NULL,
    owner_type VARCHAR(20) NOT NULL,
    answers TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    UNIQUE (event_id, owner_id, owner_type)
);

CREATE TABLE event_poll_option (
    id UUID PRIMARY KEY,
    school_id UUID NOT NULL,
    event_id UUID NOT NULL REFERENCES school_event(id),
    label VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE event_poll_vote (
    id UUID PRIMARY KEY,
    school_id UUID NOT NULL,
    event_id UUID NOT NULL REFERENCES school_event(id),
    option_id UUID NOT NULL REFERENCES event_poll_option(id),
    owner_id UUID NOT NULL,
    owner_type VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    UNIQUE (event_id, owner_id, owner_type)
);
