package com.gurukul.students.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Schema(description = """
		Bulk student enrollment payload, for onboarding a school's existing roster in one call instead
		of one request per student. Deliberately does NOT cascade bean validation into each row here -
		a malformed row is reported per-row in the response instead of rejecting the whole batch, since
		a real register (see: this feature's origin) commonly has a handful of incomplete rows among
		hundreds of good ones.
		""")
public class StudentBulkImportRequest {

	@NotEmpty
	@Schema(description = "One entry per student, same shape as POST /api/v1/students")
	private List<StudentRequest> students;

}
