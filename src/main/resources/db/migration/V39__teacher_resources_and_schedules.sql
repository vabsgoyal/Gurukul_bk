CREATE TABLE teacher_resource (
    id UUID PRIMARY KEY,
    school_id UUID NOT NULL,
    teacher_id UUID NOT NULL,
    class_section_id UUID NOT NULL,
    subject_name VARCHAR(100) NOT NULL,
    resource_type VARCHAR(40) NOT NULL,
    title VARCHAR(255) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    resource_url VARCHAR(1000) NOT NULL,
    available_offline BOOLEAN NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_teacher_resource_school FOREIGN KEY (school_id) REFERENCES school(id),
    CONSTRAINT fk_teacher_resource_teacher FOREIGN KEY (teacher_id) REFERENCES teacher(id),
    CONSTRAINT fk_teacher_resource_class_section FOREIGN KEY (class_section_id) REFERENCES class_section(id)
);

CREATE TABLE teacher_assessment_schedule (
    id UUID PRIMARY KEY,
    school_id UUID NOT NULL,
    teacher_id UUID NOT NULL,
    class_section_id UUID NOT NULL,
    subject_name VARCHAR(100) NOT NULL,
    assessment_type VARCHAR(40) NOT NULL,
    title VARCHAR(255) NOT NULL,
    scheduled_at TIMESTAMP NOT NULL,
    syllabus VARCHAR(2000) NOT NULL,
    instructions VARCHAR(1000) NOT NULL,
    max_marks INTEGER NOT NULL,
    status VARCHAR(40) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_teacher_assessment_school FOREIGN KEY (school_id) REFERENCES school(id),
    CONSTRAINT fk_teacher_assessment_teacher FOREIGN KEY (teacher_id) REFERENCES teacher(id),
    CONSTRAINT fk_teacher_assessment_class_section FOREIGN KEY (class_section_id) REFERENCES class_section(id)
);

INSERT INTO teacher_resource (
    id, school_id, teacher_id, class_section_id, subject_name, resource_type,
    title, description, resource_url, available_offline, created_at, updated_at
)
VALUES (
    '12121212-1212-1212-1212-121212121212',
    '11111111-1111-1111-1111-111111111111',
    'cccccccc-cccc-cccc-cccc-cccccccccccc',
    'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
    'Mathematics',
    'NOTES',
    'Algebra revision notes',
    'Important formulas and solved examples for the next class test.',
    'https://resources.gurukul.demo/math/algebra-revision.pdf',
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);

INSERT INTO teacher_assessment_schedule (
    id, school_id, teacher_id, class_section_id, subject_name, assessment_type,
    title, scheduled_at, syllabus, instructions, max_marks, status, created_at, updated_at
)
VALUES (
    '34343434-3434-3434-3434-343434343434',
    '11111111-1111-1111-1111-111111111111',
    'cccccccc-cccc-cccc-cccc-cccccccccccc',
    'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
    'Mathematics',
    'QUIZ',
    'Algebra basics quiz',
    TIMESTAMP '2026-08-10 10:30:00',
    'Linear equations, variables, and simple word problems.',
    '20 minute quiz. Carry notebook and pencil.',
    20,
    'SCHEDULED',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);
