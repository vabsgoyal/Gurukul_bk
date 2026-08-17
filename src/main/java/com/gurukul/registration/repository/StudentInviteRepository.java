package com.gurukul.registration.repository;

import com.gurukul.registration.entity.StudentInvite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface StudentInviteRepository extends JpaRepository<StudentInvite, UUID> {

	Optional<StudentInvite> findBySchoolIdAndCode(UUID schoolId, String code);

	Optional<StudentInvite> findBySchoolIdAndTargetStudentIdAndUsedFalse(UUID schoolId, UUID targetStudentId);

}
