-- Exam results, configurable grading scale, and per-section report-card publishing (TASK.md
-- Task 6: exam / grade card / report card flow). Term is a free-text field (e.g. "Term 1") set
-- per assessment rather than a separate entity - kept flexible per school rather than fixed.
ALTER TABLE assessment ADD COLUMN term VARCHAR(50);

CREATE TABLE assessment_result (
    id UUID PRIMARY KEY,
    school_id UUID NOT NULL,
    assessment_id UUID NOT NULL,
    student_id UUID NOT NULL,
    marks_obtained DECIMAL(5, 2),
    absent BOOLEAN NOT NULL DEFAULT FALSE,
    remarks VARCHAR(500),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_assessment_result UNIQUE (assessment_id, student_id),
    CONSTRAINT fk_assessment_result_assessment FOREIGN KEY (assessment_id) REFERENCES assessment(id),
    CONSTRAINT fk_assessment_result_student FOREIGN KEY (student_id) REFERENCES student(id),
    CONSTRAINT fk_assessment_result_school FOREIGN KEY (school_id) REFERENCES school(id)
);

CREATE INDEX idx_assessment_result_student ON assessment_result(school_id, student_id);

-- Configurable marks-percentage -> letter-grade bands, per school. No rows for a school means
-- GradingScaleService falls back to its own hardcoded default bands.
CREATE TABLE grading_band (
    id UUID PRIMARY KEY,
    school_id UUID NOT NULL,
    min_percentage DECIMAL(5, 2) NOT NULL,
    max_percentage DECIMAL(5, 2) NOT NULL,
    label VARCHAR(10) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_grading_band_school FOREIGN KEY (school_id) REFERENCES school(id)
);

CREATE INDEX idx_grading_band_school ON grading_band(school_id);

-- One row per (class-section, term) that has been published - marks entry for any assessment in
-- that section/term locks once this exists, and only then can a STUDENT session view the report
-- card (a teacher/admin can preview it any time, published or not - see ReportCardService).
CREATE TABLE report_card_publication (
    id UUID PRIMARY KEY,
    school_id UUID NOT NULL,
    class_section_id UUID NOT NULL,
    term VARCHAR(50) NOT NULL,
    published_at TIMESTAMP NOT NULL,
    published_by_employee_id UUID NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_report_card_publication UNIQUE (class_section_id, term),
    CONSTRAINT fk_report_card_publication_section FOREIGN KEY (class_section_id) REFERENCES class_section(id),
    CONSTRAINT fk_report_card_publication_employee FOREIGN KEY (published_by_employee_id) REFERENCES employee(id),
    CONSTRAINT fk_report_card_publication_school FOREIGN KEY (school_id) REFERENCES school(id)
);
