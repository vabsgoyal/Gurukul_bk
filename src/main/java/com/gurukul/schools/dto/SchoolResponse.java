package com.gurukul.schools.dto;

import com.gurukul.schools.entity.School;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@AllArgsConstructor
@Schema(description = "Registered school (tenant). Use id as X-School-Id on subsequent API calls.")
public class SchoolResponse {

	@Schema(description = "School UUID — use as X-School-Id header value")
	private UUID id;

	@Schema(description = "Official school name")
	private String name;

	@Schema(description = "Street / building address")
	private String address;

	@Schema(description = "City")
	private String city;

	@Schema(description = "State")
	private String state;

	@Schema(description = "Postal pincode")
	private String pincode;

	@Schema(description = "Primary contact email")
	private String contactEmail;

	@Schema(description = "Primary contact phone")
	private String contactPhone;

	@Schema(description = "Principal full name")
	private String principalName;

	@Schema(description = "Director full name")
	private String directorName;

	@Schema(description = "Live count of enrolled students (computed, not stored)")
	private long studentCount;

	@Schema(description = "Live count of class-sections (computed, not stored)")
	private long classSectionCount;

	@Schema(description = "Live count of teachers (computed; 0 until teachers module exists)")
	private long teacherCount;

	@Schema(description = "When the school was registered")
	private Instant createdAt;

	@Schema(description = "When the school record was last updated")
	private Instant updatedAt;

	public static SchoolResponse from(School school, long studentCount, long classSectionCount, long teacherCount) {
		return new SchoolResponse(
				school.getId(),
				school.getName(),
				school.getAddress(),
				school.getCity(),
				school.getState(),
				school.getPincode(),
				school.getContactEmail(),
				school.getContactPhone(),
				school.getPrincipalName(),
				school.getDirectorName(),
				studentCount,
				classSectionCount,
				teacherCount,
				school.getCreatedAt(),
				school.getUpdatedAt()
		);
	}

}
