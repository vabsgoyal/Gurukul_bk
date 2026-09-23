-- Soft-deactivation for schools. An inactive school is hidden from the public school directory and
-- rejected by SchoolContextFilter (so no login or tenant-scoped request works), but none of its data
-- is deleted - reversible by flipping the flag back. There is no hard-delete for schools: 66 tables
-- reference school(id) with no cascade.
ALTER TABLE school ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;

-- Retire the two QA/test tenants on production (QAEmulatorSchool, Test Flow School QA), leaving only
-- the real schools listed. No-op on any database where these ids don't exist.
UPDATE school SET active = FALSE
WHERE id IN ('ff0edfb0-2d7c-46c9-8e3b-e74f643d2012', '324178b2-8ee2-4e59-9d46-48108b8f36da');
