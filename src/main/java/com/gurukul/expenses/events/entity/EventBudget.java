package com.gurukul.expenses.events.entity;

import com.gurukul.common.BaseEntity;
import com.gurukul.events.entity.SchoolEvent;
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
@Table(name = "event_budget", uniqueConstraints = @UniqueConstraint(columnNames = "event_id"))
public class EventBudget extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "event_id", nullable = false)
	private SchoolEvent event;

}
