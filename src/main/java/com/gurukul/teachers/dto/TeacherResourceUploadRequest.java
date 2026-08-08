package com.gurukul.teachers.dto;

import com.gurukul.teachers.entity.TeacherResourceType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Schema(description = "Metadata for a file-based resource upload (see the file part for the actual file)")
public class TeacherResourceUploadRequest {

	@NotNull
	@Schema(description = "Class-section UUID from GET /api/v1/class-sections")
	private UUID classSectionId;

	@NotBlank
	@Schema(description = "Subject for this resource", example = "Mathematics")
	private String subjectName;

	@NotNull
	@Schema(description = "Resource type", example = "BOOK")
	private TeacherResourceType resourceType;

	@NotBlank
	@Schema(description = "Resource title", example = "NCERT Chapter 3 Practice Notes")
	private String title;

	@NotBlank
	@Schema(description = "Short description for students", example = "Important formulas and solved examples for revision.")
	private String description;

	@Schema(description = "Whether students should cache/download this resource for offline use", example = "true")
	private boolean availableOffline;

}
