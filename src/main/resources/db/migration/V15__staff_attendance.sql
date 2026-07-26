CREATE TABLE staff_attendance_record (
    id UUID PRIMARY KEY,
    school_id UUID NOT NULL,
    employee_id UUID NOT NULL,
    attendance_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL,
    marked_by_employee_id UUID NOT NULL,
    remarks VARCHAR(500),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_staff_attendance_school_employee_date UNIQUE (school_id, employee_id, attendance_date),
    CONSTRAINT fk_staff_attendance_school FOREIGN KEY (school_id) REFERENCES school(id),
    CONSTRAINT fk_staff_attendance_employee FOREIGN KEY (employee_id) REFERENCES employee(id),
    CONSTRAINT fk_staff_attendance_marked_by FOREIGN KEY (marked_by_employee_id) REFERENCES employee(id)
);

CREATE INDEX idx_staff_attendance_date ON staff_attendance_record(school_id, attendance_date);
