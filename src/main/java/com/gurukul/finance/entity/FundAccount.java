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
@Table(name = "fund_account", uniqueConstraints = {
		@UniqueConstraint(columnNames = {"school_id", "code"})
})
public class FundAccount extends BaseEntity {

	@Column(nullable = false)
	private String code;

	@Column(nullable = false)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(name = "account_type", nullable = false)
	private FundAccountType accountType;

}
