package com.gurukul.schools.dto;

import com.gurukul.auth.dto.AuthDtos.LoginResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
@Schema(description = "Registered school plus an auto-issued admin login token")
public class SchoolRegistrationResponse {

	private SchoolResponse school;
	private LoginResponse admin;

}
