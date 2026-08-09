package com.gurukul.registration.repository;

import com.gurukul.registration.entity.TeacherInvite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TeacherInviteRepository extends JpaRepository<TeacherInvite, UUID> {

	Optional<TeacherInvite> findBySchoolIdAndCode(UUID schoolId, String code);

}
