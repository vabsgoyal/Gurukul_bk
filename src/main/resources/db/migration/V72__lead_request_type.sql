-- The marketing site now takes two kinds of request through the same form endpoint: a demo of the
-- school app (every row before this migration) and custom website services for a school.
ALTER TABLE demo_lead ADD COLUMN request_type VARCHAR(30) NOT NULL DEFAULT 'DEMO';
ALTER TABLE demo_lead ADD COLUMN services VARCHAR(300);
ALTER TABLE demo_lead ADD COLUMN budget VARCHAR(40);
