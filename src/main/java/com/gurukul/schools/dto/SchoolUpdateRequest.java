package com.gurukul.schools.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Update a school's profile fields")
public class SchoolUpdateRequest {

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

	@Pattern(regexp = "^\\d{9,18}$", message = "Enter a valid bank account number (9-18 digits)")
	@Schema(description = "Bank account number fees are paid into", example = "123456789012")
	private String bankAccountNumber;

	@Pattern(regexp = "^[A-Z]{4}0[A-Z0-9]{6}$", message = "Enter a valid IFSC code, e.g. SBIN0001234")
	@Schema(description = "IFSC code of the fee-receiving bank account", example = "SBIN0001234")
	private String bankIfsc;

	@Schema(description = "Account holder name shown to students when paying; defaults to school name if blank",
			example = "Delhi Public School")
	private String bankAccountHolderName;

	@Pattern(regexp = "^$|^[\\w.-]+@[\\w.-]+$", message = "Enter a valid UPI VPA, e.g. name@oksbi")
	@Schema(description = "Optional real UPI VPA to use as-is for payment intents instead of deriving one "
			+ "from the bank account number/IFSC — for testing against a real account", example = "school@oksbi")
	private String upiVpaOverride;

}
