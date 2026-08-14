package com.gurukul.academichelper;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;

import java.util.List;

public class AcademicHelperDtos {

	public record ChatMessageDto(
			@NotBlank @Pattern(regexp = "user|assistant") String role,
			@NotBlank String content) {
	}

	public record AskRequest(
			@NotBlank @Pattern(regexp = "student|teacher") String mode,
			@NotEmpty @Valid List<ChatMessageDto> messages) {
	}

	public record AskResponse(String reply) {
	}

}
