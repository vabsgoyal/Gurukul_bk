package com.gurukul.expenses.infrastructure.dto;

import com.gurukul.expenses.infrastructure.entity.InfraExpenseRequest;
import com.gurukul.expenses.infrastructure.entity.InfraExpenseStatus;
import com.gurukul.finance.entity.PaymentMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public class InfraExpenseDtos {

	@Getter @AllArgsConstructor
	public static class CategoryResponse {
		private UUID id;
		private String code;
		private String name;

		public static CategoryResponse from(com.gurukul.expenses.infrastructure.entity.InfraExpenseCategory c) {
			return new CategoryResponse(c.getId(), c.getCode(), c.getName());
		}
	}

	@Getter @Setter
	public static class RequestCreate {
		@NotNull private UUID categoryId;
		@NotBlank private String description;
		@NotNull @DecimalMin("0.01") private BigDecimal estimatedAmount;
	}

	@Getter @AllArgsConstructor
	public static class RequestResponse {
		private UUID id;
		private UUID categoryId;
		private String categoryCode;
		private String description;
		private BigDecimal estimatedAmount;
		private InfraExpenseStatus status;

		public static RequestResponse from(InfraExpenseRequest r) {
			return new RequestResponse(r.getId(), r.getCategory().getId(), r.getCategory().getCode(),
					r.getDescription(), r.getEstimatedAmount(), r.getStatus());
		}
	}

	@Getter @Setter
	public static class PurchaseRequest {
		@NotNull private UUID vendorId;
		@NotBlank private String invoiceNumber;
		@NotNull @DecimalMin("0.01") private BigDecimal actualAmount;
	}

	@Getter @Setter
	public static class PayRequest {
		@NotNull private PaymentMethod paymentMethod;
		private String paymentReference;
		private LocalDate transactionDate;
	}

	@Getter @Setter
	public static class ApprovalActionRequest {
		private String actor;
		private String comment;
	}

}
