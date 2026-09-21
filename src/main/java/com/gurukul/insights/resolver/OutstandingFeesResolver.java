package com.gurukul.insights.resolver;

import com.gurukul.insights.InsightQueryException;
import com.gurukul.insights.MetricResolver;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Backs {@code fee_assessment}'s {@code outstandingSum} metric - CUSTOM because it sums a computed
 * value (totalDue - totalPaid), not a raw column, so it isn't expressible by the generic COUNT engine.
 */
@Component
@RequiredArgsConstructor
public class OutstandingFeesResolver implements MetricResolver {

	private final EntityManager entityManager;

	@Override
	public Object resolve(UUID schoolId, Map<String, Object> params) {
		StringBuilder jpql = new StringBuilder(
				"select coalesce(sum(e.totalDue - e.totalPaid), 0) from com.gurukul.fees.entity.StudentFeeAssessment e "
						+ "where e.schoolId = :schoolId");

		Object status = params.get("status");
		Object academicYear = params.get("academicYear");
		Object classSectionId = params.get("classSectionId");
		if (status != null) {
			jpql.append(" and e.status = :status");
		}
		if (academicYear != null) {
			jpql.append(" and e.academicYear = :academicYear");
		}
		if (classSectionId != null) {
			jpql.append(" and e.student.classSection.id = :classSectionId");
		}

		TypedQuery<BigDecimal> query = entityManager.createQuery(jpql.toString(), BigDecimal.class);
		query.setParameter("schoolId", schoolId);
		if (status != null) {
			query.setParameter("status", parseStatus(status.toString()));
		}
		if (academicYear != null) {
			query.setParameter("academicYear", academicYear.toString());
		}
		if (classSectionId != null) {
			query.setParameter("classSectionId", parseUuid(classSectionId.toString()));
		}

		return Map.of("outstandingSum", query.getSingleResult());
	}

	private com.gurukul.fees.entity.FeeAssessmentStatus parseStatus(String value) {
		try {
			return com.gurukul.fees.entity.FeeAssessmentStatus.valueOf(value);
		} catch (IllegalArgumentException ex) {
			throw new InsightQueryException("Invalid status '" + value + "'. Allowed: UNPAID, PARTIAL, PAID, OVERDUE");
		}
	}

	private UUID parseUuid(String value) {
		try {
			return UUID.fromString(value);
		} catch (IllegalArgumentException ex) {
			throw new InsightQueryException("Invalid classSectionId '" + value + "'");
		}
	}

}
