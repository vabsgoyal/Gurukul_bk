package com.gurukul.calls.entity;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "RsvpStatus", description = "An invitee's response to a scheduled call")
public enum RsvpStatus {

	@Schema(description = "Invitee has not responded yet")
	PENDING,

	@Schema(description = "Invitee will join")
	ACCEPTED,

	@Schema(description = "Invitee will not join")
	DECLINED

}
