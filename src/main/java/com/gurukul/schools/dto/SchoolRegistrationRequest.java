package com.gurukul.schools.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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

	@NotBlank
	@Schema(description = "Phone number for the Principal's login (OTP-based login uses this)", example = "9876543210")
	private String principalPhone;

	@Schema(description = "Optional Principal username for password-based login; defaults to principalPhone if omitted")
	private String principalUsername;

	@Schema(description = "Optional Principal password for password-based login; if omitted, only OTP login works for the Principal")
	private String principalPassword;

	@NotBlank
	@Schema(description = "Phone number for the Admin (director)'s login (OTP-based login uses this)", example = "9876500000")
	private String adminPhone;

	@Schema(description = "Optional Admin username for password-based login; defaults to adminPhone if omitted")
	private String adminUsername;

	@Schema(description = "Optional Admin password for password-based login; if omitted, only OTP login works for the Admin")
	private String adminPassword;

	@Min(-90) @Max(90)
	@Schema(description = "Latitude of the school, for the teacher self-attendance geofence - optional, can be set/edited later via PUT /api/v1/schools/{id}/location", example = "26.9124")
	private Double latitude;

	@Min(-180) @Max(180)
	@Schema(description = "Longitude of the school, for the teacher self-attendance geofence - optional, can be set/edited later", example = "75.7873")
	private Double longitude;

	@Min(1)
	@Schema(description = "Geofence radius in meters for teacher self-attendance; defaults to the School entity's default (100) if omitted", example = "100")
	private Integer geofenceRadiusMeters;

}
