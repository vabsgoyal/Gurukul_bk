package com.gurukul.teachers.dto;

import com.gurukul.teachers.entity.TeacherClassAssignment;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@AllArgsConstructor
@Schema(description = "Teacher class-section and subject assignment")
public class TeacherAssignmentResponse {

	private UUID id;
	private UUID schoolId;
	private UUID teacherId;
	private UUID classSectionId;
	private String className;
	private String section;
	private String academicYear;
	private String classSectionLabel;
	private String subjectName;
	private String assignmentRole;
	private Instant createdAt;
	private Instant updatedAt;

	public static TeacherAssignmentResponse from(TeacherClassAssignment assignment) {
		return new TeacherAssignmentResponse(
				assignment.getId(),
				assignment.getSchoolId(),
				assignment.getTeacher().getId(),
				assignment.getClassSection().getId(),
				assignment.getClassSection().getClassName(),
				assignment.getClassSection().getSection(),
				assignment.getClassSection().getAcademicYear(),
				assignment.getClassSection().getDisplayLabel(),
				assignment.getSubjectName(),
				assignment.getAssignmentRole().name(),
				assignment.getCreatedAt(),
				assignment.getUpdatedAt()
		);
	}

}
