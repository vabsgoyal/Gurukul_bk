package com.gurukul.teachers.dto;

import com.gurukul.teachers.entity.TeacherAssignmentRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Schema(description = "Assign a teacher to a class-section and subject")
public class TeacherAssignmentRequest {

	@NotNull
	@Schema(description = "Class-section UUID from GET /api/v1/class-sections")
	private UUID classSectionId;

	@NotBlank
	@Schema(description = "Subject handled by the teacher", example = "Mathematics")
	private String subjectName;

	@NotNull
	@Schema(description = "Teacher responsibility", example = "SUBJECT_TEACHER")
	private TeacherAssignmentRole assignmentRole;

}
