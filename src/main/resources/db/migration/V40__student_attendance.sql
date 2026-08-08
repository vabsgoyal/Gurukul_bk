CREATE TABLE student_attendance (
    id UUID PRIMARY KEY,
    school_id UUID NOT NULL,
    teacher_id UUID NOT NULL,
    class_section_id UUID NOT NULL,
    student_id UUID NOT NULL,
    attendance_date DATE NOT NULL,
    session_name VARCHAR(100) NOT NULL,
    status VARCHAR(30) NOT NULL,
    remarks VARCHAR(500),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_student_attendance_session UNIQUE (
        school_id,
        class_section_id,
        student_id,
        attendance_date,
        session_name
    ),
    CONSTRAINT fk_student_attendance_school FOREIGN KEY (school_id) REFERENCES school(id),
    CONSTRAINT fk_student_attendance_teacher FOREIGN KEY (teacher_id) REFERENCES teacher(id),
    CONSTRAINT fk_student_attendance_class_section FOREIGN KEY (class_section_id) REFERENCES class_section(id),
    CONSTRAINT fk_student_attendance_student FOREIGN KEY (student_id) REFERENCES student(id)
);
