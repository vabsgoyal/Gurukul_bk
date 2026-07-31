package com.gurukul.gamification.repository;

import com.gurukul.gamification.entity.StudentGameProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface StudentGameProfileRepository extends JpaRepository<StudentGameProfile, UUID> {

	Optional<StudentGameProfile> findBySchoolIdAndStudentId(UUID schoolId, UUID studentId);

}
