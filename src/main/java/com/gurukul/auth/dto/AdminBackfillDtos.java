package com.gurukul.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

public class AdminBackfillDtos {

	@Getter @AllArgsConstructor
	@Schema(description = "A newly-created admin credential - the password is shown here once and cannot be retrieved again")
	public static class BackfilledAdmin {
		private UUID schoolId;
		private String schoolName;
		private String username;
		private String password;
	}

	@Getter @AllArgsConstructor
	@Schema(description = "Result of an admin-backfill run")
	public static class AdminBackfillResponse {
		private int schoolsProcessed;
		private List<BackfilledAdmin> created;
	}

}
