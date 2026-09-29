-- ID cards (GUR-ID-01): the extra details a person enters on their own profile for their ID card.
-- Kept out of student/employee so admin forms are untouched - admins don't collect these up front.
-- owner_type is STUDENT or EMPLOYEE; owner_id is that student/employee row. photo_object_key is an
-- S3 key under profile-photos/{schoolId}/..., never a URL (a GET url is presigned on every read).
CREATE TABLE id_card_profile (
    id UUID PRIMARY KEY,
    school_id UUID NOT NULL,
    owner_type VARCHAR(20) NOT NULL,
    owner_id UUID NOT NULL,
    photo_object_key VARCHAR(512),
    blood_group VARCHAR(8),
    emergency_contact_name VARCHAR(120),
    emergency_phone VARCHAR(20),
    updated_by VARCHAR(255),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_id_card_profile_owner UNIQUE (school_id, owner_type, owner_id)
);
