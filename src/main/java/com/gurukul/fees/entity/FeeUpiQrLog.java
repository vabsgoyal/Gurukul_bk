package com.gurukul.fees.entity;

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

/**
 * Insert-only audit trail of every UPI QR generated for a fee assessment. There is no gateway
 * behind this yet, so nothing here ever transitions to a "confirmed" state - the principal
 * verifies the payment landed in their own bank/UPI app and records it via the normal
 * FeePayment flow. This table exists purely so "was a QR generated, for how much, when" is
 * traceable later.
 */
@Getter
@Setter
@Entity
@Table(name = "fee_upi_qr_log")
public class FeeUpiQrLog extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "assessment_id", nullable = false)
	private StudentFeeAssessment assessment;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal amount;

	@Column(name = "reference_id", nullable = false, unique = true)
	private String referenceId;

	@Column(name = "upi_vpa", nullable = false)
	private String upiVpa;

	@Column(name = "payee_name", nullable = false)
	private String payeeName;

}
