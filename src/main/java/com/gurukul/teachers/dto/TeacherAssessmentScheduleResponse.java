package com.gurukul.teachers.dto;

import com.gurukul.teachers.entity.AssessmentStatus;
import com.gurukul.teachers.entity.AssessmentType;
import com.gurukul.teachers.entity.TeacherAssessmentSchedule;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@AllArgsConstructor
@Schema(description = "Teacher scheduled quiz/test with syllabus")
public class TeacherAssessmentScheduleResponse {

	private UUID id;
	private UUID schoolId;
	private UUID teacherId;
	private String teacherName;
	private UUID classSectionId;
	private String className;
	private String section;
	private String academicYear;
	private String classSectionLabel;
	private String subjectName;
	private AssessmentType assessmentType;
	private String title;
	private LocalDateTime scheduledAt;
	private String syllabus;
	private String instructions;
	private Integer maxMarks;
	private AssessmentStatus status;
	private Instant createdAt;
	private Instant updatedAt;

	public static TeacherAssessmentScheduleResponse from(TeacherAssessmentSchedule schedule) {
		return new TeacherAssessmentScheduleResponse(
				schedule.getId(),
				schedule.getSchoolId(),
				schedule.getTeacher().getId(),
				schedule.getTeacher().getName(),
				schedule.getClassSection().getId(),
				schedule.getClassSection().getClassName(),
				schedule.getClassSection().getSection(),
				schedule.getClassSection().getAcademicYear(),
				schedule.getClassSection().getDisplayLabel(),
				schedule.getSubjectName(),
				schedule.getAssessmentType(),
				schedule.getTitle(),
				schedule.getScheduledAt(),
				schedule.getSyllabus(),
				schedule.getInstructions(),
				schedule.getMaxMarks(),
				schedule.getStatus(),
				schedule.getCreatedAt(),
				schedule.getUpdatedAt()
		);
	}

}
