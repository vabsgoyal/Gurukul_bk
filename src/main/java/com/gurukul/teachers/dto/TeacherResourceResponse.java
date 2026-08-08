package com.gurukul.teachers.dto;

import com.gurukul.teachers.entity.TeacherResource;
import com.gurukul.teachers.entity.TeacherResourceType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@AllArgsConstructor
@Schema(description = "Resource shared by a teacher")
public class TeacherResourceResponse {

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
	private TeacherResourceType resourceType;
	private String title;
	private String description;
	private String resourceUrl;
	private boolean availableOffline;
	private String fileName;
	private String contentType;
	private Long fileSizeBytes;
	private Instant createdAt;
	private Instant updatedAt;

	public static TeacherResourceResponse from(TeacherResource resource) {
		return new TeacherResourceResponse(
				resource.getId(),
				resource.getSchoolId(),
				resource.getTeacher().getId(),
				resource.getTeacher().getName(),
				resource.getClassSection().getId(),
				resource.getClassSection().getClassName(),
				resource.getClassSection().getSection(),
				resource.getClassSection().getAcademicYear(),
				resource.getClassSection().getDisplayLabel(),
				resource.getSubjectName(),
				resource.getResourceType(),
				resource.getTitle(),
				resource.getDescription(),
				resource.getResourceUrl(),
				resource.isAvailableOffline(),
				resource.getFileName(),
				resource.getContentType(),
				resource.getFileSizeBytes(),
				resource.getCreatedAt(),
				resource.getUpdatedAt()
		);
	}

}
