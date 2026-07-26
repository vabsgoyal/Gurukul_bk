package com.gurukul.schools.dto;

import com.gurukul.auth.dto.AuthDtos.LoginResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
@Schema(description = "Registered school plus auto-issued Principal and Admin login tokens")
public class SchoolRegistrationResponse {

	private SchoolResponse school;
	private LoginResponse principal;
	private LoginResponse admin;

}
