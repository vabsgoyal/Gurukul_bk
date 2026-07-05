package com.gurukul.sponsorships.entity;

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
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "sponsorship_payment")
public class SponsorshipPayment extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "sponsorship_id", nullable = false)
	private Sponsorship sponsorship;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal amount;

	@Column(name = "transaction_id", nullable = false)
	private UUID transactionId;

}
