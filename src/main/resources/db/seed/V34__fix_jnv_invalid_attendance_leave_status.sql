-- Same bug class as V33: V25__seed_jnv_full_dataset.sql used 'LEAVE' as an attendance status for
-- both attendance_record and staff_attendance_record, but AttendanceStatus is only
-- PRESENT|ABSENT|LATE|HALF_DAY - there is no "leave" concept anywhere else in this app. Roughly
-- 1-in-37 (student) / 1-in-40 (staff) of the ~260 seeded school-day rows per person hit this, so
-- almost every JNV student/employee has at least one row that crashes their attendance history
-- with "No enum constant ...AttendanceStatus.LEAVE" - this was the actual root cause still
-- breaking Attendance History after V33 (which didn't touch these two tables).
--
-- Scoped by value, not by school_id - see V33's note on why that's safe.
UPDATE attendance_record SET status = 'ABSENT' WHERE status = 'LEAVE';
UPDATE staff_attendance_record SET status = 'ABSENT' WHERE status = 'LEAVE';
