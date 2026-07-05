package com.gurukul.fees.dto;

import com.gurukul.fees.entity.FeeStructure;
import com.gurukul.fees.entity.FeeStructureLine;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@AllArgsConstructor
@Schema(description = "Fee structure with line items")
public class FeeStructureResponse {

	private UUID id;
	private UUID schoolId;
	private UUID classSectionId;
	private String className;
	private String section;
	private String academicYear;
	private List<LineResponse> lines;
	private Instant createdAt;
	private Instant updatedAt;

	@Getter
	@AllArgsConstructor
	public static class LineResponse {
		private UUID id;
		private UUID feeCategoryId;
		private String feeCategoryCode;
		private String feeCategoryName;
		private BigDecimal amount;
	}

	public static FeeStructureResponse from(FeeStructure structure, List<FeeStructureLine> lines) {
		List<LineResponse> lineResponses = lines.stream()
				.map(line -> new LineResponse(
						line.getId(),
						line.getFeeCategory().getId(),
						line.getFeeCategory().getCode(),
						line.getFeeCategory().getName(),
						line.getAmount()))
				.toList();

		return new FeeStructureResponse(
				structure.getId(),
				structure.getSchoolId(),
				structure.getClassSection().getId(),
				structure.getClassSection().getClassName(),
				structure.getClassSection().getSection(),
				structure.getAcademicYear(),
				lineResponses,
				structure.getCreatedAt(),
				structure.getUpdatedAt()
		);
	}

}
