package com.gurukul.students.entity;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Lifecycle status of an enrolled student")
public enum StudentStatus {

	@Schema(description = "Currently enrolled and attending")
	ACTIVE,

	@Schema(description = "Graduated or left after completing studies")
	ALUMNI,

	@Schema(description = "Left before completion")
	WITHDRAWN

}
