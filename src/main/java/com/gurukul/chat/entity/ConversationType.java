package com.gurukul.chat.entity;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Kind of 1:1 conversation, or the Helpdesk BOT conversation")
public enum ConversationType {

	@Schema(description = "Staff-to-staff 1:1 conversation")
	STAFF_STAFF,

	@Schema(description = "Staff-to-student/guardian 1:1 conversation")
	STAFF_STUDENT,

	@Schema(description = "A user's private conversation with the Helpdesk BOT - has exactly one human participant")
	BOT

}
