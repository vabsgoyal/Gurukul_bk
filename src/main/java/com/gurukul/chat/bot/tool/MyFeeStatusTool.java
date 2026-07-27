package com.gurukul.chat.bot.tool;

import com.anthropic.models.messages.Tool;
import com.gurukul.auth.entity.OwnerType;
import com.gurukul.auth.security.AuthPrincipal;
import com.gurukul.fees.service.FeePaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class MyFeeStatusTool implements BotTool {

	private final FeePaymentService feePaymentService;

	@Override
	public String name() {
		return "get_my_fee_status";
	}

	@Override
	public String description() {
		return "Returns the asking student's own fee assessments: total due, total paid, remaining due, status, "
				+ "and due date for each academic year. Never returns another student's fee information.";
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
		return feePaymentService.listByStudent(principal.getOwnerId());
	}

}
