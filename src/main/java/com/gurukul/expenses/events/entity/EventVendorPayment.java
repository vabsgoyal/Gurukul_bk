package com.gurukul.expenses.events.entity;

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

import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "event_vendor_payment")
public class EventVendorPayment extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "request_id", nullable = false)
	private EventExpenseRequest request;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "vendor_id", nullable = false)
	private Vendor vendor;

	@Column(name = "transaction_id", nullable = false)
	private UUID transactionId;

}
