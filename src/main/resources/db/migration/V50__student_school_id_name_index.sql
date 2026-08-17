-- Backs GET /api/v1/students' paginated findAllBySchoolIdOrderByNameAsc - both the LIMIT/OFFSET
-- content query and Spring Data's separate COUNT(*) query were falling back to a full scan (only
-- index available was the (school_id, roll_number) uniqueness constraint, no use for a plain
-- school_id lookup or a name ordering).
CREATE INDEX idx_student_school_name ON student(school_id, name);
