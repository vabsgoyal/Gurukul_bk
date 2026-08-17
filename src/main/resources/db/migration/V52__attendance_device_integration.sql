-- RFID/biometric/face-recognition hardware integration for attendance (FEATURE_ADOPTION_PLAN.md #11).
-- A physical device at a school gate/classroom door reports a scan event to
-- POST /api/v1/attendance/device-events; the backend resolves the device's reported external id to a
-- pre-enrolled student/employee and marks attendance automatically - a third attendance source
-- alongside teacher-marked (attendance_record, existing) and self-marked/admin-marked
-- (staff_attendance_record, Task 7). The actual biometric/face matching happens on the vendor's own
-- device/SDK, never in this backend - we only ever receive an already-resolved external id. Unlike
-- Task 7's self-mark, no GPS/geofence check applies here: the device itself is bolted to the school
-- premises, so a successful scan already implies on-site presence.

CREATE TABLE attendance_device (
    id UUID PRIMARY KEY,
    school_id UUID NOT NULL,
    name VARCHAR(200) NOT NULL,
    device_type VARCHAR(20) NOT NULL,
    api_key_hash VARCHAR(200) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    last_seen_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_attendance_device_school FOREIGN KEY (school_id) REFERENCES school(id)
);

-- Maps an external identifier a device reports (an RFID card UID, a fingerprint template id, or a
-- face-recognition vendor's subject id) to the student/employee it belongs to. Uniqueness among
-- active rows (one identifier isn't shared by two people; one person has at most one active
-- identifier per method) is enforced in the service layer, not a DB constraint - a partial/filtered
-- unique index would need to behave identically on both H2 (used by the test suite) and Postgres
-- (production), which isn't guaranteed, whereas an application-level check (same pattern already
-- used by CredentialService for username uniqueness) is portable and this table's write volume is
-- low (admin enrollment, not a hot path).
CREATE TABLE attendance_identifier (
    id UUID PRIMARY KEY,
    school_id UUID NOT NULL,
    owner_type VARCHAR(20) NOT NULL,
    owner_id UUID NOT NULL,
    method VARCHAR(20) NOT NULL,
    external_id VARCHAR(200) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_attendance_identifier_school FOREIGN KEY (school_id) REFERENCES school(id)
);

CREATE INDEX idx_attendance_identifier_external ON attendance_identifier(school_id, method, external_id);
CREATE INDEX idx_attendance_identifier_owner ON attendance_identifier(school_id, owner_type, owner_id);

-- Both "marked by a human" columns become nullable - a device event has no human marker at all.
-- marked_by_device_id/method are additive, matching how Task 7 added self_marked/lat/lng: existing
-- teacher-marked and self-marked rows are unaffected (both new columns stay null for them).
ALTER TABLE attendance_record ALTER COLUMN marked_by_teacher_id DROP NOT NULL;
ALTER TABLE attendance_record ADD COLUMN marked_by_device_id UUID REFERENCES attendance_device(id);
ALTER TABLE attendance_record ADD COLUMN method VARCHAR(20);

ALTER TABLE staff_attendance_record ALTER COLUMN marked_by_employee_id DROP NOT NULL;
ALTER TABLE staff_attendance_record ADD COLUMN marked_by_device_id UUID REFERENCES attendance_device(id);
ALTER TABLE staff_attendance_record ADD COLUMN method VARCHAR(20);
