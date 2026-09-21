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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Backs {@code attendance}'s {@code presentPercentage} metric. Attendance percentage has no stored
 * column (present-count / total-days, computed on the fly), and needs a conditional aggregate across a
 * join - a native query, since JPQL has no portable {@code CASE WHEN}-in-aggregate-friendly shorthand
 * here. Uses {@code SUM(CASE WHEN ... THEN 1 ELSE 0 END)} rather than Postgres-only
 * {@code FILTER (WHERE ...)} so this also works against the H2 profile local tests run on - see
 * docs/security/SECURITY_AND_ACCESS.md §9.7 on H2/Postgres parity gaps in this codebase.
 */
@Component
@RequiredArgsConstructor
public class StudentAttendancePercentageResolver implements MetricResolver {

	private static final int MAX_ROWS_LISTED = 50;

	private final EntityManager entityManager;

	@Override
	public Object resolve(UUID schoolId, Map<String, Object> params) {
		Object className = params.get("className");
		if (className == null) {
			throw new InsightQueryException("presentPercentage requires a 'className' filter, e.g. {\"field\":\"className\",\"op\":\"=\",\"value\":\"Grade 7\"}");
		}
		Object section = params.get("section");
		LocalDate dateFrom = parseOptionalDate(params.get("dateFrom"));
		LocalDate dateTo = parseOptionalDate(params.get("dateTo"));
		BigDecimal belowPercent = parseOptionalDecimal(params.get("belowPercent"));

		String sql = """
				select cast(s.id as varchar), s.name,
				       sum(case when ar.status = 'PRESENT' then 1 else 0 end) * 100.0 / count(*) as pct
				from student s
				join attendance_record ar on ar.student_id = s.id and ar.school_id = :schoolId
				join class_section cs on cs.id = s.class_section_id
				where s.school_id = :schoolId
				  and cs.class_name = :className
				  and (cast(:section as varchar) is null or cs.section = :section)
				  and (cast(:dateFrom as date) is null or ar.attendance_date >= :dateFrom)
				  and (cast(:dateTo as date) is null or ar.attendance_date <= :dateTo)
				group by s.id, s.name
				having (cast(:belowPercent as numeric) is null
				        or sum(case when ar.status = 'PRESENT' then 1 else 0 end) * 100.0 / count(*) < :belowPercent)
				order by pct asc
				""";

		Query query = entityManager.createNativeQuery(sql);
		query.setParameter("schoolId", schoolId);
		query.setParameter("className", className.toString());
		query.setParameter("section", section == null ? null : section.toString());
		query.setParameter("dateFrom", dateFrom);
		query.setParameter("dateTo", dateTo);
		query.setParameter("belowPercent", belowPercent);
		query.setMaxResults(500);

		@SuppressWarnings("unchecked")
		List<Object[]> rows = query.getResultList();

		List<Map<String, Object>> students = new ArrayList<>();
		for (Object[] row : rows) {
			Map<String, Object> student = new LinkedHashMap<>();
			student.put("studentId", row[0].toString());
			student.put("name", row[1]);
			student.put("presentPercentage", ((BigDecimal) row[2]).setScale(1, RoundingMode.HALF_UP));
			students.add(student);
		}

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("matchCount", students.size());
		result.put("students", students.size() > MAX_ROWS_LISTED ? students.subList(0, MAX_ROWS_LISTED) : students);
		if (students.size() > MAX_ROWS_LISTED) {
			result.put("note", "Showing the first " + MAX_ROWS_LISTED + " of " + students.size() + " matching students.");
		}
		return result;
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

	private BigDecimal parseOptionalDecimal(Object value) {
		if (value == null) {
			return null;
		}
		try {
			return new BigDecimal(value.toString());
		} catch (NumberFormatException ex) {
			throw new InsightQueryException("Invalid belowPercent value '" + value + "'");
		}
	}

}
