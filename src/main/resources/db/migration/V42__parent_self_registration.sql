-- Self-registration for student/teacher/parent, gated by admin approval. Reuses the existing
-- approval_request table (see V-something workflow) rather than a new status column - a pending
-- registration is just "credential.enabled = false" until an admin approves it.
ALTER TABLE credential ADD COLUMN enabled BOOLEAN NOT NULL DEFAULT TRUE;

-- Parent is scoped per school like everything else - a parent with children at two schools has
-- two separate rows (and two separate credentials), not one cross-school identity.
CREATE TABLE parent (
    id UUID PRIMARY KEY,
    school_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255),
    phone VARCHAR(50),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE parent_student_link (
    id UUID PRIMARY KEY,
    school_id UUID NOT NULL,
    parent_id UUID NOT NULL REFERENCES parent(id),
    student_id UUID NOT NULL REFERENCES student(id),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    UNIQUE (parent_id, student_id)
);

-- Admin-issued, single-use, expiring code required for teacher self-registration.
CREATE TABLE teacher_invite (
    id UUID PRIMARY KEY,
    school_id UUID NOT NULL,
    code VARCHAR(20) NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    used BOOLEAN NOT NULL DEFAULT FALSE,
    created_by_employee_id UUID NOT NULL REFERENCES employee(id),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);
