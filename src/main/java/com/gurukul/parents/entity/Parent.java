package com.gurukul.parents.entity;

import com.gurukul.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Scoped to one school, like every other entity here - a parent with children at two schools has
 * two separate Parent rows (and two separate Credentials), the same way a teacher employed at two
 * schools would have two separate Employee records. This deliberately avoids a cross-school
 * identity model, which would require rethinking the JWT (schoolId is baked into every token).
 */
@Getter
@Setter
@Entity
@Table(name = "parent")
public class Parent extends BaseEntity {

	@Column(nullable = false)
	private String name;

	private String email;

	private String phone;

}
