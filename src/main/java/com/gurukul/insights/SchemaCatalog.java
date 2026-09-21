package com.gurukul.insights;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Loads every {@code insights/schema/*.yaml} file on the classpath at startup into a queryable
 * catalog of what {@link MetricQueryEngine} is allowed to run - adding a new queryable table is meant
 * to be "add a YAML file here", not "write a new Java class". Uses SnakeYAML directly (already on the
 * classpath transitively via Spring Boot) rather than a schema-binding library, since the shape here
 * is simple enough that manual mapping is clearer than fighting a generic deserializer's defaults.
 */
@Component
@Slf4j
public class SchemaCatalog {

	private static final String LOCATION_PATTERN = "classpath*:insights/schema/*.yaml";

	private final Map<String, EntitySchema> entitiesByName = new TreeMap<>();

	@PostConstruct
	void load() throws IOException {
		PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
		Resource[] resources = resolver.getResources(LOCATION_PATTERN);
		Yaml yaml = new Yaml();
		for (Resource resource : resources) {
			EntitySchema entity = parse(yaml, resource);
			if (entitiesByName.containsKey(entity.name())) {
				throw new IllegalStateException("Duplicate insights schema entity name: " + entity.name());
			}
			entitiesByName.put(entity.name(), entity);
		}
		log.info("Loaded {} insights schema entities: {}", entitiesByName.size(), entitiesByName.keySet());
	}

	public List<EntitySchema> entities() {
		return List.copyOf(entitiesByName.values());
	}

	public Optional<EntitySchema> entity(String name) {
		return Optional.ofNullable(entitiesByName.get(name));
	}

	@SuppressWarnings("unchecked")
	private EntitySchema parse(Yaml yaml, Resource resource) throws IOException {
		try (InputStream in = resource.getInputStream()) {
			Map<String, Object> root = yaml.load(in);
			String name = requireString(root, "entity", resource);
			String jpaEntity = requireString(root, "jpaEntity", resource);
			String description = requireString(root, "description", resource).strip();

			Map<String, FieldSchema> filters = new LinkedHashMap<>();
			Map<String, Object> rawFilters = (Map<String, Object>) root.getOrDefault("filters", Map.of());
			for (Map.Entry<String, Object> entry : rawFilters.entrySet()) {
				Map<String, Object> f = (Map<String, Object>) entry.getValue();
				filters.put(entry.getKey(), new FieldSchema(
						requireString(f, "path", resource),
						FieldType.valueOf(requireString(f, "type", resource)),
						(List<String>) f.get("values"),
						(String) f.get("enumClass")));
			}

			Map<String, MetricSchema> metrics = new LinkedHashMap<>();
			Map<String, Object> rawMetrics = (Map<String, Object>) root.getOrDefault("metrics", Map.of());
			for (Map.Entry<String, Object> entry : rawMetrics.entrySet()) {
				Map<String, Object> m = (Map<String, Object>) entry.getValue();
				metrics.put(entry.getKey(), new MetricSchema(
						MetricType.valueOf(requireString(m, "type", resource)),
						(String) m.get("resolver"),
						requireString(m, "description", resource).strip()));
			}
			if (metrics.isEmpty()) {
				throw new IllegalStateException("Insights schema " + resource.getFilename() + " defines no metrics");
			}

			return new EntitySchema(name, jpaEntity, description, Map.copyOf(filters), Map.copyOf(metrics));
		}
	}

	private String requireString(Map<String, Object> map, String key, Resource resource) {
		Object value = map.get(key);
		if (value == null) {
			throw new IllegalStateException("Insights schema " + resource.getFilename() + " is missing required key '" + key + "'");
		}
		return value.toString();
	}

}
