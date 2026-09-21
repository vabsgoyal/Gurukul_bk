package com.gurukul.insights.resolver;

import com.gurukul.insights.InsightQueryException;
import com.gurukul.insights.MetricResolver;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Backs {@code staff_attendance}'s {@code presentPercentage} metric. Employees are identified by name
 * (the LLM can't know ids), so this resolves a name to exactly one employee within the caller's school
 * first - zero or multiple matches return a clarifying message (via InsightQueryException, surfaced to
 * the model as a normal tool result, not swallowed as an error) instead of guessing.
 */
@Component
@RequiredArgsConstructor
public class StaffAttendancePercentageResolver implements MetricResolver {

	private final EntityManager entityManager;

	@Override
	public Object resolve(UUID schoolId, Map<String, Object> params) {
		Object employeeName = params.get("employeeName");
		if (employeeName == null) {
			throw new InsightQueryException("presentPercentage requires an 'employeeName' filter, e.g. {\"field\":\"employeeName\",\"op\":\"=\",\"value\":\"Ravi Kumar\"}");
		}
		LocalDate dateFrom = parseOptionalDate(params.get("dateFrom"));
		LocalDate dateTo = parseOptionalDate(params.get("dateTo"));

		UUID employeeId = resolveEmployee(schoolId, employeeName.toString());

		String sql = """
				select sum(case when ar.status = 'PRESENT' then 1 else 0 end) * 100.0 / nullif(count(*), 0) as pct,
				       count(*) as totalDays
				from staff_attendance_record ar
				where ar.school_id = :schoolId
				  and ar.employee_id = :employeeId
				  and (cast(:dateFrom as date) is null or ar.attendance_date >= :dateFrom)
				  and (cast(:dateTo as date) is null or ar.attendance_date <= :dateTo)
				""";

		Query query = entityManager.createNativeQuery(sql);
		query.setParameter("schoolId", schoolId);
		query.setParameter("employeeId", employeeId);
		query.setParameter("dateFrom", dateFrom);
		query.setParameter("dateTo", dateTo);

		Object[] row = (Object[]) query.getSingleResult();
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("employeeId", employeeId.toString());
		result.put("totalDays", ((Number) row[1]).intValue());
		result.put("presentPercentage", row[0] == null ? null : ((BigDecimal) row[0]).setScale(1, RoundingMode.HALF_UP));
		if (row[0] == null) {
			result.put("note", "No attendance records found for this employee in the given range.");
		}
		return result;
	}

	@SuppressWarnings("unchecked")
	private UUID resolveEmployee(UUID schoolId, String name) {
		List<Object[]> matches = entityManager.createNativeQuery(
						// id cast to varchar: an unmapped native query returns the driver's raw JDBC
						// representation per column, and H2 (unlike Postgres) hands back a UUID column as a
						// raw byte[] rather than a String - cast makes this portable across both.
						"select cast(id as varchar), name from employee where school_id = :schoolId and lower(name) like :pattern")
				.setParameter("schoolId", schoolId)
				.setParameter("pattern", "%" + name.toLowerCase().strip() + "%")
				.getResultList();

		if (matches.isEmpty()) {
			throw new InsightQueryException("No employee found matching '" + name + "'. Ask the user for the correct name.");
		}
		if (matches.size() > 1) {
			String candidates = matches.stream().map(row -> row[1].toString()).collect(Collectors.joining(", "));
			throw new InsightQueryException("Multiple employees match '" + name + "': " + candidates
					+ ". Ask the user which one they mean, then call this again with the exact name.");
		}
		return UUID.fromString(matches.get(0)[0].toString());
	}

	private LocalDate parseOptionalDate(Object value) {
		if (value == null) {
			return null;
		}
		try {
			return LocalDate.parse(value.toString());
		} catch (Exception ex) {
			throw new InsightQueryException("Invalid date '" + value + "', expected ISO-8601 (YYYY-MM-DD)");
		}
	}

}
