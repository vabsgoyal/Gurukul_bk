package com.gurukul.registration.repository;

import com.gurukul.registration.entity.TeacherInvite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TeacherInviteRepository extends JpaRepository<TeacherInvite, UUID> {

	Optional<TeacherInvite> findBySchoolIdAndCode(UUID schoolId, String code);

	boolean existsBySchoolIdAndTargetEmployeeIdAndUsedFalse(UUID schoolId, UUID targetEmployeeId);

	/** Used so admin can re-view/re-share a previously generated invite - the create response is otherwise the only place it's shown. */
	Optional<TeacherInvite> findFirstBySchoolIdAndTargetEmployeeIdOrderByCreatedAtDesc(UUID schoolId, UUID targetEmployeeId);

}
