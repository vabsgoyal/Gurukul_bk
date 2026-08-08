package com.gurukul.notifications.entity;

import com.gurukul.auth.entity.OwnerType;
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
 * One row per device a user is logged in on (the same owner can have several - phone + tablet).
 * expoPushToken is unique across the whole table, not scoped per owner: if the same physical
 * device later logs in as a different owner (e.g. someone else borrows the phone), re-registering
 * reassigns the row rather than leaving two rows racing to receive the same device's pushes.
 */
@Getter
@Setter
@Entity
@Table(name = "device_token", uniqueConstraints = {
		@UniqueConstraint(columnNames = {"expo_push_token"})
})
public class DeviceToken extends BaseEntity {

	@Enumerated(EnumType.STRING)
	@Column(name = "owner_type", nullable = false)
	private OwnerType ownerType;

	@Column(name = "owner_id", nullable = false)
	private UUID ownerId;

	@Column(name = "expo_push_token", nullable = false)
	private String expoPushToken;

}
