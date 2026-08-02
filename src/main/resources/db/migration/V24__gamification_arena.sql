-- Gamification Phase 4a: Gurukul Arena, async 1v1 quiz challenges only. Live/synchronized
-- class-wide quizzes (needing real-time STOMP signaling) are deferred - see
-- specs/gamification/execution-plan.md.
CREATE TABLE quiz_question (
    id UUID PRIMARY KEY,
    school_id UUID NOT NULL,
    subject_id UUID NOT NULL,
    question_text VARCHAR(500) NOT NULL,
    option_a VARCHAR(255) NOT NULL,
    option_b VARCHAR(255) NOT NULL,
    option_c VARCHAR(255) NOT NULL,
    option_d VARCHAR(255) NOT NULL,
    correct_option VARCHAR(1) NOT NULL,
    created_by_teacher_id UUID NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_quiz_question_subject FOREIGN KEY (subject_id) REFERENCES subject(id),
    CONSTRAINT fk_quiz_question_school FOREIGN KEY (school_id) REFERENCES school(id)
);

CREATE INDEX idx_quiz_question_subject ON quiz_question(school_id, subject_id);

-- question_ids is a fixed, comma-joined list of quiz_question ids decided once at creation, so
-- both sides answer the exact same set - see QuizChallenge.questionIdList().
CREATE TABLE quiz_challenge (
    id UUID PRIMARY KEY,
    school_id UUID NOT NULL,
    subject_id UUID NOT NULL,
    challenger_student_id UUID NOT NULL,
    opponent_student_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL,
    winner_student_id UUID,
    question_ids VARCHAR(500) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_quiz_challenge_subject FOREIGN KEY (subject_id) REFERENCES subject(id),
    CONSTRAINT fk_quiz_challenge_school FOREIGN KEY (school_id) REFERENCES school(id)
);

CREATE INDEX idx_quiz_challenge_challenger ON quiz_challenge(school_id, challenger_student_id);
CREATE INDEX idx_quiz_challenge_opponent ON quiz_challenge(school_id, opponent_student_id);
CREATE INDEX idx_quiz_challenge_status ON quiz_challenge(status, created_at);

CREATE TABLE quiz_answer (
    id UUID PRIMARY KEY,
    school_id UUID NOT NULL,
    challenge_id UUID NOT NULL,
    student_id UUID NOT NULL,
    question_id UUID NOT NULL,
    selected_option VARCHAR(1) NOT NULL,
    correct BOOLEAN NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_quiz_answer UNIQUE (challenge_id, student_id, question_id),
    CONSTRAINT fk_quiz_answer_challenge FOREIGN KEY (challenge_id) REFERENCES quiz_challenge(id),
    CONSTRAINT fk_quiz_answer_school FOREIGN KEY (school_id) REFERENCES school(id)
);

CREATE INDEX idx_quiz_answer_challenge_student ON quiz_answer(challenge_id, student_id);
