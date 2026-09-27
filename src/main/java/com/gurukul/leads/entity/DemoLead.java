package com.gurukul.leads.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A demo request submitted from the public marketing site. Deliberately not a {@code BaseEntity}:
 * a prospect has no school in the system yet, so there is no school_id to scope it to.
 */
@Getter
@Setter
@Entity
@Table(name = "demo_lead")
public class DemoLead {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false, length = 100)
	private String name;

	@Column(name = "school_name", nullable = false, length = 150)
	private String schoolName;

	@Column(length = 60)
	private String role;

	@Column(nullable = false, length = 20)
	private String phone;

	@Column(length = 150)
	private String email;

	@Column(length = 80)
	private String city;

	@Column(length = 80)
	private String state;

	@Column(name = "student_count", length = 20)
	private String studentCount;

	@Column(length = 1000)
	private String message;

	@Column(name = "source_page", length = 200)
	private String sourcePage;

	/** Salted SHA-256 of the submitter IP - the raw IP is never stored. */
	@Column(name = "ip_hash", nullable = false, length = 64)
	private String ipHash;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@PrePersist
	protected void onCreate() {
		createdAt = Instant.now();
	}

}
