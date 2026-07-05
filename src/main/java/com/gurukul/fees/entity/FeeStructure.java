package com.gurukul.fees.entity;

import com.gurukul.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "fee_structure", uniqueConstraints = {
		@UniqueConstraint(columnNames = {"school_id", "class_section_id", "academic_year"})
})
public class FeeStructure extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "class_section_id", nullable = false)
	private com.gurukul.students.entity.ClassSection classSection;

	@Column(name = "academic_year", nullable = false)
	private String academicYear;

}
