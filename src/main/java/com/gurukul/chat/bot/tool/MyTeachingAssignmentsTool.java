package com.gurukul.chat.bot.tool;

import com.anthropic.models.messages.Tool;
import com.gurukul.academics.service.AcademicsService;
import com.gurukul.auth.entity.OwnerType;
import com.gurukul.auth.security.AuthPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class MyTeachingAssignmentsTool implements BotTool {

	private final AcademicsService academicsService;

	@Override
	public String name() {
		return "get_my_teaching_assignments";
	}

	@Override
	public String description() {
		return "Returns the asking staff member's own section+subject teaching assignments. Never returns "
				+ "another teacher's assignments.";
	}

	@Override
	public Tool.InputSchema inputSchema() {
		return ToolSchemas.empty();
	}

	@Override
	public boolean appliesTo(AuthPrincipal principal) {
		return principal.getOwnerType() == OwnerType.EMPLOYEE;
	}

	@Override
	public Object execute(AuthPrincipal principal, Map<String, Object> input) {
		return academicsService.listAssignmentsForTeacher(principal.getOwnerId());
	}

}
