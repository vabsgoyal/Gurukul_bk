-- Append-only record of every insert/update/delete of a persisted entity: who (actor), what (entity
-- type/id + field-level old/new diff as JSON), and when. Written by AuditEventListener in the same
-- transaction as the change itself. No FK to school so history outlives any future school removal.
-- actor_* are null / 'SYSTEM' for writes with no authenticated user (seeders, device API-key calls).
CREATE TABLE audit_log (
    id UUID PRIMARY KEY,
    school_id UUID NOT NULL,
    entity_type VARCHAR(100) NOT NULL,
    entity_id VARCHAR(100) NOT NULL,
    action VARCHAR(10) NOT NULL,
    changes TEXT NOT NULL,
    actor_owner_id UUID,
    actor_owner_type VARCHAR(20),
    actor_role VARCHAR(20) NOT NULL,
    actor_username VARCHAR(200) NOT NULL,
    occurred_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_audit_log_school_time ON audit_log (school_id, occurred_at);
CREATE INDEX idx_audit_log_school_entity ON audit_log (school_id, entity_type, entity_id);
