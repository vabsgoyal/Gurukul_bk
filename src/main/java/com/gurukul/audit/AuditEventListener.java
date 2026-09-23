package com.gurukul.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gurukul.auth.security.AuthContext;
import com.gurukul.auth.security.AuthPrincipal;
import com.gurukul.common.BaseEntity;
import com.gurukul.schools.entity.School;
import lombok.RequiredArgsConstructor;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.event.spi.PostDeleteEvent;
import org.hibernate.event.spi.PostDeleteEventListener;
import org.hibernate.event.spi.PostInsertEvent;
import org.hibernate.event.spi.PostInsertEventListener;
import org.hibernate.event.spi.PostUpdateEvent;
import org.hibernate.event.spi.PostUpdateEventListener;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.proxy.HibernateProxy;
import org.hibernate.type.Type;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Writes one audit_log row for every insert/update/delete Hibernate flushes, for every entity - no
 * per-service code, so new entities are covered automatically. Registered by AuditListenerRegistrar.
 *
 * Rows are written with JdbcTemplate, not through the Hibernate session: persisting from inside a
 * flush listener re-enters the flush. JdbcTemplate picks up the same transaction-bound connection
 * (JpaTransactionManager exposes it), so the audit row commits or rolls back with the change itself.
 *
 * Not captured: bulk JPQL UPDATE/DELETE and native SQL (they bypass entity events).
 */
@Component
@RequiredArgsConstructor
public class AuditEventListener implements PostInsertEventListener, PostUpdateEventListener, PostDeleteEventListener {

	/**
	 * Property names whose values are never written to the log (credential hashes, TokenCipher-encrypted
	 * Aadhaar/bank numbers, OAuth/push tokens, device API key hashes). The log is admin-readable, so it
	 * must not become a second copy of secrets. A new sensitive field must match this pattern.
	 */
	static final Pattern REDACTED_PROPERTY = Pattern.compile("(?i).*(password|hash|secret|token|otp|encrypted|pin).*");
	static final String REDACTED = "[redacted]";

	/**
	 * Not audited: private chat content (admins must not read conversations through the log), push
	 * tokens, and high-churn gamification gameplay (answers, XP ticks, matchmaking), which is not an
	 * edit by any useful definition and would drown out the real changes.
	 */
	static final Set<String> EXCLUDED_ENTITIES = Set.of(
			"Message", "Conversation", "ConversationParticipant", "DeviceToken", "ReceiptSequence",
			"XpEvent", "StudentGameProfile", "BattleAnswer", "BattleBuzzWinner", "BattleRoom",
			"BattleRoomParticipant", "PracticeAnswer", "PracticeSession", "QuizAnswer", "QuizChallenge");

	private static final Set<String> SKIPPED_PROPERTIES = Set.of("createdAt", "updatedAt");

	private static final String INSERT_SQL = """
			INSERT INTO audit_log (id, school_id, entity_type, entity_id, action, changes,
			    actor_owner_id, actor_owner_type, actor_role, actor_username, occurred_at)
			VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
			""";

	private final JdbcTemplate jdbcTemplate;
	private final ObjectMapper objectMapper = new ObjectMapper();

	@Override
	public void onPostInsert(PostInsertEvent event) {
		record(event.getEntity(), event.getId(), event.getPersister(), AuditAction.CREATE,
				diff(event.getPersister(), event.getSession(), null, event.getState(), null));
	}

	@Override
	public void onPostUpdate(PostUpdateEvent event) {
		Map<String, Map<String, Object>> changes = diff(event.getPersister(), event.getSession(),
				event.getOldState(), event.getState(), event.getDirtyProperties());
		if (changes.isEmpty()) {
			return;
		}
		record(event.getEntity(), event.getId(), event.getPersister(), AuditAction.UPDATE, changes);
	}

	@Override
	public void onPostDelete(PostDeleteEvent event) {
		record(event.getEntity(), event.getId(), event.getPersister(), AuditAction.DELETE,
				diff(event.getPersister(), event.getSession(), event.getDeletedState(), null, null));
	}

	@Override
	public boolean requiresPostCommitHandling(EntityPersister persister) {
		return false;
	}

	private void record(Object entity, Object id, EntityPersister persister, AuditAction action,
			Map<String, Map<String, Object>> changes) {
		String entityType = simpleName(persister.getEntityName());
		UUID schoolId = schoolIdOf(entity);
		if (EXCLUDED_ENTITIES.contains(entityType) || schoolId == null) {
			return;
		}
		AuthPrincipal actor = AuthContext.currentOrNull();
		jdbcTemplate.update(INSERT_SQL,
				UUID.randomUUID(),
				schoolId,
				entityType,
				String.valueOf(id),
				action.name(),
				toJson(changes),
				actor != null ? actor.getOwnerId() : null,
				actor != null ? actor.getOwnerType().name() : null,
				actor != null ? actor.getRole().name() : "SYSTEM",
				actor != null ? actor.getUsername() : "SYSTEM",
				Timestamp.from(Instant.now()));
	}

	/** Field name to {"old": ..., "new": ...}; "old" is absent for CREATE, "new" for DELETE. */
	private Map<String, Map<String, Object>> diff(EntityPersister persister, SharedSessionContractImplementor session,
			Object[] oldState, Object[] newState, int[] dirtyProperties) {
		String[] names = persister.getPropertyNames();
		Type[] types = persister.getPropertyTypes();
		Map<String, Map<String, Object>> changes = new LinkedHashMap<>();
		int[] indexes = dirtyProperties != null ? dirtyProperties : allIndexes(names.length);
		for (int i : indexes) {
			String name = names[i];
			if (SKIPPED_PROPERTIES.contains(name) || types[i].isCollectionType()) {
				continue;
			}
			Object oldValue = oldState != null ? auditValue(types[i], oldState[i], session) : null;
			Object newValue = newState != null ? auditValue(types[i], newState[i], session) : null;
			boolean isUpdate = oldState != null && newState != null;
			if (isUpdate ? Objects.equals(oldValue, newValue) : (oldValue == null && newValue == null)) {
				continue;
			}
			boolean redact = REDACTED_PROPERTY.matcher(name).matches();
			Map<String, Object> change = new LinkedHashMap<>();
			if (oldState != null) {
				change.put("old", redact && oldValue != null ? REDACTED : oldValue);
			}
			if (newState != null) {
				change.put("new", redact && newValue != null ? REDACTED : newValue);
			}
			changes.put(name, change);
		}
		return changes;
	}

	private Object auditValue(Type type, Object value, SharedSessionContractImplementor session) {
		if (value == null) {
			return null;
		}
		if (type.isEntityType()) {
			if (value instanceof HibernateProxy proxy) {
				return String.valueOf(proxy.getHibernateLazyInitializer().getIdentifier());
			}
			if (value instanceof BaseEntity base) {
				return String.valueOf(base.getId());
			}
			if (value instanceof School school) {
				return String.valueOf(school.getId());
			}
			return String.valueOf(session.getEntityPersister(null, value).getIdentifier(value, session));
		}
		if (value instanceof Enum<?> e) {
			return e.name();
		}
		if (value instanceof Number || value instanceof Boolean || value instanceof String) {
			return value;
		}
		return value.toString();
	}

	private UUID schoolIdOf(Object entity) {
		if (entity instanceof BaseEntity base) {
			return base.getSchoolId();
		}
		if (entity instanceof School school) {
			return school.getId();
		}
		return null;
	}

	private String toJson(Map<String, Map<String, Object>> changes) {
		try {
			return objectMapper.writeValueAsString(changes);
		} catch (JsonProcessingException ex) {
			return "{}";
		}
	}

	private static String simpleName(String entityName) {
		return entityName.substring(entityName.lastIndexOf('.') + 1);
	}

	private static int[] allIndexes(int length) {
		int[] indexes = new int[length];
		for (int i = 0; i < length; i++) {
			indexes[i] = i;
		}
		return indexes;
	}

}
