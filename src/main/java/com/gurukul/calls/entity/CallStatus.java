package com.gurukul.calls.entity;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "CallStatus", description = "Lifecycle status of a scheduled call")
public enum CallStatus {

	@Schema(description = "Created, waiting for its scheduled time")
	SCHEDULED,

	@Schema(description = "Host has started the call")
	STARTED,

	@Schema(description = "Call finished normally")
	COMPLETED,

	@Schema(description = "Cancelled by the host before it started")
	CANCELLED,

	@Schema(description = "Scheduled time plus grace period passed with no start")
	EXPIRED

}
