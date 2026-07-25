CREATE TABLE subject (
    id UUID PRIMARY KEY,
    school_id UUID NOT NULL,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(500),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_subject_school_code UNIQUE (school_id, code),
    CONSTRAINT fk_subject_school FOREIGN KEY (school_id) REFERENCES school(id)
);

CREATE TABLE section_subject_teacher (
    id UUID PRIMARY KEY,
    school_id UUID NOT NULL,
    section_id UUID NOT NULL,
    subject_id UUID NOT NULL,
    teacher_id UUID NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_section_subject_teacher UNIQUE (section_id, subject_id, teacher_id),
    CONSTRAINT fk_sst_school FOREIGN KEY (school_id) REFERENCES school(id),
    CONSTRAINT fk_sst_section FOREIGN KEY (section_id) REFERENCES class_section(id),
    CONSTRAINT fk_sst_subject FOREIGN KEY (subject_id) REFERENCES subject(id),
    CONSTRAINT fk_sst_teacher FOREIGN KEY (teacher_id) REFERENCES employee(id)
);

ALTER TABLE class_section ADD COLUMN class_teacher_id UUID;
ALTER TABLE class_section ADD CONSTRAINT fk_class_section_teacher FOREIGN KEY (class_teacher_id) REFERENCES employee(id);
-- Unique constraint to ensure a teacher manages only one class per academic year
ALTER TABLE class_section ADD CONSTRAINT uq_class_section_teacher UNIQUE (class_teacher_id, academic_year);

ALTER TABLE employee ADD COLUMN academic_background VARCHAR(1000);
ALTER TABLE employee ADD COLUMN experience_years INT;
ALTER TABLE employee ADD COLUMN experience_months INT;
ALTER TABLE employee ADD COLUMN rating DECIMAL(3, 2);
