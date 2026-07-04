package com.gurukul.students.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Payload to create a class-section (grade + division + year)")
public class ClassSectionRequest {

	@NotBlank
	@Schema(description = "Class or grade name", example = "Grade 8")
	private String className;

	@NotBlank
	@Schema(description = "Section or division", example = "A")
	private String section;

	@NotBlank
	@Schema(description = "Academic year", example = "2026-27")
	private String academicYear;

}
