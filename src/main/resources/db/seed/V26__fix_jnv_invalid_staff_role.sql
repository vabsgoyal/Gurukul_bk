-- V25 seeded non-teaching JNV employees' credentials with role='STAFF', but the Role enum
-- (com.gurukul.auth.entity.Role) only defines ADMIN/TEACHER/STUDENT. That invalid value breaks
-- any query that deserializes these credentials (e.g. employee list/role lookups). Since V25 has
-- already run in environments where this was seeded, correct the bad rows here rather than editing
-- the already-applied V25 script.
UPDATE credential
SET role = 'ADMIN', updated_at = CURRENT_TIMESTAMP
WHERE school_id = '99999999-9999-9999-9999-999999999999'
  AND role = 'STAFF';
