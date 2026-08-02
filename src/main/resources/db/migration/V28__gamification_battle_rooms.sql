-- Gamification Phase 4b: Battle Rooms - live, multiplayer (2-5 students), fastest-buzz-first quiz
-- battles, scoped to one class (any section) + one subject. Fills the gap Phase 4a (Arena) left
-- deliberately open: async 1v1 only, no live/synchronized session.
--
-- question_ids follows the exact same fixed, comma-joined convention as quiz_challenge.question_ids
-- (picked once when the room activates, from the same quiz_question bank Arena uses).
--
-- battle_buzz_winner's UNIQUE(room_id, question_index) constraint *is* the fairness mechanism:
-- every buzz is a plain INSERT attempt: whichever commits first wins the race, everyone else's
-- insert fails the constraint and is simply told "too late" - no in-memory locking needed.
CREATE TABLE battle_room (
    id UUID PRIMARY KEY,
    school_id UUID NOT NULL,
    class_name VARCHAR(100) NOT NULL,
    academic_year VARCHAR(20) NOT NULL,
    subject_id UUID NOT NULL,
    created_by_student_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL,
    min_players INT NOT NULL,
    max_players INT NOT NULL,
    join_window_seconds INT NOT NULL,
    question_count INT NOT NULL,
    question_ids VARCHAR(500),
    current_question_index INT NOT NULL DEFAULT 0,
    question_started_at TIMESTAMP,
    winner_student_id UUID,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_battle_room_subject FOREIGN KEY (subject_id) REFERENCES subject(id),
    CONSTRAINT fk_battle_room_school FOREIGN KEY (school_id) REFERENCES school(id)
);

-- Matchmaking lookup: "find an open room for this class+subject" (auto-match) and the scheduled
-- sweep's "find WAITING rooms whose join window has elapsed" / "find ACTIVE rooms to tick".
CREATE INDEX idx_battle_room_match ON battle_room(school_id, class_name, academic_year, subject_id, status);
CREATE INDEX idx_battle_room_status ON battle_room(status, created_at);

CREATE TABLE battle_room_participant (
    id UUID PRIMARY KEY,
    school_id UUID NOT NULL,
    room_id UUID NOT NULL,
    student_id UUID NOT NULL,
    correct_count INT NOT NULL DEFAULT 0,
    joined_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_battle_room_participant UNIQUE (room_id, student_id),
    CONSTRAINT fk_battle_room_participant_room FOREIGN KEY (room_id) REFERENCES battle_room(id),
    CONSTRAINT fk_battle_room_participant_school FOREIGN KEY (school_id) REFERENCES school(id)
);

CREATE INDEX idx_battle_room_participant_room ON battle_room_participant(room_id);

CREATE TABLE battle_buzz_winner (
    id UUID PRIMARY KEY,
    school_id UUID NOT NULL,
    room_id UUID NOT NULL,
    question_index INT NOT NULL,
    student_id UUID NOT NULL,
    buzzed_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_battle_buzz_winner UNIQUE (room_id, question_index),
    CONSTRAINT fk_battle_buzz_winner_room FOREIGN KEY (room_id) REFERENCES battle_room(id),
    CONSTRAINT fk_battle_buzz_winner_school FOREIGN KEY (school_id) REFERENCES school(id)
);

CREATE TABLE battle_answer (
    id UUID PRIMARY KEY,
    school_id UUID NOT NULL,
    room_id UUID NOT NULL,
    question_index INT NOT NULL,
    student_id UUID NOT NULL,
    selected_option VARCHAR(1) NOT NULL,
    correct BOOLEAN NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_battle_answer UNIQUE (room_id, question_index),
    CONSTRAINT fk_battle_answer_room FOREIGN KEY (room_id) REFERENCES battle_room(id),
    CONSTRAINT fk_battle_answer_school FOREIGN KEY (school_id) REFERENCES school(id)
);
