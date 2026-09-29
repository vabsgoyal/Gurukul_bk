package com.gurukul.idcards.entity;

import com.gurukul.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * The ID-card details a person enters on their own profile (photo, blood group, emergency contact).
 * One row per student/employee, created lazily on first edit - a missing row just means "nothing
 * filled in yet", and the card still renders with placeholders.
 */
@Getter
@Setter
@Entity
@Table(name = "id_card_profile", uniqueConstraints = {
		@UniqueConstraint(columnNames = {"school_id", "owner_type", "owner_id"})
})
public class IdCardProfile extends BaseEntity {

	@Enumerated(EnumType.STRING)
	@Column(name = "owner_type", nullable = false)
	private IdCardOwnerType ownerType;

	@Column(name = "owner_id", nullable = false)
	private UUID ownerId;

	@Column(name = "photo_object_key")
	private String photoObjectKey;

	@Column(name = "blood_group")
	private String bloodGroup;

	@Column(name = "emergency_contact_name")
	private String emergencyContactName;

	@Column(name = "emergency_phone")
	private String emergencyPhone;

	/** Username of whoever last edited it (the student, a linked parent, or the employee). */
	@Column(name = "updated_by")
	private String updatedBy;

}
