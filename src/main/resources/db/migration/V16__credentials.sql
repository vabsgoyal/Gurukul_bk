-- owner_id is polymorphic (Employee or Student depending on owner_type), so no FK to a single table.
CREATE TABLE credential (
    id UUID PRIMARY KEY,
    school_id UUID NOT NULL,
    owner_type VARCHAR(20) NOT NULL,
    owner_id UUID NOT NULL,
    username VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_credential_school_username UNIQUE (school_id, username),
    CONSTRAINT uq_credential_owner UNIQUE (owner_type, owner_id),
    CONSTRAINT fk_credential_school FOREIGN KEY (school_id) REFERENCES school(id)
);
