package com.gurukul.attendance.service;

import com.gurukul.attendance.entity.AttendanceDevice;
import com.gurukul.attendance.entity.AttendanceRecord;
import com.gurukul.attendance.entity.StaffAttendanceRecord;
import com.gurukul.attendance.repository.AttendanceRecordRepository;
import com.gurukul.attendance.repository.StaffAttendanceRecordRepository;
import com.gurukul.auth.entity.Role;
import com.gurukul.auth.security.AuthPrincipal;
import com.gurukul.employees.entity.Employee;
import com.gurukul.students.entity.ClassSection;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds the admin attendance spreadsheet (.xlsx). Read-only: never touches the records it exports.
 * "Marked At" is the record's createdAt (first marked) and "Last Updated At" its updatedAt (last
 * changed), both rendered in the school's time zone.
 */
@Service
@RequiredArgsConstructor
public class AttendanceExportService {

	public enum ExportType {
		STUDENT,
		STAFF
	}

	static final long MAX_RANGE_DAYS = 366;

	private static final ZoneId SCHOOL_ZONE = ZoneId.of("Asia/Kolkata");
	private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(SCHOOL_ZONE);
	private static final Pattern LEADING_NUMBER = Pattern.compile("^(\\d+)");

	static final List<String> STUDENT_HEADERS = List.of(
			"Date", "Class-Section", "Roll No", "Student Name", "Status", "Method",
			"Marked By", "Marked At", "Last Updated At", "Remarks");
	static final List<String> STAFF_HEADERS = List.of(
			"Date", "Name", "Designation", "Status", "Method", "Self-Marked",
			"Marked By", "Marked At", "Last Updated At", "Latitude", "Longitude", "Remarks");

	private final AttendanceRecordRepository attendanceRecordRepository;
	private final StaffAttendanceRecordRepository staffAttendanceRecordRepository;

	@Transactional(readOnly = true)
	public byte[] export(AuthPrincipal principal, ExportType type, LocalDate from, LocalDate to, UUID sectionId) {
		if (principal.getRole() != Role.ADMIN) {
			throw new AccessDeniedException("Only an admin can export attendance");
		}
		if (to.isBefore(from)) {
			throw new IllegalArgumentException("'to' date must not be before 'from' date");
		}
		if (ChronoUnit.DAYS.between(from, to) >= MAX_RANGE_DAYS) {
			throw new IllegalArgumentException("Date range must be at most " + MAX_RANGE_DAYS + " days");
		}
		UUID schoolId = principal.getSchoolId();

		try (SXSSFWorkbook workbook = new SXSSFWorkbook(100)) {
			CellStyle headerStyle = headerStyle(workbook);
			if (type == ExportType.STUDENT) {
				List<AttendanceRecord> records = sectionId != null
						? attendanceRecordRepository.findAllBySchoolIdAndSectionIdAndAttendanceDateBetween(schoolId, sectionId, from, to)
						: attendanceRecordRepository.findAllBySchoolIdAndAttendanceDateBetween(schoolId, from, to);
				writeStudentSheet(workbook, headerStyle, records);
			} else {
				writeStaffSheet(workbook, headerStyle,
						staffAttendanceRecordRepository.findAllBySchoolIdAndAttendanceDateBetween(schoolId, from, to));
			}
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			workbook.write(out);
			return out.toByteArray();
		} catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	private void writeStudentSheet(SXSSFWorkbook workbook, CellStyle headerStyle, List<AttendanceRecord> records) {
		SXSSFSheet sheet = workbook.createSheet("Student Attendance");
		writeHeader(sheet, headerStyle, STUDENT_HEADERS);
		List<AttendanceRecord> sorted = records.stream()
				.sorted(Comparator.comparing(AttendanceRecord::getAttendanceDate)
						.thenComparing(r -> sectionLabel(r.getSection()))
						.thenComparing(r -> r.getStudent().getRollNumber(), AttendanceExportService::compareRollNumbers)
						.thenComparing(r -> r.getStudent().getName(), Comparator.nullsLast(Comparator.naturalOrder())))
				.toList();
		int rowIndex = 1;
		for (AttendanceRecord r : sorted) {
			writeRow(sheet.createRow(rowIndex++),
					r.getAttendanceDate().toString(),
					sectionLabel(r.getSection()),
					r.getStudent().getRollNumber(),
					r.getStudent().getName(),
					r.getStatus().name(),
					r.getMethod() != null ? r.getMethod().name() : "MANUAL",
					markedBy(r.getMarkedByTeacher(), r.getMarkedByDevice(), false),
					timestamp(r.getCreatedAt()),
					timestamp(r.getUpdatedAt()),
					r.getRemarks());
		}
		sizeColumns(sheet, STUDENT_HEADERS.size());
	}

	private void writeStaffSheet(SXSSFWorkbook workbook, CellStyle headerStyle, List<StaffAttendanceRecord> records) {
		SXSSFSheet sheet = workbook.createSheet("Staff Attendance");
		writeHeader(sheet, headerStyle, STAFF_HEADERS);
		List<StaffAttendanceRecord> sorted = records.stream()
				.sorted(Comparator.comparing(StaffAttendanceRecord::getAttendanceDate)
						.thenComparing(r -> r.getEmployee().getName(), Comparator.nullsLast(Comparator.naturalOrder())))
				.toList();
		int rowIndex = 1;
		for (StaffAttendanceRecord r : sorted) {
			writeRow(sheet.createRow(rowIndex++),
					r.getAttendanceDate().toString(),
					r.getEmployee().getName(),
					r.getEmployee().getDesignation(),
					r.getStatus().name(),
					r.getMethod() != null ? r.getMethod().name() : "MANUAL",
					r.isSelfMarked() ? "Yes" : "No",
					markedBy(r.getMarkedByEmployee(), r.getMarkedByDevice(), r.isSelfMarked()),
					timestamp(r.getCreatedAt()),
					timestamp(r.getUpdatedAt()),
					r.getMarkedLatitude() != null ? r.getMarkedLatitude().toString() : null,
					r.getMarkedLongitude() != null ? r.getMarkedLongitude().toString() : null,
					r.getRemarks());
		}
		sizeColumns(sheet, STAFF_HEADERS.size());
	}

	private static String markedBy(Employee employee, AttendanceDevice device, boolean selfMarked) {
		if (selfMarked) {
			return "Self";
		}
		if (device != null) {
			return "Device: " + device.getName();
		}
		return employee != null ? employee.getName() : null;
	}

	private static String sectionLabel(ClassSection section) {
		if (section == null) {
			return "";
		}
		return section.getSection() == null || section.getSection().isBlank()
				? section.getClassName()
				: section.getClassName() + "-" + section.getSection();
	}

	/** Numeric-aware: "2" before "10"; non-numeric roll numbers sort after numeric ones, then as text. */
	static int compareRollNumbers(String a, String b) {
		Long na = leadingNumber(a);
		Long nb = leadingNumber(b);
		if (na != null && nb != null && !na.equals(nb)) {
			return Long.compare(na, nb);
		}
		if (na != null && nb == null) {
			return -1;
		}
		if (na == null && nb != null) {
			return 1;
		}
		return Comparator.nullsLast(Comparator.<String>naturalOrder()).compare(a, b);
	}

	private static Long leadingNumber(String value) {
		if (value == null) {
			return null;
		}
		Matcher m = LEADING_NUMBER.matcher(value.trim());
		return m.find() ? Long.parseLong(m.group(1)) : null;
	}

	private static String timestamp(Instant instant) {
		return instant != null ? TIMESTAMP.format(instant) : null;
	}

	private static CellStyle headerStyle(SXSSFWorkbook workbook) {
		Font bold = workbook.createFont();
		bold.setBold(true);
		CellStyle style = workbook.createCellStyle();
		style.setFont(bold);
		return style;
	}

	private static void writeHeader(SXSSFSheet sheet, CellStyle style, List<String> headers) {
		Row row = sheet.createRow(0);
		for (int i = 0; i < headers.size(); i++) {
			row.createCell(i).setCellValue(headers.get(i));
			row.getCell(i).setCellStyle(style);
		}
		sheet.createFreezePane(0, 1);
	}

	private static void writeRow(Row row, String... values) {
		for (int i = 0; i < values.length; i++) {
			row.createCell(i).setCellValue(values[i] != null ? values[i] : "");
		}
	}

	/**
	 * Fixed widths rather than autoSizeColumn(): auto-sizing needs AWT font metrics, which fail on a
	 * headless server JRE with no fonts installed. Width unit is 1/256th of a character.
	 */
	private static void sizeColumns(Sheet sheet, int columns) {
		for (int i = 0; i < columns; i++) {
			sheet.setColumnWidth(i, 20 * 256);
		}
	}

}
