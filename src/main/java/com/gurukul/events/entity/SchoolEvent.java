package com.gurukul.events.entity;

import com.gurukul.common.BaseEntity;
import com.gurukul.employees.entity.Employee;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * status/inflowEnabled/outflowEnabled are the original finance-lifecycle fields (gating
 * collections/expenses - see EventCollectionService/EventExpenseService) and are untouched by the
 * participation fields below. category/scope/participationType/etc. are all nullable: a
 * finance-only event (created without them) behaves exactly as before. cancelled is a separate
 * flag from status - "CLOSED" (finance done) and "cancelled" (participation called off) are
 * different lifecycles that can move independently.
 */
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

	@Enumerated(EnumType.STRING)
	private EventCategory category;

	/** Non-null only for events created through the participation flow (RSVP/registration/poll). */
	@Enumerated(EnumType.STRING)
	private EventScope scope;

	@Column(name = "section_id")
	private UUID sectionId;

	@Column(name = "class_name")
	private String className;

	private String venue;

	@Column(name = "start_at")
	private Instant startAt;

	@Column(name = "end_at")
	private Instant endAt;

	@Column(nullable = false)
	private boolean cancelled;

	@Enumerated(EnumType.STRING)
	@Column(name = "participation_type")
	private EventParticipationType participationType;

	/**
	 * JSON array of {key,label,required} field definitions, only meaningful when
	 * participationType == REGISTRATION. Plain TEXT + Jackson rather than JSONB, to stay portable
	 * between H2 (tests) and Postgres (prod).
	 */
	@Column(name = "registration_fields", columnDefinition = "TEXT")
	private String registrationFieldsJson;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "created_by_employee_id")
	private Employee createdByEmployee;

}
