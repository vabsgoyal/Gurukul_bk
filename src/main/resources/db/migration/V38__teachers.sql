CREATE TABLE teacher (
    id UUID PRIMARY KEY,
    school_id UUID NOT NULL,
    employee_code VARCHAR(50) NOT NULL,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    phone VARCHAR(50) NOT NULL,
    qualification VARCHAR(255) NOT NULL,
    specialization VARCHAR(255) NOT NULL,
    joining_date DATE NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_teacher_school_employee UNIQUE (school_id, employee_code),
    CONSTRAINT uq_teacher_school_email UNIQUE (school_id, email),
    CONSTRAINT fk_teacher_school FOREIGN KEY (school_id) REFERENCES school(id)
);

CREATE TABLE teacher_class_assignment (
    id UUID PRIMARY KEY,
    school_id UUID NOT NULL,
    teacher_id UUID NOT NULL,
    class_section_id UUID NOT NULL,
    subject_name VARCHAR(100) NOT NULL,
    assignment_role VARCHAR(40) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_teacher_assignment UNIQUE (
        school_id,
        teacher_id,
        class_section_id,
        subject_name,
        assignment_role
    ),
    CONSTRAINT fk_teacher_assignment_school FOREIGN KEY (school_id) REFERENCES school(id),
    CONSTRAINT fk_teacher_assignment_teacher FOREIGN KEY (teacher_id) REFERENCES teacher(id),
    CONSTRAINT fk_teacher_assignment_class_section FOREIGN KEY (class_section_id) REFERENCES class_section(id)
);

INSERT INTO teacher (
    id, school_id, employee_code, name, email, phone, qualification,
    specialization, joining_date, status, created_at, updated_at
)
VALUES (
    'cccccccc-cccc-cccc-cccc-cccccccccccc',
    '11111111-1111-1111-1111-111111111111',
    'T-1001',
    'Anita Verma',
    'anita.verma@gurukul.demo',
    '9876543210',
    'M.Sc. Mathematics, B.Ed.',
    'Mathematics',
    DATE '2024-04-01',
    'ACTIVE',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);

INSERT INTO teacher (
    id, school_id, employee_code, name, email, phone, qualification,
    specialization, joining_date, status, created_at, updated_at
)
VALUES (
    'dddddddd-dddd-dddd-dddd-dddddddddddd',
    '11111111-1111-1111-1111-111111111111',
    'T-1002',
    'Rohit Mehta',
    'rohit.mehta@gurukul.demo',
    '9123456780',
    'M.A. English, B.Ed.',
    'English',
    DATE '2023-06-15',
    'ACTIVE',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);

INSERT INTO teacher_class_assignment (
    id, school_id, teacher_id, class_section_id, subject_name,
    assignment_role, created_at, updated_at
)
VALUES (
    'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee',
    '11111111-1111-1111-1111-111111111111',
    'cccccccc-cccc-cccc-cccc-cccccccccccc',
    'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
    'Mathematics',
    'CLASS_TEACHER',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);

INSERT INTO teacher_class_assignment (
    id, school_id, teacher_id, class_section_id, subject_name,
    assignment_role, created_at, updated_at
)
VALUES (
    'ffffffff-ffff-ffff-ffff-ffffffffffff',
    '11111111-1111-1111-1111-111111111111',
    'dddddddd-dddd-dddd-dddd-dddddddddddd',
    'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
    'English',
    'SUBJECT_TEACHER',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);
