package com.gurukul.exams.entity;

import com.gurukul.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** One marks-percentage band -> letter grade, e.g. 90-100 -> "A+". Per-school; see GradingScaleService. */
@Getter
@Setter
@Entity
@Table(name = "grading_band")
public class GradingBand extends BaseEntity {

	@Column(name = "min_percentage", nullable = false, precision = 5, scale = 2)
	private BigDecimal minPercentage;

	@Column(name = "max_percentage", nullable = false, precision = 5, scale = 2)
	private BigDecimal maxPercentage;

	@Column(nullable = false, length = 10)
	private String label;

}
