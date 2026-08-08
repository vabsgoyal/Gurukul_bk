package com.gurukul.teachers.dto;

import com.gurukul.teachers.entity.Teacher;
import com.gurukul.teachers.entity.TeacherStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Getter
@AllArgsConstructor
@Schema(description = "Teacher profile returned by the API")
public class TeacherResponse {

	private UUID id;
	private UUID schoolId;
	private String employeeCode;
	private String name;
	private String email;
	private String phone;
	private String qualification;
	private String specialization;
	private LocalDate joiningDate;
	private TeacherStatus status;
	private long assignmentCount;
	private List<TeacherAssignmentResponse> assignments;
	private Instant createdAt;
	private Instant updatedAt;

	public static TeacherResponse from(Teacher teacher, List<TeacherAssignmentResponse> assignments) {
		return new TeacherResponse(
				teacher.getId(),
				teacher.getSchoolId(),
				teacher.getEmployeeCode(),
				teacher.getName(),
				teacher.getEmail(),
				teacher.getPhone(),
				teacher.getQualification(),
				teacher.getSpecialization(),
				teacher.getJoiningDate(),
				teacher.getStatus(),
				assignments.size(),
				assignments,
				teacher.getCreatedAt(),
				teacher.getUpdatedAt()
		);
	}

	public static TeacherResponse summary(Teacher teacher, long assignmentCount) {
		return new TeacherResponse(
				teacher.getId(),
				teacher.getSchoolId(),
				teacher.getEmployeeCode(),
				teacher.getName(),
				teacher.getEmail(),
				teacher.getPhone(),
				teacher.getQualification(),
				teacher.getSpecialization(),
				teacher.getJoiningDate(),
				teacher.getStatus(),
				assignmentCount,
				List.of(),
				teacher.getCreatedAt(),
				teacher.getUpdatedAt()
		);
	}

}
