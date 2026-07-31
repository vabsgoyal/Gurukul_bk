-- Gamification Phase 3: houses (team layer). Automatic XP contribution to a house's total is
-- never stored - it's computed live by summing xp_event for the house's members, same pattern
-- as Phase 2's live weekly league XP. Only teacher-awarded "spot recognition" is persisted here.
CREATE TABLE house (
    id UUID PRIMARY KEY,
    school_id UUID NOT NULL,
    name VARCHAR(80) NOT NULL,
    color_hex VARCHAR(7) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_house_name UNIQUE (school_id, name),
    CONSTRAINT fk_house_school FOREIGN KEY (school_id) REFERENCES school(id)
);

CREATE TABLE house_membership (
    id UUID PRIMARY KEY,
    school_id UUID NOT NULL,
    house_id UUID NOT NULL,
    student_id UUID NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_house_membership_student UNIQUE (school_id, student_id),
    CONSTRAINT fk_house_membership_house FOREIGN KEY (house_id) REFERENCES house(id),
    CONSTRAINT fk_house_membership_school FOREIGN KEY (school_id) REFERENCES school(id)
);

CREATE INDEX idx_house_membership_house ON house_membership(house_id);

CREATE TABLE house_point_event (
    id UUID PRIMARY KEY,
    school_id UUID NOT NULL,
    house_id UUID NOT NULL,
    student_id UUID NOT NULL,
    amount INT NOT NULL,
    reason VARCHAR(300) NOT NULL,
    awarded_by_employee_id UUID NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_house_point_event_house FOREIGN KEY (house_id) REFERENCES house(id),
    CONSTRAINT fk_house_point_event_school FOREIGN KEY (school_id) REFERENCES school(id)
);

CREATE INDEX idx_house_point_event_house ON house_point_event(house_id);
CREATE INDEX idx_house_point_event_school_created ON house_point_event(school_id, created_at);
