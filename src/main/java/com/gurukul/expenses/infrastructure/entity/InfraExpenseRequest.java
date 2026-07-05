package com.gurukul.expenses.infrastructure.entity;

import com.gurukul.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "infra_expense_request")
public class InfraExpenseRequest extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "category_id", nullable = false)
	private InfraExpenseCategory category;

	@Column(nullable = false, length = 1000)
	private String description;

	@Column(name = "estimated_amount", nullable = false, precision = 12, scale = 2)
	private BigDecimal estimatedAmount;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private InfraExpenseStatus status;

}
