package com.gurukul.insights;

import java.util.Map;
import java.util.UUID;

/**
 * Handles one CUSTOM metric named in a schema YAML file's {@code metrics.<name>.resolver} - anything
 * needing a join or conditional aggregation beyond a generic COUNT. Implementations are Spring
 * {@code @Component} beans whose bean name matches the YAML {@code resolver} value exactly.
 *
 * {@code schoolId} is passed in by {@link MetricQueryEngine} from {@code SchoolContext} - implementations
 * must use it for every query and must never accept a school id from {@code params}. {@code params} is
 * the caller's raw field-&gt;value filter map (untrusted; each resolver defines and validates its own
 * expected param names, documented in its YAML metric's description).
 */
public interface MetricResolver {

	Object resolve(UUID schoolId, Map<String, Object> params);

}
