CREATE TABLE approval_request (
    id UUID PRIMARY KEY,
    school_id UUID NOT NULL,
    entity_type VARCHAR(50) NOT NULL,
    entity_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL,
    submitted_by VARCHAR(255),
    approved_by VARCHAR(255),
    comment VARCHAR(500),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_approval_request UNIQUE (entity_type, entity_id),
    CONSTRAINT fk_approval_request_school FOREIGN KEY (school_id) REFERENCES school(id)
);

CREATE TABLE approval_history (
    id UUID PRIMARY KEY,
    school_id UUID NOT NULL,
    approval_request_id UUID NOT NULL,
    from_status VARCHAR(20),
    to_status VARCHAR(20) NOT NULL,
    changed_by VARCHAR(255),
    changed_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_approval_history_request FOREIGN KEY (approval_request_id) REFERENCES approval_request(id),
    CONSTRAINT fk_approval_history_school FOREIGN KEY (school_id) REFERENCES school(id)
);
