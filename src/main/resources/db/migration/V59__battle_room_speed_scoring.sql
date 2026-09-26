-- Battle rooms switch from fastest-buzz-first (only the buzz winner answers) to everyone-answers:
-- every participant answers each question within the answer window, and a correct answer scores
-- 1-10 points by how fast it came in. So battle_answer holds one row per (room, question, student)
-- instead of per (room, question), and scores are points, not just a correct count.
--
-- battle_buzz_winner is no longer written; it's left in place for the history already in it.
ALTER TABLE battle_answer DROP CONSTRAINT uq_battle_answer;
ALTER TABLE battle_answer ADD CONSTRAINT uq_battle_answer UNIQUE (room_id, question_index, student_id);
ALTER TABLE battle_answer ADD COLUMN points INT NOT NULL DEFAULT 0;
-- Server-measured: question start to the answer's arrival, never client-reported.
ALTER TABLE battle_answer ADD COLUMN response_ms INT NOT NULL DEFAULT 0;

ALTER TABLE battle_room_participant ADD COLUMN points INT NOT NULL DEFAULT 0;
