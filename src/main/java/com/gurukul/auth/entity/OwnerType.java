package com.gurukul.auth.entity;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "The kind of entity a credential belongs to")
public enum OwnerType {

	@Schema(description = "Owner is an Employee")
	EMPLOYEE,

	@Schema(description = "Owner is a Student")
	STUDENT

}
