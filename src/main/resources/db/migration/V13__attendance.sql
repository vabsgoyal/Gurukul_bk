CREATE TABLE attendance_record (
    id UUID PRIMARY KEY,
    school_id UUID NOT NULL,
    student_id UUID NOT NULL,
    section_id UUID NOT NULL,
    attendance_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL,
    marked_by_teacher_id UUID NOT NULL,
    remarks VARCHAR(500),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_attendance_school_student_date UNIQUE (school_id, student_id, attendance_date),
    CONSTRAINT fk_attendance_school FOREIGN KEY (school_id) REFERENCES school(id),
    CONSTRAINT fk_attendance_student FOREIGN KEY (student_id) REFERENCES student(id),
    CONSTRAINT fk_attendance_section FOREIGN KEY (section_id) REFERENCES class_section(id),
    CONSTRAINT fk_attendance_teacher FOREIGN KEY (marked_by_teacher_id) REFERENCES employee(id)
);

CREATE INDEX idx_attendance_section_date ON attendance_record(school_id, section_id, attendance_date);
