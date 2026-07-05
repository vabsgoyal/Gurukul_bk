package com.gurukul.vendors.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Vendor create/update payload")
public class VendorRequest {

	@NotBlank
	@Schema(description = "Vendor name", example = "ABC Supplies")
	private String name;

	@Schema(description = "Contact phone", example = "9876543210")
	private String contactPhone;

	@Schema(description = "Contact email", example = "vendor@example.com")
	private String contactEmail;

	@Schema(description = "Bank account number")
	private String bankAccount;

	@Schema(description = "UPI ID")
	private String upiId;

	@Schema(description = "Vendor address")
	private String address;

}
