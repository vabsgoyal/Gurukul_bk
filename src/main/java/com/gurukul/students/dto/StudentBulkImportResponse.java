package com.gurukul.students.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

@Getter
@AllArgsConstructor
@Schema(description = "Result of a bulk student enrollment - per-row outcome, since a batch this size commonly has some rows that fail")
public class StudentBulkImportResponse {

	@Schema(description = "Rows submitted")
	private int total;

	@Schema(description = "Rows successfully enrolled")
	private int succeeded;

	@Schema(description = "Rows that failed - see results for why")
	private int failed;

	@Schema(description = "One entry per submitted row, in the same order as the request")
	private List<RowResult> results;

	@Getter
	@AllArgsConstructor
	@Schema(description = "Outcome for a single row of a bulk import")
	public static class RowResult {

		@Schema(description = "0-based index of this row in the submitted list")
		private int index;

		@Schema(description = "Student name as submitted, for identifying the row in a large batch")
		private String name;

		@Schema(description = "Whether this row was enrolled")
		private boolean success;

		@Schema(description = "New student's ID, if successful")
		private UUID studentId;

		@Schema(description = "Why this row failed, if it did")
		private String error;

		public static RowResult success(int index, String name, UUID studentId) {
			return new RowResult(index, name, true, studentId, null);
		}

		public static RowResult failure(int index, String name, String error) {
			return new RowResult(index, name, false, null, error);
		}
	}

}
