package com.gurukul.collections.entity;

import com.gurukul.common.BaseEntity;
import com.gurukul.events.entity.SchoolEvent;
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
@Table(name = "event_collection_payment")
public class EventCollectionPayment extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "event_id", nullable = false)
	private SchoolEvent event;

	@Column(name = "payer_name", nullable = false)
	private String payerName;

	@Column(name = "payer_reference")
	private String payerReference;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal amount;

	@Column(name = "transaction_id", nullable = false)
	private UUID transactionId;

}
