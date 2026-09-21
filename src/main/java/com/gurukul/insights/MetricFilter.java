package com.gurukul.insights;

/** One filter/param the caller (an LLM tool call) supplied. {@code field}, {@code op}, and {@code value} are all untrusted input. */
public record MetricFilter(String field, String op, Object value) {
}
