CREATE TABLE assessment (
    id UUID PRIMARY KEY,
    school_id UUID NOT NULL,
    section_id UUID NOT NULL,
    subject_id UUID,
    type VARCHAR(20) NOT NULL,
    title VARCHAR(255) NOT NULL,
    assessment_date DATE NOT NULL,
    max_marks DECIMAL(5, 2) NOT NULL,
    description VARCHAR(1000),
    created_by_teacher_id UUID,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_assessment_school FOREIGN KEY (school_id) REFERENCES school(id),
    CONSTRAINT fk_assessment_section FOREIGN KEY (section_id) REFERENCES class_section(id),
    CONSTRAINT fk_assessment_subject FOREIGN KEY (subject_id) REFERENCES subject(id),
    CONSTRAINT fk_assessment_teacher FOREIGN KEY (created_by_teacher_id) REFERENCES employee(id)
);

CREATE INDEX idx_assessment_section_date ON assessment(school_id, section_id, assessment_date);
