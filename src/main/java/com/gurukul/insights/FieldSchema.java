package com.gurukul.insights;

import java.util.List;

/**
 * {@code path} is a JPQL navigation path through the entity's relations (e.g. "student.classSection.id"),
 * sourced only from our own YAML catalog files - never from a caller/LLM-supplied string - so it is
 * safe to concatenate directly into generated JPQL. {@code enumClass} is set only for ENUM fields, to
 * resolve the real Java enum constant a JPQL parameter needs to bind against.
 */
public record FieldSchema(String path, FieldType type, List<String> values, String enumClass) {
}
