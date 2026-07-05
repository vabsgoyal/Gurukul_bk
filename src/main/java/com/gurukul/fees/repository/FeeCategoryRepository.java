package com.gurukul.fees.repository;

import com.gurukul.fees.entity.FeeCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FeeCategoryRepository extends JpaRepository<FeeCategory, UUID> {

	List<FeeCategory> findAllBySchoolIdOrderByCodeAsc(UUID schoolId);

	Optional<FeeCategory> findByIdAndSchoolId(UUID id, UUID schoolId);

	Optional<FeeCategory> findBySchoolIdAndCode(UUID schoolId, String code);

}
