package com.gurukul.attendance.repository;

import com.gurukul.attendance.entity.AttendanceIdentifier;
import com.gurukul.attendance.entity.AttendanceMethod;
import com.gurukul.auth.entity.OwnerType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AttendanceIdentifierRepository extends JpaRepository<AttendanceIdentifier, UUID> {

	List<AttendanceIdentifier> findAllBySchoolIdAndOwnerTypeAndOwnerId(UUID schoolId, OwnerType ownerType, UUID ownerId);

	Optional<AttendanceIdentifier> findBySchoolIdAndMethodAndExternalIdAndActiveTrue(
			UUID schoolId, AttendanceMethod method, String externalId);

	boolean existsBySchoolIdAndMethodAndExternalIdAndActiveTrue(UUID schoolId, AttendanceMethod method, String externalId);

	boolean existsBySchoolIdAndOwnerTypeAndOwnerIdAndMethodAndActiveTrue(
			UUID schoolId, OwnerType ownerType, UUID ownerId, AttendanceMethod method);

	Optional<AttendanceIdentifier> findByIdAndSchoolId(UUID id, UUID schoolId);

}
