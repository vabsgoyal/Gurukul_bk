package com.gurukul.sponsorships.entity;

import com.gurukul.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "sponsor")
public class Sponsor extends BaseEntity {

	@Column(nullable = false)
	private String name;

	@Column(name = "contact_phone")
	private String contactPhone;

	@Column(name = "contact_email")
	private String contactEmail;

	@Column(length = 20)
	private String pan;

}
