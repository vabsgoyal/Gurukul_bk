package com.gurukul.calls.entity;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "CallOutcome", description = "How a call session ended")
public enum CallOutcome {

	@Schema(description = "Still ringing / in progress")
	IN_PROGRESS,

	@Schema(description = "Both parties connected and the call finished normally")
	COMPLETED,

	@Schema(description = "Callee never answered within the timeout")
	MISSED,

	@Schema(description = "Callee explicitly declined")
	DECLINED,

	@Schema(description = "Callee was already on another call")
	BUSY,

	@Schema(description = "Caller cancelled before the callee responded")
	CANCELLED

}
