package com.gurukul.insights;

import com.gurukul.common.SchoolContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The single choke point every insight-query bot tool calls through. Every guarantee lives here,
 * once, rather than per-tool:
 * <ul>
 *   <li>{@code schoolId} always comes from {@link SchoolContext}, never from a caller-supplied filter -
 *       an LLM tool call can never set or override it.</li>
 *   <li>Every {@code entity}/{@code metric}/filter {@code field} is validated against the loaded
 *       {@link SchemaCatalog} before anything runs; unknown values fail closed via
 *       {@link InsightQueryException} rather than guessing.</li>
 *   <li>Column/path names embedded in generated JPQL only ever come from the catalog's own YAML-sourced
 *       {@link FieldSchema#path()} - never from the caller's filter text - so there is no injection
 *       surface even though this builds a query string dynamically.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class MetricQueryEngine {

	private static final Set<String> ALLOWED_OPERATORS = Set.of("=", "!=", "<", "<=", ">", ">=");

	private final SchemaCatalog catalog;
	private final SchoolContext schoolContext;
	private final EntityManager entityManager;
	private final Map<String, MetricResolver> resolversByName;

	@Transactional(readOnly = true)
	public Object query(String entityName, String metricName, List<MetricFilter> filters) {
		EntitySchema entity = catalog.entity(entityName)
				.orElseThrow(() -> new InsightQueryException(
						"Unknown entity '" + entityName + "'. Available entities: " + catalogEntityNames()));
		MetricSchema metric = entity.metrics().get(metricName);
		if (metric == null) {
			throw new InsightQueryException("Unknown metric '" + metricName + "' for entity '" + entityName
					+ "'. Available metrics: " + entity.metrics().keySet());
		}
		UUID schoolId = schoolContext.getSchoolId();
		if (metric.type() == MetricType.CUSTOM) {
			return runCustom(metric, schoolId, filters);
		}
		return runCount(entity, schoolId, filters);
	}

	private Object runCustom(MetricSchema metric, UUID schoolId, List<MetricFilter> filters) {
		MetricResolver resolver = resolversByName.get(metric.resolver());
		if (resolver == null) {
			throw new IllegalStateException("No MetricResolver bean named '" + metric.resolver() + "'");
		}
		Map<String, Object> params = filters.stream()
				.collect(Collectors.toMap(MetricFilter::field, f -> f.value(), (a, b) -> b, HashMap::new));
		return resolver.resolve(schoolId, params);
	}

	private long runCount(EntitySchema entity, UUID schoolId, List<MetricFilter> filters) {
		StringBuilder jpql = new StringBuilder("select count(e) from ").append(entity.jpaEntity()).append(" e where e.schoolId = :schoolId");
		Map<String, Object> params = new HashMap<>();
		params.put("schoolId", schoolId);

		int i = 0;
		for (MetricFilter filter : filters) {
			FieldSchema field = entity.filters().get(filter.field());
			if (field == null) {
				throw new InsightQueryException("Unknown filter field '" + filter.field() + "' for entity '" + entity.name()
						+ "'. Available filter fields: " + entity.filters().keySet());
			}
			String op = validateOperator(filter.op());
			String paramName = "p" + i++;
			jpql.append(" and e.").append(field.path()).append(' ').append(op).append(" :").append(paramName);
			params.put(paramName, coerce(field, filter.value()));
		}

		TypedQuery<Long> query = entityManager.createQuery(jpql.toString(), Long.class);
		params.forEach(query::setParameter);
		return query.getSingleResult();
	}

	private String validateOperator(String op) {
		if (!ALLOWED_OPERATORS.contains(op)) {
			throw new InsightQueryException("Unsupported filter operator '" + op + "'. Allowed: " + ALLOWED_OPERATORS);
		}
		return op;
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private Object coerce(FieldSchema field, Object rawValue) {
		String value = String.valueOf(rawValue);
		return switch (field.type()) {
			case STRING -> value;
			case UUID -> {
				try {
					yield java.util.UUID.fromString(value);
				} catch (IllegalArgumentException ex) {
					throw new InsightQueryException("Invalid id value '" + value + "'");
				}
			}
			case DATE -> {
				try {
					yield LocalDate.parse(value);
				} catch (Exception ex) {
					throw new InsightQueryException("Invalid date value '" + value + "', expected ISO-8601 (YYYY-MM-DD)");
				}
			}
			case INTEGER -> {
				try {
					yield Integer.parseInt(value);
				} catch (NumberFormatException ex) {
					throw new InsightQueryException("Invalid integer value '" + value + "'");
				}
			}
			case ENUM -> {
				if (field.values() != null && !field.values().contains(value)) {
					throw new InsightQueryException("Invalid value '" + value + "'. Allowed: " + field.values());
				}
				try {
					Class<? extends Enum> enumClass = (Class<? extends Enum>) Class.forName(field.enumClass());
					yield Enum.valueOf(enumClass, value);
				} catch (ClassNotFoundException | IllegalArgumentException ex) {
					throw new InsightQueryException("Invalid value '" + value + "'. Allowed: " + field.values());
				}
			}
		};
	}

	private List<String> catalogEntityNames() {
		return catalog.entities().stream().map(EntitySchema::name).toList();
	}

}
