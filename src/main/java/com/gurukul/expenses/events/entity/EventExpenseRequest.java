package com.gurukul.expenses.events.entity;

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
@Table(name = "event_expense_request")
public class EventExpenseRequest extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "budget_line_id", nullable = false)
	private EventBudgetLine budgetLine;

	@Column(nullable = false, length = 1000)
	private String description;

	@Column(name = "estimated_amount", nullable = false, precision = 12, scale = 2)
	private BigDecimal estimatedAmount;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private EventExpenseStatus status;

}
