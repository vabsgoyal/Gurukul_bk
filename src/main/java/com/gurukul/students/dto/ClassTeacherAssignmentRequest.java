package com.gurukul.students.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Schema(description = "Assign a class teacher to a class-section")
public class ClassTeacherAssignmentRequest {

	@NotNull
	@Schema(description = "Employee UUID to assign as class teacher")
	private UUID teacherId;

}
