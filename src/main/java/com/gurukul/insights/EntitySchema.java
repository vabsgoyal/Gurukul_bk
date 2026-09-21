package com.gurukul.insights;

import java.util.Map;

public record EntitySchema(
		String name,
		String jpaEntity,
		String description,
		Map<String, FieldSchema> filters,
		Map<String, MetricSchema> metrics) {
}
