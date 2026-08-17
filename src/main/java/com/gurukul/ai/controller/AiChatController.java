package com.gurukul.ai.controller;

import com.gurukul.ai.dto.AiDtos.AiChatRequest;
import com.gurukul.ai.dto.AiDtos.AiChatResponse;
import com.gurukul.ai.service.AiChatService;
import com.gurukul.auth.security.AuthContext;
import com.gurukul.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
@Tag(name = "AI", description = "Academic Helper - open-ended tutoring for students and teaching "
		+ "support for staff, answered by a large language model. Distinct from the Helpdesk bot "
		+ "(/api/v1/chat/bot/conversation), which answers questions about the caller's own attendance, "
		+ "fees, and subjects using tools; this one has no access to school data. Requires "
		+ "authentication and the X-School-Id header.")
public class AiChatController {

	private final AiChatService aiChatService;

	@PostMapping("/chat")
	@Operation(summary = "Ask the Academic Helper a question. The caller's role decides which system "
			+ "prompt is used (students are taught the method rather than handed answers; teachers get "
			+ "full answer keys) - the client cannot select it, nor the model.")
	public ApiResponse<AiChatResponse> chat(@Valid @RequestBody AiChatRequest request) {
		return ApiResponse.success(aiChatService.chat(AuthContext.current(), request));
	}

}
