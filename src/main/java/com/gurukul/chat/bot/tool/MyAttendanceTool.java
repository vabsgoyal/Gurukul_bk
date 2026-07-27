package com.gurukul.chat.bot.tool;

import com.anthropic.models.messages.Tool;
import com.gurukul.attendance.service.AttendanceService;
import com.gurukul.attendance.service.StaffAttendanceService;
import com.gurukul.auth.entity.OwnerType;
import com.gurukul.auth.security.AuthPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class MyAttendanceTool implements BotTool {

	private final AttendanceService attendanceService;
	private final StaffAttendanceService staffAttendanceService;

	@Override
	public String name() {
		return "get_my_attendance";
	}

	@Override
	public String description() {
		return "Returns the asking user's own attendance history and present/absent/late/half-day counts for an "
				+ "optional date range. Students see their own record; staff see their own staff-attendance "
				+ "record. Never returns another person's attendance.";
	}

	@Override
	public Tool.InputSchema inputSchema() {
		return ToolSchemas.optionalDateRange();
	}

	@Override
	public boolean appliesTo(AuthPrincipal principal) {
		return true;
	}

	@Override
	public Object execute(AuthPrincipal principal, Map<String, Object> input) {
		LocalDate from = parseDate(input.get("from"));
		LocalDate to = parseDate(input.get("to"));
		if (principal.getOwnerType() == OwnerType.STUDENT) {
			return attendanceService.getStudentHistory(principal.getOwnerId(), from, to);
		}
		return staffAttendanceService.getEmployeeHistory(principal.getOwnerId(), from, to);
	}

	private LocalDate parseDate(Object value) {
		if (value == null) {
			return null;
		}
		return LocalDate.parse(value.toString());
	}

}
