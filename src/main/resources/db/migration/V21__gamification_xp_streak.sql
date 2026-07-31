-- Gamification Phase 1: XP ledger + a maintained per-student profile snapshot (total XP,
-- current/longest streak). Streak continuity is derived from attendance_record rows directly -
-- a school day only ever gets a row when a teacher actually marks that section's roster, so the
-- existing attendance data already encodes "which days were school days" with no separate
-- calendar concept needed.
CREATE TABLE xp_event (
    id UUID PRIMARY KEY,
    school_id UUID NOT NULL,
    student_id UUID NOT NULL,
    source VARCHAR(20) NOT NULL,
    amount INT NOT NULL,
    related_date DATE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_xp_event_school FOREIGN KEY (school_id) REFERENCES school(id)
);

CREATE INDEX idx_xp_event_student ON xp_event(school_id, student_id);
-- Fast idempotency lookup for recordAttendanceXp's "already awarded for this date" check.
CREATE INDEX idx_xp_event_student_source_date ON xp_event(student_id, source, related_date);

CREATE TABLE student_game_profile (
    id UUID PRIMARY KEY,
    school_id UUID NOT NULL,
    student_id UUID NOT NULL,
    total_xp BIGINT NOT NULL DEFAULT 0,
    current_streak_days INT NOT NULL DEFAULT 0,
    longest_streak_days INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_student_game_profile UNIQUE (school_id, student_id),
    CONSTRAINT fk_student_game_profile_school FOREIGN KEY (school_id) REFERENCES school(id)
);
