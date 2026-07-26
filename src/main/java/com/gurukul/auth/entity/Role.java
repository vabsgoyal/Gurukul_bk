package com.gurukul.auth.entity;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Role granted to a logged-in principal")
public enum Role {

	@Schema(description = "School administrator / principal - full access")
	ADMIN,

	@Schema(description = "Teaching staff - restricted to their own assigned section(s)/subject(s)")
	TEACHER,

	@Schema(description = "Student - restricted to their own data")
	STUDENT

}
