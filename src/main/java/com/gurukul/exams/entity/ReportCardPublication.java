package com.gurukul.exams.entity;

import com.gurukul.common.BaseEntity;
import com.gurukul.employees.entity.Employee;
import com.gurukul.students.entity.ClassSection;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * One row per (class-section, term) that an admin has published. Its mere existence is the lock:
 * once present, marks entry for any assessment in that section/term is rejected (see
 * AssessmentResultService), and a STUDENT session may view the report card for that term (an
 * ADMIN/TEACHER may always preview it, published or not - see ReportCardService).
 */
@Getter
@Setter
@Entity
@Table(name = "report_card_publication", uniqueConstraints = {
		@UniqueConstraint(columnNames = {"class_section_id", "term"})
})
public class ReportCardPublication extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "class_section_id", nullable = false)
	private ClassSection classSection;

	@Column(nullable = false, length = 50)
	private String term;

	@Column(name = "published_at", nullable = false)
	private Instant publishedAt;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "published_by_employee_id", nullable = false)
	private Employee publishedByEmployee;

}
