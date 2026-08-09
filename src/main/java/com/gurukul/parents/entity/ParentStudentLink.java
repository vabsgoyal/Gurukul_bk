package com.gurukul.parents.entity;

import com.gurukul.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/** One row per (parent, child) - a parent with siblings at the same school has multiple rows. */
@Getter
@Setter
@Entity
@Table(name = "parent_student_link", uniqueConstraints = {
		@UniqueConstraint(columnNames = {"parent_id", "student_id"})
})
public class ParentStudentLink extends BaseEntity {

	@Column(name = "parent_id", nullable = false)
	private UUID parentId;

	@Column(name = "student_id", nullable = false)
	private UUID studentId;

}
