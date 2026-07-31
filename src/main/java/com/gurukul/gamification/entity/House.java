package com.gurukul.gamification.entity;

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
@Table(name = "house", uniqueConstraints = {
		@UniqueConstraint(columnNames = {"school_id", "name"})
})
public class House extends BaseEntity {

	@Column(nullable = false)
	private String name;

	@Column(name = "color_hex", nullable = false)
	private String colorHex;

}
