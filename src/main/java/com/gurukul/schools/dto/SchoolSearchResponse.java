package com.gurukul.schools.dto;

import com.gurukul.schools.entity.School;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Getter
@AllArgsConstructor
@Schema(description = "Minimal public school listing - deliberately excludes contact/staff details")
public class SchoolSearchResponse {

	@Schema(description = "School UUID - use as X-School-Id header value")
	private UUID id;

	@Schema(description = "Official school name")
	private String name;

	@Schema(description = "City")
	private String city;

	@Schema(description = "State")
	private String state;

	public static SchoolSearchResponse from(School school) {
		return new SchoolSearchResponse(school.getId(), school.getName(), school.getCity(), school.getState());
	}

}
