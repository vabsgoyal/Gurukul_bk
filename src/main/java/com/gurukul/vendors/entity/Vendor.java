package com.gurukul.vendors.entity;

import com.gurukul.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "vendor")
public class Vendor extends BaseEntity {

	@Column(nullable = false)
	private String name;

	@Column(name = "contact_phone")
	private String contactPhone;

	@Column(name = "contact_email")
	private String contactEmail;

	@Column(name = "bank_account")
	private String bankAccount;

	@Column(name = "upi_id")
	private String upiId;

	@Column(length = 500)
	private String address;

}
