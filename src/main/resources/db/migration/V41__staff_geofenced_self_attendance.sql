-- Geofenced teacher self-attendance (TASK.md Task 7). A school's location is nullable until an
-- admin configures it via PUT /api/v1/schools/{id}/location - self-mark rejects with a clear
-- "not configured" error rather than silently allowing/denying everyone. Radius defaults to 100m,
-- not the originally-discussed 20m, since real-world outdoor GPS accuracy alone is 5-20m and is
-- worse near buildings - a 20m radius would produce frequent false negatives.
ALTER TABLE school ADD COLUMN latitude DOUBLE PRECISION;
ALTER TABLE school ADD COLUMN longitude DOUBLE PRECISION;
ALTER TABLE school ADD COLUMN geofence_radius_meters INTEGER NOT NULL DEFAULT 100;

-- Records whether a staff_attendance_record came from the teacher's own geofenced check-in
-- (self_marked = true) vs. an admin's bulk entry, plus the coordinates/accuracy that were
-- submitted for that check-in, kept for audit rather than re-derived from marked_by_employee_id
-- (an admin can also self-mark, which would otherwise be indistinguishable from marking someone else).
ALTER TABLE staff_attendance_record ADD COLUMN self_marked BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE staff_attendance_record ADD COLUMN marked_latitude DOUBLE PRECISION;
ALTER TABLE staff_attendance_record ADD COLUMN marked_longitude DOUBLE PRECISION;
ALTER TABLE staff_attendance_record ADD COLUMN marked_accuracy_meters DOUBLE PRECISION;
