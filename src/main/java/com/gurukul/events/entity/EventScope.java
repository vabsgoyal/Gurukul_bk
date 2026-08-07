package com.gurukul.events.entity;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Audience of a participation event, same shape as AnnouncementScope")
public enum EventScope {

	@Schema(description = "Visible to the entire school")
	SCHOOL,

	@Schema(description = "Visible to a single class-section")
	CLASS,

	@Schema(description = "Visible to every section of a grade together")
	GRADE

}
