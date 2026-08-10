-- Roll number stops being admin-entered and becomes server-computed: the 1-indexed alphabetical
-- rank of a student's name among ACTIVE students within their class-section (ties broken by
-- admission date, then created_at). It is therefore only unique per class-section, not per school.
ALTER TABLE student DROP CONSTRAINT uq_student_school_roll;

-- Backfill: assign the same alphabetical-rank scheme to every existing ACTIVE student, per
-- class-section. Non-ACTIVE students get a distinct placeholder (never collides with the "1..N"
-- scheme) so they don't hold a slot in the active roster's numbering, matching what
-- StudentService.recomputeActiveRollNumbers does at runtime for status transitions.
UPDATE student s
SET roll_number = (
    SELECT CAST(ranked.rn AS VARCHAR)
    FROM (
        SELECT id, ROW_NUMBER() OVER (
            PARTITION BY class_section_id
            ORDER BY LOWER(name), admission_date, created_at
        ) AS rn
        FROM student
        WHERE status = 'ACTIVE'
    ) ranked
    WHERE ranked.id = s.id
)
WHERE s.status = 'ACTIVE';

UPDATE student s
SET roll_number = 'INACTIVE-' || CAST(s.id AS VARCHAR)
WHERE s.status <> 'ACTIVE';

ALTER TABLE student ADD CONSTRAINT uq_student_class_section_roll UNIQUE (class_section_id, roll_number);

-- Parent registration now claims by registrationNumber (globally unique per school and stable),
-- not roll number (only unique per class-section, and shifts as classmates join/leave).
ALTER TABLE parent_claim_attempt RENAME COLUMN student_roll_number TO student_registration_number;
