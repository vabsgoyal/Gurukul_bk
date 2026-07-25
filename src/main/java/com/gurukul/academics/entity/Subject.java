package com.gurukul.academics.entity;

import com.gurukul.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "subject", uniqueConstraints = {
		@UniqueConstraint(columnNames = {"school_id", "code"})
})
public class Subject extends BaseEntity {

	@Column(nullable = false)
	private String code;

	@Column(nullable = false)
	private String name;

	@Column(length = 500)
	private String description;

}
