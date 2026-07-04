CREATE TABLE school (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    address VARCHAR(500) NOT NULL,
    city VARCHAR(100) NOT NULL,
    state VARCHAR(100) NOT NULL,
    pincode VARCHAR(20) NOT NULL,
    contact_email VARCHAR(255) NOT NULL,
    contact_phone VARCHAR(50) NOT NULL,
    principal_name VARCHAR(255) NOT NULL,
    director_name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

INSERT INTO school (
    id, name, address, city, state, pincode,
    contact_email, contact_phone, principal_name, director_name,
    created_at, updated_at
)
VALUES (
    '11111111-1111-1111-1111-111111111111',
    'Gurukul Demo School',
    '123 Education Lane',
    'Jaipur',
    'Rajasthan',
    '302001',
    'admin@gurukul.demo',
    '9876543210',
    'Dr. Meena Sharma',
    'Mr. Rajesh Kumar',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);

CREATE TABLE class_section (
    id UUID PRIMARY KEY,
    school_id UUID NOT NULL,
    class_name VARCHAR(100) NOT NULL,
    section VARCHAR(20) NOT NULL,
    academic_year VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_class_section UNIQUE (school_id, class_name, section, academic_year),
    CONSTRAINT fk_class_section_school FOREIGN KEY (school_id) REFERENCES school(id)
);

INSERT INTO class_section (id, school_id, class_name, section, academic_year, created_at, updated_at)
VALUES (
    'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
    '11111111-1111-1111-1111-111111111111',
    'Grade 8',
    'A',
    '2026-27',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);

INSERT INTO class_section (id, school_id, class_name, section, academic_year, created_at, updated_at)
VALUES (
    'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',
    '11111111-1111-1111-1111-111111111111',
    'Grade 8',
    'B',
    '2026-27',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);

CREATE TABLE student (
    id UUID PRIMARY KEY,
    school_id UUID NOT NULL,
    roll_number VARCHAR(50) NOT NULL,
    name VARCHAR(255) NOT NULL,
    dob DATE NOT NULL,
    gender VARCHAR(20) NOT NULL,
    address VARCHAR(500) NOT NULL,
    parent_name VARCHAR(255) NOT NULL,
    parent_contact VARCHAR(50) NOT NULL,
    class_section_id UUID NOT NULL,
    admission_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_student_school_roll UNIQUE (school_id, roll_number),
    CONSTRAINT fk_student_class_section FOREIGN KEY (class_section_id) REFERENCES class_section(id),
    CONSTRAINT fk_student_school FOREIGN KEY (school_id) REFERENCES school(id)
);
