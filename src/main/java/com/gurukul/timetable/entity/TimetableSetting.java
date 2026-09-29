package com.gurukul.timetable.entity;

import com.gurukul.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Per-school timetable switches. Mon-Fri are always school days; Saturday is opt-in. */
@Getter
@Setter
@Entity
@Table(name = "timetable_setting")
public class TimetableSetting extends BaseEntity {

	@Column(name = "saturday_enabled", nullable = false)
	private boolean saturdayEnabled;

}
