package com.gurukul.finance.entity;

import com.gurukul.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "receipt_sequence", uniqueConstraints = {
		@UniqueConstraint(columnNames = {"school_id", "sequence_type", "academic_year"})
})
public class ReceiptSequence extends BaseEntity {

	@Enumerated(EnumType.STRING)
	@Column(name = "sequence_type", nullable = false)
	private ReceiptSequenceType sequenceType;

	@Column(name = "academic_year", nullable = false)
	private String academicYear;

	@Column(name = "last_value", nullable = false)
	private long lastValue;

}
