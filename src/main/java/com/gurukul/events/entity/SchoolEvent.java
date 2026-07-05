package com.gurukul.events.entity;

import com.gurukul.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "school_event")
public class SchoolEvent extends BaseEntity {

	@Column(nullable = false)
	private String name;

	@Column(length = 1000)
	private String description;

	@Column(name = "event_date", nullable = false)
	private LocalDate eventDate;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private EventStatus status;

	@Column(name = "inflow_enabled", nullable = false)
	private boolean inflowEnabled;

	@Column(name = "outflow_enabled", nullable = false)
	private boolean outflowEnabled;

}
