-- Gamification Phase 2: adds each student's current weekly league tier onto the existing
-- profile snapshot from Phase 1. Weekly XP itself is intentionally not stored anywhere - it's
-- always computed live from xp_event for the current week, so there is no counter to keep in
-- sync when XP is awarded from any source (attendance today, assessments/quizzes later).
ALTER TABLE student_game_profile
    ADD COLUMN current_tier VARCHAR(20) NOT NULL DEFAULT 'BRONZE';
