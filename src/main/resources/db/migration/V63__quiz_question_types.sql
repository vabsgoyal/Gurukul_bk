-- Question bank types (AI quiz generation). Existing rows are all multiple choice, so the new
-- column defaults to MCQ. NUMERIC and SHORT_WORD questions keep their answer in answer_text and
-- have no options, so the option/correct_option columns become nullable. Arena games (challenges,
-- battle rooms, practice) only ever select MCQ rows - see QuizQuestionRepository.
ALTER TABLE quiz_question ADD COLUMN question_type VARCHAR(20) DEFAULT 'MCQ' NOT NULL;
ALTER TABLE quiz_question ADD COLUMN answer_text VARCHAR(255);
ALTER TABLE quiz_question ALTER COLUMN option_a DROP NOT NULL;
ALTER TABLE quiz_question ALTER COLUMN option_b DROP NOT NULL;
ALTER TABLE quiz_question ALTER COLUMN option_c DROP NOT NULL;
ALTER TABLE quiz_question ALTER COLUMN option_d DROP NOT NULL;
ALTER TABLE quiz_question ALTER COLUMN correct_option DROP NOT NULL;

CREATE INDEX idx_quiz_question_type ON quiz_question(school_id, subject_id, question_type);
