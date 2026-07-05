package com.gurukul.collections.entity;

import com.gurukul.common.BaseEntity;
import com.gurukul.events.entity.SchoolEvent;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "event_participation_fee", uniqueConstraints = {
		@UniqueConstraint(columnNames = {"event_id", "participant_type"})
})
public class EventParticipationFee extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "event_id", nullable = false)
	private SchoolEvent event;

	@Enumerated(EnumType.STRING)
	@Column(name = "participant_type", nullable = false)
	private ParticipantType participantType;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal amount;

}
