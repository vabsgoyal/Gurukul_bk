package com.gurukul.schools.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "school")
public class School {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false)
	private String name;

	@Column(nullable = false)
	private String address;

	@Column(nullable = false)
	private String city;

	@Column(nullable = false)
	private String state;

	@Column(nullable = false)
	private String pincode;

	@Column(name = "contact_email", nullable = false)
	private String contactEmail;

	@Column(name = "contact_phone", nullable = false)
	private String contactPhone;

	@Column(name = "principal_name", nullable = false)
	private String principalName;

	@Column(name = "director_name", nullable = false)
	private String directorName;

	/** Bank details students pay fees into. Null until an admin sets them in Fee Payment Settings. */
	@Column(name = "bank_account_number")
	private String bankAccountNumber;

	@Column(name = "bank_ifsc")
	private String bankIfsc;

	@Column(name = "bank_account_holder_name")
	private String bankAccountHolderName;

	/**
	 * Optional real UPI VPA to use as-is for the payment intent instead of deriving one from
	 * bankAccountNumber+bankIfsc. The derived VPA is a best-effort guess (accountNumber@bankHandle)
	 * that may not resolve to a real payee - this field lets an admin enter their bank's actual
	 * issued VPA directly for a guaranteed-valid destination, e.g. for testing against a real account.
	 */
	@Column(name = "upi_vpa_override")
	private String upiVpaOverride;

	/** Null until an admin configures the school's geofence via PUT /api/v1/schools/{id}/location. */
	private Double latitude;

	private Double longitude;

	@Column(name = "geofence_radius_meters", nullable = false)
	private Integer geofenceRadiusMeters = 100;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@Column(nullable = false)
	private Instant updatedAt;

	@PrePersist
	protected void onCreate() {
		Instant now = Instant.now();
		createdAt = now;
		updatedAt = now;
	}

	@PreUpdate
	protected void onUpdate() {
		updatedAt = Instant.now();
	}

}
