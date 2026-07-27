package com.gurukul.chat.bot.tool;

import com.anthropic.models.messages.Tool;
import com.gurukul.academics.service.AcademicsService;
import com.gurukul.auth.entity.OwnerType;
import com.gurukul.auth.security.AuthPrincipal;
import com.gurukul.students.entity.Student;
import com.gurukul.students.service.StudentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class MySubjectsTool implements BotTool {

	private final StudentService studentService;
	private final AcademicsService academicsService;

	@Override
	public String name() {
		return "get_my_subjects";
	}

	@Override
	public String description() {
		return "Returns the asking student's own subjects and the teacher assigned to each, for their current "
				+ "class-section. There is no bell-schedule/timetable data - only subjects and teachers. Never "
				+ "returns another student's or section's subjects.";
	}

	@Override
	public Tool.InputSchema inputSchema() {
		return ToolSchemas.empty();
	}

	@Override
	public boolean appliesTo(AuthPrincipal principal) {
		return principal.getOwnerType() == OwnerType.STUDENT;
	}

	@Override
	public Object execute(AuthPrincipal principal, Map<String, Object> input) {
		Student student = studentService.getScopedEntity(principal.getOwnerId());
		return academicsService.listSectionSubjects(student.getClassSection().getId());
	}

}
