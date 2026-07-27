package com.gurukul.chat.entity;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Whether a message was authored by a human participant or the Helpdesk BOT")
public enum SenderKind {

	HUMAN,

	BOT

}
