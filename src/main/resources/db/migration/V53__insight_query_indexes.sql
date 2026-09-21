-- Backs the new natural-language school-metrics chat tool (com.gurukul.insights). Two of the starter
-- entities' most common filter fields have no supporting index today: student_fee_assessment.status
-- (only indexed via the (school_id, student_id, academic_year) uniqueness constraint, no use for a
-- plain status count) and student.class_section_id (no index at all - Postgres does not auto-index FK
-- columns), so "how many students haven't paid fees" / "class 7 students..." style aggregate queries
-- would otherwise be full scans.
CREATE INDEX idx_student_fee_assessment_school_status ON student_fee_assessment(school_id, status);
CREATE INDEX idx_student_class_section ON student(school_id, class_section_id);
