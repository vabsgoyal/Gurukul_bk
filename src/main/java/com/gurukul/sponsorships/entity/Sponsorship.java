package com.gurukul.sponsorships.entity;

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
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "sponsorship")
public class Sponsorship extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "sponsor_id", nullable = false)
	private Sponsor sponsor;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private SponsorshipPurpose purpose;

	@Column(name = "pledged_amount", nullable = false, precision = 12, scale = 2)
	private BigDecimal pledgedAmount;

	@Column(name = "fund_account_id")
	private UUID fundAccountId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private SponsorshipStatus status;

}
