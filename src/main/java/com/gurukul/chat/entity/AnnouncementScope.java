package com.gurukul.chat.entity;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Audience of an announcement")
public enum AnnouncementScope {

	@Schema(description = "Visible to the entire school")
	SCHOOL,

	@Schema(description = "Visible to a single class-section's students and teachers")
	CLASS

}
