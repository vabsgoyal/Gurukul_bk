-- Short, human-shareable room codes for Battle Rooms - the UUID id stays the DB/WS-topic identity,
-- room_code is purely for a student to read aloud/type when inviting classmates. Left nullable
-- since this alters an already-shipped table; a plain UNIQUE constraint (not a partial index -
-- H2, used by tests, doesn't support indexes with a WHERE clause) is fine here because standard
-- SQL treats every NULL as distinct for uniqueness purposes, so old NULL-room_code rows can't
-- collide with each other or with newly-generated codes.
ALTER TABLE battle_room ADD COLUMN room_code VARCHAR(6);
ALTER TABLE battle_room ADD CONSTRAINT uq_battle_room_code UNIQUE (school_id, room_code);

-- Practice Mode: solo, no-stakes prep - a student picks a subject and works through questions
-- from the same quiz_question bank Arena/Battle Rooms use, at their own pace, with no XP reward
-- (kept out of the XP economy on purpose - this is prep, not competition). question_ids is a
-- fixed, comma-joined list decided once at creation, same convention as quiz_challenge/battle_room.
CREATE TABLE practice_session (
    id UUID PRIMARY KEY,
    school_id UUID NOT NULL,
    student_id UUID NOT NULL,
    subject_id UUID NOT NULL,
    question_ids VARCHAR(500) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_practice_session_subject FOREIGN KEY (subject_id) REFERENCES subject(id),
    CONSTRAINT fk_practice_session_school FOREIGN KEY (school_id) REFERENCES school(id)
);

CREATE INDEX idx_practice_session_student ON practice_session(school_id, student_id);

CREATE TABLE practice_answer (
    id UUID PRIMARY KEY,
    school_id UUID NOT NULL,
    session_id UUID NOT NULL,
    question_id UUID NOT NULL,
    selected_option VARCHAR(1) NOT NULL,
    correct BOOLEAN NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_practice_answer UNIQUE (session_id, question_id),
    CONSTRAINT fk_practice_answer_session FOREIGN KEY (session_id) REFERENCES practice_session(id),
    CONSTRAINT fk_practice_answer_school FOREIGN KEY (school_id) REFERENCES school(id)
);
