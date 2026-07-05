package com.gurukul.expenses.infrastructure.entity;

import com.gurukul.common.BaseEntity;
import com.gurukul.vendors.entity.Vendor;
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
@Table(name = "infra_purchase_record")
public class InfraPurchaseRecord extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "request_id", nullable = false)
	private InfraExpenseRequest request;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "vendor_id", nullable = false)
	private Vendor vendor;

	@Column(name = "invoice_number", nullable = false)
	private String invoiceNumber;

	@Column(name = "actual_amount", nullable = false, precision = 12, scale = 2)
	private BigDecimal actualAmount;

}
