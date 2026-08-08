package com.gurukul.exams.dto;

import com.gurukul.exams.entity.Assessment;
import com.gurukul.exams.entity.AssessmentType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@AllArgsConstructor
@Schema(description = "Assessment (assignment/quiz/test/exam) record")
public class AssessmentResponse {

	private UUID id;
	private UUID schoolId;
	private UUID sectionId;
	private String className;
	private String section;
	private String academicYear;
	private AssessmentType type;
	private String title;
	private UUID subjectId;
	private String subjectName;
	private String subjectCode;
	private LocalDate assessmentDate;
	private BigDecimal maxMarks;
	private String description;
	private UUID createdByTeacherId;
	private String createdByTeacherName;
	private String term;
	private Instant createdAt;
	private Instant updatedAt;

	public static AssessmentResponse from(Assessment assessment) {
		return new AssessmentResponse(
				assessment.getId(),
				assessment.getSchoolId(),
				assessment.getSection().getId(),
				assessment.getSection().getClassName(),
				assessment.getSection().getSection(),
				assessment.getSection().getAcademicYear(),
				assessment.getType(),
				assessment.getTitle(),
				assessment.getSubject() != null ? assessment.getSubject().getId() : null,
				assessment.getSubject() != null ? assessment.getSubject().getName() : null,
				assessment.getSubject() != null ? assessment.getSubject().getCode() : null,
				assessment.getAssessmentDate(),
				assessment.getMaxMarks(),
				assessment.getDescription(),
				assessment.getCreatedByTeacher() != null ? assessment.getCreatedByTeacher().getId() : null,
				assessment.getCreatedByTeacher() != null ? assessment.getCreatedByTeacher().getName() : null,
				assessment.getTerm(),
				assessment.getCreatedAt(),
				assessment.getUpdatedAt()
		);
	}

}
