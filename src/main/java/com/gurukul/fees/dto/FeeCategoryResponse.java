package com.gurukul.fees.dto;

import com.gurukul.fees.entity.FeeCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@AllArgsConstructor
@Schema(description = "Fee category record")
public class FeeCategoryResponse {

	private UUID id;
	private UUID schoolId;
	private String code;
	private String name;
	private Instant createdAt;
	private Instant updatedAt;

	public static FeeCategoryResponse from(FeeCategory category) {
		return new FeeCategoryResponse(
				category.getId(),
				category.getSchoolId(),
				category.getCode(),
				category.getName(),
				category.getCreatedAt(),
				category.getUpdatedAt()
		);
	}

}
