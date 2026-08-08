package com.gurukul.notifications.repository;

import com.gurukul.auth.entity.OwnerType;
import com.gurukul.notifications.entity.DeviceToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeviceTokenRepository extends JpaRepository<DeviceToken, UUID> {

	Optional<DeviceToken> findByExpoPushToken(String expoPushToken);

	List<DeviceToken> findAllBySchoolIdAndOwnerTypeAndOwnerId(UUID schoolId, OwnerType ownerType, UUID ownerId);

	List<DeviceToken> findAllBySchoolIdAndOwnerTypeAndOwnerIdIn(UUID schoolId, OwnerType ownerType, List<UUID> ownerIds);

}
