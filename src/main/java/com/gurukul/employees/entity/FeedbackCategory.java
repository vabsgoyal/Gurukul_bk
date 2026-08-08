package com.gurukul.employees.entity;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Category of feedback given about an employee")
public enum FeedbackCategory {

	@Schema(description = "Quality of teaching/instruction")
	TEACHING_QUALITY,

	@Schema(description = "Classroom discipline and management")
	DISCIPLINE,

	@Schema(description = "Timeliness and attendance")
	PUNCTUALITY,

	@Schema(description = "Feedback received from a parent")
	PARENT_FEEDBACK,

	@Schema(description = "Feedback from a peer colleague")
	PEER_REVIEW,

	@Schema(description = "Anything not covered by the other categories")
	OTHER

}
