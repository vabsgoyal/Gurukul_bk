-- Adds a GRADE scope to announcements: visible to every section of a class-name (e.g. "Grade 8"
-- A/B/C together), not just one specific section. The existing CLASS scope is genuinely
-- section-level (tied to one class_section row) - GRADE fills the gap for a teacher who wants to
-- reach a whole grade without posting to each section individually. Matches the same
-- className-across-sections pattern already established by battle_room/quiz_question rather than
-- introducing a new concept.
ALTER TABLE announcement ADD COLUMN class_name VARCHAR(100);

ALTER TABLE announcement DROP CONSTRAINT chk_announcement_scope;
ALTER TABLE announcement ADD CONSTRAINT chk_announcement_scope CHECK (
    (scope = 'SCHOOL' AND section_id IS NULL AND class_name IS NULL)
    OR (scope = 'CLASS' AND section_id IS NOT NULL AND class_name IS NULL)
    OR (scope = 'GRADE' AND section_id IS NULL AND class_name IS NOT NULL)
);

-- "Grade's announcements" feed (scope = GRADE), newest first.
CREATE INDEX idx_announcement_grade ON announcement(school_id, class_name, created_at);
