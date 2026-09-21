package com.gurukul.insights;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Plain unit test (no Spring context needed) - confirms every insights/schema/*.yaml file on the
 * classpath parses and that the starter catalog has the shape the chat bot tool and its resolvers
 * depend on. A typo in a YAML file should fail here, loudly, at build time, rather than surfacing as
 * a confusing runtime error the first time someone asks a question that touches it.
 */
class SchemaCatalogTest {

	private SchemaCatalog catalog;

	@BeforeEach
	void setUp() throws Exception {
		catalog = new SchemaCatalog();
		catalog.load();
	}

	@Test
	void loadsAllFiveStarterEntities() {
		assertThat(catalog.entities()).extracting(EntitySchema::name)
				.containsExactlyInAnyOrder("fee_assessment", "attendance", "staff_attendance", "payroll", "enrollment");
	}

	@Test
	void feeAssessmentHasCountAndOutstandingSumMetrics() {
		EntitySchema entity = catalog.entity("fee_assessment").orElseThrow();
		assertThat(entity.jpaEntity()).isEqualTo("com.gurukul.fees.entity.StudentFeeAssessment");
		assertThat(entity.metrics()).containsKeys("count", "outstandingSum");
		assertThat(entity.metrics().get("count").type()).isEqualTo(MetricType.COUNT);
		assertThat(entity.metrics().get("outstandingSum").type()).isEqualTo(MetricType.CUSTOM);
		assertThat(entity.metrics().get("outstandingSum").resolver()).isEqualTo("outstandingFeesResolver");
		assertThat(entity.filters().get("status").enumClass()).isEqualTo("com.gurukul.fees.entity.FeeAssessmentStatus");
		assertThat(entity.filters().get("status").values()).containsExactly("UNPAID", "PARTIAL", "PAID", "OVERDUE");
	}

	@Test
	void payrollFiltersReachThroughTheRunJoin() {
		EntitySchema entity = catalog.entity("payroll").orElseThrow();
		assertThat(entity.filters().get("runStatus").path()).isEqualTo("run.status");
		assertThat(entity.filters().get("runStatus").enumClass()).isEqualTo("com.gurukul.payroll.entity.PayrollRunStatus");
	}

	@Test
	void staffAndStudentAttendanceEachDeclareAPresentPercentageCustomMetric() {
		assertThat(catalog.entity("attendance").orElseThrow().metrics().get("presentPercentage").resolver())
				.isEqualTo("studentAttendancePercentageResolver");
		assertThat(catalog.entity("staff_attendance").orElseThrow().metrics().get("presentPercentage").resolver())
				.isEqualTo("staffAttendancePercentageResolver");
	}

	@Test
	void unknownEntityLookupIsEmpty() {
		assertThat(catalog.entity("does_not_exist")).isEmpty();
	}

}
