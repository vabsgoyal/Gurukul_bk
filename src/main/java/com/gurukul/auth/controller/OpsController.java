package com.gurukul.auth.controller;

import com.gurukul.auth.dto.AdminBackfillDtos.AdminBackfillResponse;
import com.gurukul.auth.service.AdminBackfillService;
import com.gurukul.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Ops", description = "Internal operations endpoints, gated by a shared secret (X-Ops-Key header), not user auth.")
public class OpsController {

	private final AdminBackfillService adminBackfillService;

	@PostMapping("/api/v1/ops/admin-backfill")
	@Operation(
			summary = "Backfill an ADMIN credential for every school that doesn't have one yet",
			description = """
					Ops-only, requires an X-Ops-Key header matching the app.ops.backfill-key secret - not gated
					by user auth, since a school with no existing ADMIN credential has nobody who could log in
					to call a normally-gated endpoint. Safe to call repeatedly - skips schools that already have
					an ADMIN. Generated passwords are returned once in the response and never retrievable again;
					capture and distribute them securely immediately.
					"""
	)
	public ApiResponse<AdminBackfillResponse> backfillAdmins(@RequestHeader("X-Ops-Key") String opsKey) {
		return ApiResponse.success(adminBackfillService.backfill(opsKey), "Admin backfill complete");
	}

}
