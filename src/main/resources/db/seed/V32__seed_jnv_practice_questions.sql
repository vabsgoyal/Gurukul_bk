-- Populates the quiz_question bank for JNV across every grade x subject combination (5 questions
-- each), so Practice Mode and Arena have real content to exercise instead of an empty pool.
-- class_name scoping matches V30 (Arena questions are grade-scoped, not subject-only). Guarded by
-- jnv_seed_guard-style check so this is a no-op if already seeded, same pattern as V25.
CREATE TEMP TABLE jnv_practice_question_guard AS
SELECT NOT EXISTS (
    SELECT 1 FROM quiz_question
    WHERE school_id = '99999999-9999-9999-9999-999999999999' AND question_text LIKE 'Practice: %'
) AS should_seed;

CREATE TEMP TABLE t_practice_teacher AS
SELECT id FROM employee
WHERE school_id = '99999999-9999-9999-9999-999999999999'
ORDER BY id LIMIT 1;

INSERT INTO quiz_question (
    id, school_id, subject_id, class_name, question_text,
    option_a, option_b, option_c, option_d, correct_option,
    created_by_teacher_id, created_at, updated_at
)
SELECT
    gen_random_uuid(), '99999999-9999-9999-9999-999999999999', sub.id, cs.class_name,
    'Practice: ' || cs.class_name || ' ' || sub.name || ' Question ' || q.n,
    'Option A', 'Option B', 'Option C', 'Option D',
    (ARRAY['A','B','C','D'])[1 + ((q.n - 1) % 4)],
    (SELECT id FROM t_practice_teacher),
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM subject sub
CROSS JOIN (SELECT DISTINCT class_name FROM class_section WHERE school_id = '99999999-9999-9999-9999-999999999999') cs
CROSS JOIN generate_series(1, 5) AS q(n)
WHERE sub.school_id = '99999999-9999-9999-9999-999999999999'
  AND (SELECT should_seed FROM jnv_practice_question_guard);
