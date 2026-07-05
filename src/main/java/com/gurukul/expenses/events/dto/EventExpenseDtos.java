package com.gurukul.expenses.events.dto;

import com.gurukul.expenses.events.entity.*;
import com.gurukul.finance.entity.PaymentMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public class EventExpenseDtos {

	@Getter @Setter
	public static class BudgetRequest {
		@NotEmpty @Valid
		private List<BudgetLineRequest> lines;
	}

	@Getter @Setter
	public static class BudgetLineRequest {
		@NotBlank private String description;
		@NotNull @DecimalMin("0.01") private BigDecimal plannedAmount;
	}

	@Getter @AllArgsConstructor
	public static class BudgetResponse {
		private UUID id;
		private UUID eventId;
		private List<BudgetLineResponse> lines;

		public static BudgetResponse from(EventBudget budget, List<EventBudgetLine> lines) {
			return new BudgetResponse(budget.getId(), budget.getEvent().getId(),
					lines.stream().map(BudgetLineResponse::from).toList());
		}
	}

	@Getter @AllArgsConstructor
	public static class BudgetLineResponse {
		private UUID id;
		private String description;
		private BigDecimal plannedAmount;

		public static BudgetLineResponse from(EventBudgetLine line) {
			return new BudgetLineResponse(line.getId(), line.getDescription(), line.getPlannedAmount());
		}
	}

	@Getter @Setter
	public static class ExpenseRequestCreate {
		@NotNull private UUID budgetLineId;
		@NotBlank private String description;
		@NotNull @DecimalMin("0.01") private BigDecimal estimatedAmount;
	}

	@Getter @AllArgsConstructor
	public static class ExpenseRequestResponse {
		private UUID id;
		private UUID budgetLineId;
		private String description;
		private BigDecimal estimatedAmount;
		private EventExpenseStatus status;

		public static ExpenseRequestResponse from(EventExpenseRequest r) {
			return new ExpenseRequestResponse(r.getId(), r.getBudgetLine().getId(), r.getDescription(), r.getEstimatedAmount(), r.getStatus());
		}
	}

	@Getter @Setter
	public static class PayRequest {
		@NotNull private UUID vendorId;
		@NotNull private PaymentMethod paymentMethod;
		private String paymentReference;
		private LocalDate transactionDate;
	}

	@Getter @Setter
	public static class ApprovalActionRequest {
		private String actor;
		private String comment;
	}

	@Getter @AllArgsConstructor
	@Schema(description = "Event P&L report")
	public static class EventPnlResponse {
		private UUID eventId;
		private BigDecimal totalCollections;
		private BigDecimal totalExpenses;
		private BigDecimal netBalance;
		private BigDecimal totalPlannedBudget;
		private BigDecimal totalActualSpent;
	}

}
