package com.gurukul.audit;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gurukul.auth.entity.Role;
import com.gurukul.auth.security.AuthPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuditLogService {

	public record Page(List<AuditLogEntryResponse> content, boolean hasNext, Long totalElements) {
	}

	private static final TypeReference<Map<String, Map<String, Object>>> CHANGES_TYPE = new TypeReference<>() {
	};

	private final JdbcTemplate jdbcTemplate;
	private final ObjectMapper objectMapper = new ObjectMapper();

	/** Newest first. school_id always comes from the caller's principal, never from a request param. */
	public Page list(AuthPrincipal principal, String entityType, String entityId, UUID actorId, int page, int size) {
		requireAdmin(principal);
		StringBuilder where = new StringBuilder(" WHERE school_id = ?");
		List<Object> args = new ArrayList<>();
		args.add(principal.getSchoolId());
		if (entityType != null && !entityType.isBlank()) {
			where.append(" AND entity_type = ?");
			args.add(entityType.trim());
		}
		if (entityId != null && !entityId.isBlank()) {
			where.append(" AND entity_id = ?");
			args.add(entityId.trim());
		}
		if (actorId != null) {
			where.append(" AND actor_owner_id = ?");
			args.add(actorId);
		}

		List<Object> pageArgs = new ArrayList<>(args);
		pageArgs.add(size + 1);
		pageArgs.add((long) page * size);
		List<AuditLogEntryResponse> rows = jdbcTemplate.query(
				"SELECT * FROM audit_log" + where + " ORDER BY occurred_at DESC, id DESC LIMIT ? OFFSET ?",
				rowMapper(), pageArgs.toArray());
		boolean hasNext = rows.size() > size;
		List<AuditLogEntryResponse> content = hasNext ? rows.subList(0, size) : rows;
		Long total = page == 0
				? jdbcTemplate.queryForObject("SELECT COUNT(*) FROM audit_log" + where, Long.class, args.toArray())
				: null;
		return new Page(content, hasNext, total);
	}

	public List<String> entityTypes(AuthPrincipal principal) {
		requireAdmin(principal);
		return jdbcTemplate.queryForList(
				"SELECT DISTINCT entity_type FROM audit_log WHERE school_id = ? ORDER BY entity_type",
				String.class, principal.getSchoolId());
	}

	private RowMapper<AuditLogEntryResponse> rowMapper() {
		return (rs, rowNum) -> new AuditLogEntryResponse(
				rs.getObject("id", UUID.class),
				rs.getString("entity_type"),
				rs.getString("entity_id"),
				AuditAction.valueOf(rs.getString("action")),
				parseChanges(rs.getString("changes")),
				rs.getObject("actor_owner_id", UUID.class),
				rs.getString("actor_owner_type"),
				rs.getString("actor_role"),
				rs.getString("actor_username"),
				rs.getTimestamp("occurred_at").toInstant());
	}

	private Map<String, Map<String, Object>> parseChanges(String json) {
		try {
			return objectMapper.readValue(json, CHANGES_TYPE);
		} catch (Exception ex) {
			return Map.of();
		}
	}

	private void requireAdmin(AuthPrincipal principal) {
		if (principal.getRole() != Role.ADMIN) {
			throw new AccessDeniedException("Only an admin can view the activity log");
		}
	}

}
