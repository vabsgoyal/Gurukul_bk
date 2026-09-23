package com.gurukul.attendance.controller;

import com.gurukul.attendance.service.AttendanceExportService;
import com.gurukul.attendance.service.AttendanceExportService.ExportType;
import com.gurukul.auth.security.AuthContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Attendance")
public class AttendanceExportController {

	static final MediaType XLSX = MediaType.parseMediaType(
			"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

	private final AttendanceExportService attendanceExportService;

	@GetMapping("/api/v1/attendance/export")
	@Operation(summary = "Export attendance as an .xlsx spreadsheet (admin only)",
			description = "type=STUDENT (optionally one sectionId) or STAFF, for an inclusive from/to date range of at "
					+ "most 366 days. Includes when each record was first marked and last updated (Asia/Kolkata).")
	public ResponseEntity<byte[]> export(
			@RequestParam ExportType type,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(required = false) UUID sectionId) {
		byte[] file = attendanceExportService.export(AuthContext.current(), type, from, to, sectionId);
		String filename = "attendance-%s-%s-to-%s.xlsx".formatted(type.name().toLowerCase(), from, to);
		return ResponseEntity.ok()
				.contentType(XLSX)
				.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
				.body(file);
	}

}
