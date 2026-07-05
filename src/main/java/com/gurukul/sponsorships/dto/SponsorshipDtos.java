package com.gurukul.sponsorships.dto;

import com.gurukul.finance.entity.PaymentMethod;
import com.gurukul.sponsorships.entity.Sponsor;
import com.gurukul.sponsorships.entity.Sponsorship;
import com.gurukul.sponsorships.entity.SponsorshipPayment;
import com.gurukul.sponsorships.entity.SponsorshipPurpose;
import com.gurukul.sponsorships.entity.SponsorshipStatus;
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

public class SponsorshipDtos {

	@Getter @Setter
	public static class SponsorRequest {
		@NotBlank private String name;
		private String contactPhone;
		private String contactEmail;
		private String pan;
	}

	@Getter @AllArgsConstructor
	public static class SponsorResponse {
		private UUID id;
		private String name;
		private String contactPhone;
		private String contactEmail;
		private String pan;
		private Instant createdAt;

		public static SponsorResponse from(Sponsor s) {
			return new SponsorResponse(s.getId(), s.getName(), s.getContactPhone(), s.getContactEmail(), s.getPan(), s.getCreatedAt());
		}
	}

	@Getter @Setter
	public static class SponsorshipRequest {
		@NotNull private UUID sponsorId;
		@NotNull private SponsorshipPurpose purpose;
		@NotNull @DecimalMin("0.01") private BigDecimal pledgedAmount;
		private UUID fundAccountId;
	}

	@Getter @AllArgsConstructor
	public static class SponsorshipResponse {
		private UUID id;
		private UUID sponsorId;
		private String sponsorName;
		private SponsorshipPurpose purpose;
		private BigDecimal pledgedAmount;
		private BigDecimal receivedAmount;
		private SponsorshipStatus status;
		private UUID fundAccountId;

		public static SponsorshipResponse from(Sponsorship s, BigDecimal received) {
			return new SponsorshipResponse(s.getId(), s.getSponsor().getId(), s.getSponsor().getName(),
					s.getPurpose(), s.getPledgedAmount(), received, s.getStatus(), s.getFundAccountId());
		}
	}

	@Getter @Setter
	public static class SponsorshipPaymentRequest {
		@NotNull @DecimalMin("0.01") private BigDecimal amount;
		@NotNull private PaymentMethod paymentMethod;
		private String paymentReference;
		private LocalDate transactionDate;
	}

	@Getter @AllArgsConstructor
	public static class SponsorshipPaymentResponse {
		private UUID id;
		private UUID sponsorshipId;
		private BigDecimal amount;
		private String receiptNumber;
		private Instant createdAt;

		public static SponsorshipPaymentResponse from(SponsorshipPayment p, String receiptNumber) {
			return new SponsorshipPaymentResponse(p.getId(), p.getSponsorship().getId(), p.getAmount(), receiptNumber, p.getCreatedAt());
		}
	}

}
