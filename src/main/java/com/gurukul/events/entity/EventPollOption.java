package com.gurukul.events.entity;

import com.gurukul.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "event_poll_option")
public class EventPollOption extends BaseEntity {

	@Column(name = "event_id", nullable = false)
	private UUID eventId;

	@Column(nullable = false)
	private String label;

}
