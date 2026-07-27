-- scope = SCHOOL: section_id is NULL, visible to the entire school.
-- scope = CLASS: section_id is NOT NULL, visible to that section's students + teachers.
-- Announcements are staff-authored only, so author_employee_id is a plain FK (no polymorphic owner
-- needed here, unlike conversation participants/senders).
CREATE TABLE announcement (
    id UUID PRIMARY KEY,
    school_id UUID NOT NULL,
    scope VARCHAR(10) NOT NULL,
    section_id UUID,
    author_employee_id UUID NOT NULL,
    title VARCHAR(200) NOT NULL,
    body TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_announcement_school FOREIGN KEY (school_id) REFERENCES school(id),
    CONSTRAINT fk_announcement_section FOREIGN KEY (section_id) REFERENCES class_section(id),
    CONSTRAINT fk_announcement_author FOREIGN KEY (author_employee_id) REFERENCES employee(id),
    CONSTRAINT chk_announcement_scope CHECK (
        (scope = 'SCHOOL' AND section_id IS NULL)
        OR (scope = 'CLASS' AND section_id IS NOT NULL)
    )
);

-- "School's announcements" feed (scope = SCHOOL), newest first.
CREATE INDEX idx_announcement_school_scope ON announcement(school_id, scope, created_at);

-- "Section's announcements" feed (scope = CLASS), newest first.
CREATE INDEX idx_announcement_section ON announcement(section_id, created_at);
