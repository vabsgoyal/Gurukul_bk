package com.gurukul.audit;

import com.gurukul.auth.security.AuthContext;
import com.gurukul.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Audit log", description = "Who changed what, and when. Admin only.")
public class AuditLogController {

	private final AuditLogService auditLogService;

	@GetMapping("/api/v1/audit-logs")
	@Operation(summary = "Activity log for the school (admin only), newest first, paginated",
			description = "Every create/update/delete with the actor and a field-level old/new diff. "
					+ "Optional filters: entityType (e.g. Student), entityId, actorId. Defaults to page 0, size 50.")
	public ApiResponse<List<AuditLogEntryResponse>> list(
			@RequestParam(required = false) String entityType,
			@RequestParam(required = false) String entityId,
			@RequestParam(required = false) UUID actorId,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "50") int size) {
		AuditLogService.Page result = auditLogService.list(AuthContext.current(), entityType, entityId, actorId,
				Math.max(page, 0), Math.clamp(size, 1, 200));
		return ApiResponse.page(result.content(), result.hasNext(), result.totalElements());
	}

	@GetMapping("/api/v1/audit-logs/entity-types")
	@Operation(summary = "Record types that appear in this school's activity log (admin only), for filtering")
	public ApiResponse<List<String>> entityTypes() {
		return ApiResponse.success(auditLogService.entityTypes(AuthContext.current()));
	}

}
