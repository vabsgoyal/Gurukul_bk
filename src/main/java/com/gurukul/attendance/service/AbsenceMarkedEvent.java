package com.gurukul.attendance.service;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Published by AttendanceService for every student saved as ABSENT for today (school time). Handled
 * after the attendance transaction commits (AbsenceAlertService), so a register that fails to save
 * never alerts anyone, and an alert that fails never rolls the register back.
 */
public record AbsenceMarkedEvent(UUID schoolId, UUID studentId, LocalDate date) {
}
