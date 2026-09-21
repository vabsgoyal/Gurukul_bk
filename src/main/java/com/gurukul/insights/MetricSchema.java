package com.gurukul.insights;

/** {@code resolver} is the Spring bean name of the {@link MetricResolver} to dispatch to; set only when type is CUSTOM. */
public record MetricSchema(MetricType type, String resolver, String description) {
}
