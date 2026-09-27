-- Demo requests from the public marketing site (smartgurukul.org). Not school-scoped: these are
-- prospective schools, so there is no school_id / FK. The submitter IP is never stored - only a
-- salted SHA-256 hash, used solely to rate-limit repeat submissions from one address.
CREATE TABLE demo_lead (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    school_name VARCHAR(150) NOT NULL,
    role VARCHAR(60),
    phone VARCHAR(20) NOT NULL,
    email VARCHAR(150),
    city VARCHAR(80),
    state VARCHAR(80),
    student_count VARCHAR(20),
    message VARCHAR(1000),
    source_page VARCHAR(200),
    ip_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_demo_lead_ip_hash_created ON demo_lead (ip_hash, created_at);
CREATE INDEX idx_demo_lead_created ON demo_lead (created_at);
