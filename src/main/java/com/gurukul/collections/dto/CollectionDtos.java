package com.gurukul.collections.dto;

import com.gurukul.collections.entity.EventCollectionPayment;
import com.gurukul.collections.entity.EventParticipationFee;
import com.gurukul.collections.entity.ParticipantType;
import com.gurukul.finance.entity.PaymentMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public class CollectionDtos {

	@Getter
	@Setter
	@Schema(description = "Event participation fee request")
	public static class ParticipationFeeRequest {
		@NotNull
		private ParticipantType participantType;
		@NotNull
		@DecimalMin("0.01")
		private BigDecimal amount;
	}

	@Getter
	@AllArgsConstructor
	@Schema(description = "Event participation fee response")
	public static class ParticipationFeeResponse {
		private UUID id;
		private UUID eventId;
		private ParticipantType participantType;
		private BigDecimal amount;

		public static ParticipationFeeResponse from(EventParticipationFee fee) {
			return new ParticipationFeeResponse(fee.getId(), fee.getEvent().getId(), fee.getParticipantType(), fee.getAmount());
		}
	}

	@Getter
	@Setter
	@Schema(description = "Event collection payment request")
	public static class CollectionPaymentRequest {
		@NotBlank
		private String payerName;
		private String payerReference;
		@NotNull
		@DecimalMin("0.01")
		private BigDecimal amount;
		@NotNull
		private PaymentMethod paymentMethod;
		private String paymentReference;
		private LocalDate transactionDate;
	}

	@Getter
	@AllArgsConstructor
	@Schema(description = "Event collection payment response")
	public static class CollectionPaymentResponse {
		private UUID id;
		private UUID eventId;
		private String payerName;
		private BigDecimal amount;
		private String receiptNumber;
		private Instant createdAt;

		public static CollectionPaymentResponse from(EventCollectionPayment payment, String receiptNumber) {
			return new CollectionPaymentResponse(
					payment.getId(), payment.getEvent().getId(), payment.getPayerName(),
					payment.getAmount(), receiptNumber, payment.getCreatedAt());
		}
	}

	@Getter
	@AllArgsConstructor
	@Schema(description = "Event balance summary")
	public static class EventBalanceResponse {
		private UUID eventId;
		private BigDecimal totalCollections;
		private BigDecimal totalExpenses;
		private BigDecimal netBalance;
	}

}
