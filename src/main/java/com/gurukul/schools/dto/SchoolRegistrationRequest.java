package com.gurukul.schools.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Register a new school (tenant organization)")
public class SchoolRegistrationRequest {

	@NotBlank
	@Schema(description = "Official school name", example = "Delhi Public School")
	private String name;

	@NotBlank
	@Schema(description = "Street / building address", example = "45 Ring Road")
	private String address;

	@NotBlank
	@Schema(description = "City", example = "Jaipur")
	private String city;

	@NotBlank
	@Schema(description = "State", example = "Rajasthan")
	private String state;

	@NotBlank
	@Schema(description = "Postal pincode", example = "302001")
	private String pincode;

	@NotBlank
	@Email
	@Schema(description = "Primary contact email", example = "admin@dps.example")
	private String contactEmail;

	@NotBlank
	@Schema(description = "Primary contact phone", example = "9876543210")
	private String contactPhone;

	@NotBlank
	@Schema(description = "Principal full name", example = "Dr. Anita Verma")
	private String principalName;

	@NotBlank
	@Schema(description = "Director full name", example = "Mr. Sanjay Mehta")
	private String directorName;

}
