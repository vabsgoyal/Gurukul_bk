package com.gurukul.students.dto;

import com.gurukul.students.entity.ClassSection;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Getter
@AllArgsConstructor
@Schema(description = "Class-section (grade + division) for enrollment dropdowns")
public class ClassSectionResponse {

	@Schema(description = "Class-section UUID")
	private UUID id;

	@Schema(description = "School (tenant) UUID")
	private UUID schoolId;

	@Schema(description = "Class or grade name", example = "Grade 8")
	private String className;

	@Schema(description = "Section or division", example = "A")
	private String section;

	@Schema(description = "Academic year", example = "2026-27")
	private String academicYear;

	@Schema(description = "Human-readable label", example = "Grade 8 - A (2026-27)")
	private String displayLabel;

	@Schema(description = "This section's assigned class teacher, if any - null until assigned via PATCH .../class-teacher")
	private UUID classTeacherId;

	@Schema(description = "Class teacher's name, for display - null when classTeacherId is null")
	private String classTeacherName;

	public static ClassSectionResponse from(ClassSection classSection) {
		return new ClassSectionResponse(
				classSection.getId(),
				classSection.getSchoolId(),
				classSection.getClassName(),
				classSection.getSection(),
				classSection.getAcademicYear(),
				classSection.getDisplayLabel(),
				classSection.getClassTeacher() != null ? classSection.getClassTeacher().getId() : null,
				classSection.getClassTeacher() != null ? classSection.getClassTeacher().getName() : null
		);
	}

}
