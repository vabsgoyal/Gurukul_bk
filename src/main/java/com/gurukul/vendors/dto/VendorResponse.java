package com.gurukul.vendors.dto;

import com.gurukul.vendors.entity.Vendor;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@AllArgsConstructor
@Schema(description = "Vendor record")
public class VendorResponse {

	private UUID id;
	private UUID schoolId;
	private String name;
	private String contactPhone;
	private String contactEmail;
	private String bankAccount;
	private String upiId;
	private String address;
	private Instant createdAt;
	private Instant updatedAt;

	public static VendorResponse from(Vendor vendor) {
		return new VendorResponse(
				vendor.getId(),
				vendor.getSchoolId(),
				vendor.getName(),
				vendor.getContactPhone(),
				vendor.getContactEmail(),
				vendor.getBankAccount(),
				vendor.getUpiId(),
				vendor.getAddress(),
				vendor.getCreatedAt(),
				vendor.getUpdatedAt()
		);
	}

}
