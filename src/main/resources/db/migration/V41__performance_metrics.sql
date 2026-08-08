CREATE TABLE assessment_result (
    id UUID PRIMARY KEY,
    school_id UUID NOT NULL,
    assessment_id UUID NOT NULL,
    student_id UUID NOT NULL,
    marks_obtained DECIMAL(5, 2) NOT NULL,
    remarks VARCHAR(500),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_assessment_result_school_assessment_student UNIQUE (school_id, assessment_id, student_id),
    CONSTRAINT fk_assessment_result_school FOREIGN KEY (school_id) REFERENCES school(id),
    CONSTRAINT fk_assessment_result_assessment FOREIGN KEY (assessment_id) REFERENCES assessment(id),
    CONSTRAINT fk_assessment_result_student FOREIGN KEY (student_id) REFERENCES student(id)
);

CREATE INDEX idx_assessment_result_student ON assessment_result(school_id, student_id);
CREATE INDEX idx_assessment_result_assessment ON assessment_result(school_id, assessment_id);

CREATE TABLE employee_feedback (
    id UUID PRIMARY KEY,
    school_id UUID NOT NULL,
    employee_id UUID NOT NULL,
    rating DECIMAL(3, 2) NOT NULL,
    category VARCHAR(30) NOT NULL,
    comment VARCHAR(1000),
    feedback_date DATE NOT NULL,
    submitted_by VARCHAR(255),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_employee_feedback_school FOREIGN KEY (school_id) REFERENCES school(id),
    CONSTRAINT fk_employee_feedback_employee FOREIGN KEY (employee_id) REFERENCES employee(id)
);

CREATE INDEX idx_employee_feedback_employee_date ON employee_feedback(school_id, employee_id, feedback_date);
