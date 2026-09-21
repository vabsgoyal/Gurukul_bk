package com.gurukul.chat.bot.tool;

import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.Tool;
import com.gurukul.auth.entity.Role;
import com.gurukul.auth.security.AuthPrincipal;
import com.gurukul.insights.EntitySchema;
import com.gurukul.insights.InsightQueryException;
import com.gurukul.insights.MetricFilter;
import com.gurukul.insights.MetricQueryEngine;
import com.gurukul.insights.SchemaCatalog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A single generic tool answering school-wide aggregate questions (counts, sums, percentages) by
 * dispatching into {@link MetricQueryEngine} against the {@link SchemaCatalog}. ADMIN-only - every
 * other bot tool answers "about me"; this one answers "about the school", so it is deliberately kept
 * out of every non-admin caller's tool list via {@link #appliesTo}.
 *
 * The tool's description and input schema are generated from the loaded catalog rather than
 * hand-written per question, so adding a new entity/metric via YAML makes it immediately usable here
 * with no code change.
 */
@Component
@RequiredArgsConstructor
public class QueryMetricTool implements BotTool {

	private final SchemaCatalog catalog;
	private final MetricQueryEngine engine;

	@Override
	public String name() {
		return "query_school_metric";
	}

	@Override
	public String description() {
		StringBuilder sb = new StringBuilder(
				"Answers a school-wide aggregate question (counts, sums, percentages) by querying real school "
						+ "data - e.g. how many students haven't paid fees, how many staff salaries are unpaid, a "
						+ "teacher's attendance percentage. Only usable by school admins; never scoped to one "
						+ "person. Always call this rather than guessing a number - if no entity/metric here fits "
						+ "the question, say so rather than answering from general knowledge.\n\n"
						+ "Available entities:\n");
		for (EntitySchema entity : catalog.entities()) {
			sb.append("- ").append(entity.name()).append(": ").append(entity.description()).append('\n');
			entity.metrics().forEach((metricName, metric) ->
					sb.append("    metric \"").append(metricName).append("\": ").append(metric.description()).append('\n'));
			entity.filters().forEach((fieldName, field) -> {
				sb.append("    filter field \"").append(fieldName).append("\" (").append(field.type()).append(")");
				if (field.values() != null) {
					sb.append(" values: ").append(field.values());
				}
				sb.append('\n');
			});
		}
		return sb.toString();
	}

	@Override
	public Tool.InputSchema inputSchema() {
		List<String> entityNames = catalog.entities().stream().map(EntitySchema::name).toList();
		return Tool.InputSchema.builder()
				.properties(Tool.InputSchema.Properties.builder()
						.putAdditionalProperty("entity", JsonValue.from(Map.of(
								"type", "string",
								"enum", entityNames,
								"description", "Which entity to query - see the tool description for the full list and its metrics/filters.")))
						.putAdditionalProperty("metric", JsonValue.from(Map.of(
								"type", "string",
								"description", "Which metric of that entity to compute - see the tool description.")))
						.putAdditionalProperty("filters", JsonValue.from(Map.of(
								"type", "array",
								"description", "Filters/params narrowing the query, e.g. "
										+ "[{\"field\":\"status\",\"op\":\"=\",\"value\":\"UNPAID\"}]. For a CUSTOM "
										+ "metric, pass its documented parameters the same way, always with op \"=\".",
								"items", Map.of(
										"type", "object",
										"properties", Map.of(
												"field", Map.of("type", "string"),
												"op", Map.of("type", "string", "enum", List.of("=", "!=", "<", "<=", ">", ">=")),
												"value", Map.of("type", "string")),
										"required", List.of("field", "op", "value")))))
						.build())
				.required(List.of("entity", "metric"))
				.build();
	}

	@Override
	public boolean appliesTo(AuthPrincipal principal) {
		return principal.getRole() == Role.ADMIN;
	}

	@Override
	@SuppressWarnings("unchecked")
	public Object execute(AuthPrincipal principal, Map<String, Object> input) {
		String entity = String.valueOf(input.get("entity"));
		String metric = String.valueOf(input.get("metric"));
		List<Map<String, Object>> rawFilters = (List<Map<String, Object>>) input.getOrDefault("filters", List.of());

		List<MetricFilter> filters = new ArrayList<>();
		for (Map<String, Object> raw : rawFilters) {
			filters.add(new MetricFilter(String.valueOf(raw.get("field")), String.valueOf(raw.get("op")), raw.get("value")));
		}

		try {
			return engine.query(entity, metric, filters);
		} catch (InsightQueryException ex) {
			// A recoverable/expected condition (unknown field, ambiguous name, bad value) - return it as a
			// normal tool result so the model reads and acts on the message, instead of letting it fall into
			// BotReplyService's generic swallowed-exception fallback ("Error retrieving this data").
			return Map.of("error", ex.getMessage());
		}
	}

}
