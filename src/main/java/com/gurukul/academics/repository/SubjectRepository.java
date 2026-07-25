package com.gurukul.academics.repository;

import com.gurukul.academics.entity.Subject;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubjectRepository extends JpaRepository<Subject, UUID> {

	List<Subject> findAllBySchoolIdOrderByCodeAsc(UUID schoolId);

	Optional<Subject> findByIdAndSchoolId(UUID id, UUID schoolId);

	Optional<Subject> findBySchoolIdAndCode(UUID schoolId, String code);

}
