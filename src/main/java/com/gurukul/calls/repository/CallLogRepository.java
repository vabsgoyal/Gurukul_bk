package com.gurukul.calls.repository;

import com.gurukul.auth.entity.OwnerType;
import com.gurukul.calls.entity.CallLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CallLogRepository extends JpaRepository<CallLog, UUID> {

	Optional<CallLog> findByIdAndSchoolId(UUID id, UUID schoolId);

	List<CallLog> findAllBySchoolIdOrderByStartedAtDesc(UUID schoolId);

	/** Slice, not Page: avoids Spring Data's automatic separate COUNT(*) query on every page - see
	 *  StudentRepository's equivalent note. Total count fetched separately only on page 0, via
	 *  countBySchoolId/countForParticipant below. */
	Slice<CallLog> findAllBySchoolIdOrderByStartedAtDesc(UUID schoolId, Pageable pageable);

	long countBySchoolId(UUID schoolId);

	@Query("SELECT c FROM CallLog c WHERE c.schoolId = :schoolId AND ("
			+ "(c.callerOwnerType = :ownerType AND c.callerOwnerId = :ownerId) "
			+ "OR (c.calleeOwnerType = :ownerType AND c.calleeOwnerId = :ownerId)) "
			+ "ORDER BY c.startedAt DESC")
	List<CallLog> findAllForParticipant(
			@Param("schoolId") UUID schoolId,
			@Param("ownerType") OwnerType ownerType,
			@Param("ownerId") UUID ownerId);

	@Query("SELECT c FROM CallLog c WHERE c.schoolId = :schoolId AND ("
			+ "(c.callerOwnerType = :ownerType AND c.callerOwnerId = :ownerId) "
			+ "OR (c.calleeOwnerType = :ownerType AND c.calleeOwnerId = :ownerId)) "
			+ "ORDER BY c.startedAt DESC")
	Slice<CallLog> findAllForParticipant(
			@Param("schoolId") UUID schoolId,
			@Param("ownerType") OwnerType ownerType,
			@Param("ownerId") UUID ownerId,
			Pageable pageable);

	@Query("SELECT COUNT(c) FROM CallLog c WHERE c.schoolId = :schoolId AND ("
			+ "(c.callerOwnerType = :ownerType AND c.callerOwnerId = :ownerId) "
			+ "OR (c.calleeOwnerType = :ownerType AND c.calleeOwnerId = :ownerId))")
	long countForParticipant(
			@Param("schoolId") UUID schoolId,
			@Param("ownerType") OwnerType ownerType,
			@Param("ownerId") UUID ownerId);

}
