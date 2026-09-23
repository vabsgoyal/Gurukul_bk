package com.gurukul.audit;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * One audit_log row. changes maps each field name to {"old": ..., "new": ...} ("old" absent on CREATE,
 * "new" absent on DELETE; sensitive values are "[redacted]").
 */
public record AuditLogEntryResponse(
		UUID id,
		String entityType,
		String entityId,
		AuditAction action,
		Map<String, Map<String, Object>> changes,
		UUID actorOwnerId,
		String actorOwnerType,
		String actorRole,
		String actorUsername,
		Instant occurredAt) {
}
