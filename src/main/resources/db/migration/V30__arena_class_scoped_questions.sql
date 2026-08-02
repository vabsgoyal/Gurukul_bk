-- Quiz questions were previously scoped only by subject, with no grade/class concept - a question
-- authored for Mathematics was served to any student challenging in that subject regardless of
-- grade. Adds class_name (grade, e.g. "Grade 8"), matching the same grade-level-across-sections
-- scoping Battle Rooms already uses (see battle_room.class_name) rather than a specific
-- class_section_id, since sections normally share a syllabus and requiring a teacher to author
-- the same question per-section would be needless friction.
--
-- Nullable since this alters an already-shipped table with existing rows (no backfill value is
-- knowable for those); new rows always populate it going forward per CreateQuizQuestionRequest's
-- validation.
ALTER TABLE quiz_question ADD COLUMN class_name VARCHAR(100);

CREATE INDEX idx_quiz_question_subject_class ON quiz_question(school_id, subject_id, class_name);
