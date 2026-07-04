package com.gurukul.students.entity;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Student gender")
public enum Gender {

	@Schema(description = "Male")
	MALE,

	@Schema(description = "Female")
	FEMALE,

	@Schema(description = "Other or prefer not to say")
	OTHER

}
