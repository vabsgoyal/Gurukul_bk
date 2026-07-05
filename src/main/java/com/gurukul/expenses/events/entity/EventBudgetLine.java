package com.gurukul.expenses.events.entity;

import com.gurukul.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "event_budget_line")
public class EventBudgetLine extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "budget_id", nullable = false)
	private EventBudget budget;

	@Column(nullable = false, length = 500)
	private String description;

	@Column(name = "planned_amount", nullable = false, precision = 12, scale = 2)
	private BigDecimal plannedAmount;

}
