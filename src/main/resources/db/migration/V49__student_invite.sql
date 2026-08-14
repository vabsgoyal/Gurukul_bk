-- Admin-issued, single-use, expiring code required for student self-registration - mirrors
-- teacher_invite (see V43), just targeting a Student record instead of an Employee.
CREATE TABLE student_invite (
    id UUID PRIMARY KEY,
    school_id UUID NOT NULL,
    code VARCHAR(20) NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    used BOOLEAN NOT NULL DEFAULT FALSE,
    created_by_employee_id UUID NOT NULL REFERENCES employee(id),
    target_student_id UUID NOT NULL REFERENCES student(id),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);
