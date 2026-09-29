-- Timetable (GUR-SCHED-01, phase 1). One bell schedule per school, a Saturday on/off switch, and
-- a weekly per-section timetable. Slots store the period NUMBER (not an FK to period_definition)
-- so the bell schedule can be re-saved without cascading into timetables; the service refuses a
-- bell-schedule change that would orphan slots.

CREATE TABLE period_definition (
    id UUID PRIMARY KEY,
    school_id UUID NOT NULL,
    period_number INT NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    is_break BOOLEAN NOT NULL DEFAULT FALSE,
    label VARCHAR(50),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_period_definition_school_number UNIQUE (school_id, period_number),
    CONSTRAINT fk_period_definition_school FOREIGN KEY (school_id) REFERENCES school(id)
);

-- Mon-Fri are always school days; Saturday is the only switch. Absent row = Saturday off.
CREATE TABLE timetable_setting (
    id UUID PRIMARY KEY,
    school_id UUID NOT NULL,
    saturday_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_timetable_setting_school UNIQUE (school_id),
    CONSTRAINT fk_timetable_setting_school FOREIGN KEY (school_id) REFERENCES school(id)
);

-- academic_year is copied from the section so a teacher's slot in last year's section doesn't
-- clash with this year's timetable.
CREATE TABLE timetable_slot (
    id UUID PRIMARY KEY,
    school_id UUID NOT NULL,
    section_id UUID NOT NULL,
    academic_year VARCHAR(255) NOT NULL,
    day_of_week VARCHAR(10) NOT NULL,
    period_number INT NOT NULL,
    subject_id UUID NOT NULL,
    teacher_id UUID NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_timetable_slot_section_day_period UNIQUE (section_id, day_of_week, period_number),
    CONSTRAINT uq_timetable_slot_teacher_day_period UNIQUE (school_id, academic_year, teacher_id, day_of_week, period_number),
    CONSTRAINT fk_timetable_slot_school FOREIGN KEY (school_id) REFERENCES school(id),
    CONSTRAINT fk_timetable_slot_section FOREIGN KEY (section_id) REFERENCES class_section(id),
    CONSTRAINT fk_timetable_slot_subject FOREIGN KEY (subject_id) REFERENCES subject(id),
    CONSTRAINT fk_timetable_slot_teacher FOREIGN KEY (teacher_id) REFERENCES employee(id)
);

CREATE INDEX idx_timetable_slot_teacher ON timetable_slot (teacher_id);
