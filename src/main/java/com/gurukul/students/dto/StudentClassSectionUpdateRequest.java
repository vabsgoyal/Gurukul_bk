package com.gurukul.students.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Schema(description = "Transfer a student to a different class-section")
public class StudentClassSectionUpdateRequest {

	@NotNull
	@Schema(description = "Target class-section UUID from GET /api/v1/class-sections")
	private UUID classSectionId;

}
