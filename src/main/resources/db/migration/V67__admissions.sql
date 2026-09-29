-- Admissions: an admin records an application (class applied for, student + parent details), moves
-- it NEW -> UNDER_REVIEW -> APPROVED/REJECTED, and on enrolment picks the section, which creates the
-- real student row through the normal enrolment path. stage is a plain VARCHAR (no CHECK) like every
-- other enum column here, so adding a stage later is a code-only change.
CREATE TABLE admission_application (
    id UUID PRIMARY KEY,
    school_id UUID NOT NULL,
    stage VARCHAR(20) NOT NULL,
    student_name VARCHAR(255) NOT NULL,
    dob DATE NOT NULL,
    gender VARCHAR(20) NOT NULL,
    address VARCHAR(500) NOT NULL,
    previous_school_name VARCHAR(255),
    parent_name VARCHAR(255) NOT NULL,
    parent_contact VARCHAR(50) NOT NULL,
    parent_email VARCHAR(255),
    applied_class_name VARCHAR(100) NOT NULL,
    assigned_class_section_id UUID,
    notes VARCHAR(2000),
    -- Set once, on enrolment. UNIQUE: a second guard (besides the row lock in AdmissionService) that
    -- one application can never produce two students - and so never two fee bills.
    student_id UUID,
    decided_at TIMESTAMP,
    enrolled_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_admission_application_student UNIQUE (student_id),
    CONSTRAINT fk_admission_application_school FOREIGN KEY (school_id) REFERENCES school(id),
    -- SET NULL: deleting a student/section later must not be blocked by an old application.
    CONSTRAINT fk_admission_application_student FOREIGN KEY (student_id) REFERENCES student(id) ON DELETE SET NULL,
    CONSTRAINT fk_admission_application_class_section FOREIGN KEY (assigned_class_section_id)
        REFERENCES class_section(id) ON DELETE SET NULL
);

CREATE INDEX idx_admission_application_school_stage ON admission_application (school_id, stage);

-- Optional supporting documents. Only the private S3 object key is stored; download links are
-- presigned, time-limited, on every read.
CREATE TABLE admission_document (
    id UUID PRIMARY KEY,
    school_id UUID NOT NULL,
    application_id UUID NOT NULL,
    document_type VARCHAR(40) NOT NULL,
    object_key VARCHAR(1024) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    file_size_bytes BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_admission_document_object_key UNIQUE (object_key),
    CONSTRAINT fk_admission_document_application FOREIGN KEY (application_id)
        REFERENCES admission_application(id) ON DELETE CASCADE
);

CREATE INDEX idx_admission_document_application ON admission_document (application_id);
