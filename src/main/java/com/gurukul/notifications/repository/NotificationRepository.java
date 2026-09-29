package com.gurukul.notifications.repository;

import com.gurukul.auth.entity.OwnerType;
import com.gurukul.notifications.entity.Notification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

	/** Slice, not Page - no COUNT(*) per page (see StudentRepository's note). */
	Slice<Notification> findAllBySchoolIdAndRecipientOwnerTypeAndRecipientOwnerIdOrderByCreatedAtDesc(
			UUID schoolId, OwnerType ownerType, UUID ownerId, Pageable pageable);

	long countBySchoolIdAndRecipientOwnerTypeAndRecipientOwnerIdAndReadAtIsNull(
			UUID schoolId, OwnerType ownerType, UUID ownerId);

	Optional<Notification> findByIdAndSchoolIdAndRecipientOwnerTypeAndRecipientOwnerId(
			UUID id, UUID schoolId, OwnerType ownerType, UUID ownerId);

	/** Which of these recipients already have an alert with this dedupe key. */
	@Query("SELECT n.recipientOwnerId FROM Notification n WHERE n.recipientOwnerType = :ownerType "
			+ "AND n.recipientOwnerId IN :ownerIds AND n.dedupeKey = :dedupeKey")
	List<UUID> findRecipientIdsWithDedupeKey(
			@Param("ownerType") OwnerType ownerType,
			@Param("ownerIds") Collection<UUID> ownerIds,
			@Param("dedupeKey") String dedupeKey);

	@Modifying
	@Query("UPDATE Notification n SET n.readAt = :now, n.updatedAt = :now WHERE n.schoolId = :schoolId "
			+ "AND n.recipientOwnerType = :ownerType AND n.recipientOwnerId = :ownerId AND n.readAt IS NULL")
	int markAllRead(
			@Param("schoolId") UUID schoolId,
			@Param("ownerType") OwnerType ownerType,
			@Param("ownerId") UUID ownerId,
			@Param("now") Instant now);

}
