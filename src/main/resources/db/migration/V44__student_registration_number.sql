ALTER TABLE student ADD COLUMN registration_number VARCHAR(20);

-- Backfill existing students: per (school_id, admission year), assign sequential
-- {year}{6-digit counter} numbers ordered by admission_date, then created_at for stability.
-- Written as a portable correlated subquery (no PL/pgSQL) so it also runs on H2 in tests.
UPDATE student s
SET registration_number = (
    SELECT CAST(EXTRACT(YEAR FROM s.admission_date) AS VARCHAR)
        || LPAD(CAST(ranked.rn AS VARCHAR), 6, '0')
    FROM (
        SELECT id, ROW_NUMBER() OVER (
            PARTITION BY school_id, EXTRACT(YEAR FROM admission_date)
            ORDER BY admission_date, created_at
        ) AS rn
        FROM student
    ) ranked
    WHERE ranked.id = s.id
);

-- Seed receipt_sequence rows so future generation continues from the correct counter per (school, year).
-- No ON CONFLICT needed: STUDENT_REGISTRATION is a brand-new sequence_type, so no row can already exist for it.
-- IDs are deterministic (not gen_random_uuid/RANDOM_UUID, neither of which both H2 and Postgres support) -
-- built from a row number so they're guaranteed unique within this one-off backfill insert.
INSERT INTO receipt_sequence (id, school_id, sequence_type, academic_year, last_value, created_at, updated_at)
SELECT CAST('00000000-0000-0000-0000-' || LPAD(CAST(ROW_NUMBER() OVER (ORDER BY school_id, EXTRACT(YEAR FROM admission_date)) AS VARCHAR), 12, '0') AS UUID),
       school_id,
       'STUDENT_REGISTRATION',
       CAST(EXTRACT(YEAR FROM admission_date) AS VARCHAR),
       COUNT(*),
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP
FROM student
GROUP BY school_id, EXTRACT(YEAR FROM admission_date);

ALTER TABLE student ALTER COLUMN registration_number SET NOT NULL;
ALTER TABLE student ADD CONSTRAINT uq_student_registration_number UNIQUE (school_id, registration_number);

ALTER TABLE teacher_invite ADD COLUMN target_employee_id UUID;

CREATE TABLE parent_claim_attempt (
    id UUID PRIMARY KEY,
    school_id UUID NOT NULL,
    student_roll_number VARCHAR(50) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    locked_until TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_parent_claim_attempt UNIQUE (school_id, student_roll_number)
);
