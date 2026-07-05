package com.gurukul.fees.repository;

import com.gurukul.fees.entity.FeeStructure;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FeeStructureRepository extends JpaRepository<FeeStructure, UUID> {

	List<FeeStructure> findAllBySchoolId(UUID schoolId);

	Optional<FeeStructure> findByIdAndSchoolId(UUID id, UUID schoolId);

	Optional<FeeStructure> findBySchoolIdAndClassSectionIdAndAcademicYear(
			UUID schoolId, UUID classSectionId, String academicYear);

}
