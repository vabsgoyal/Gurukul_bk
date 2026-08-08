package com.gurukul.exams.repository;

import com.gurukul.exams.entity.GradingBand;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

public interface GradingBandRepository extends JpaRepository<GradingBand, UUID> {

	List<GradingBand> findAllBySchoolIdOrderByMinPercentageDesc(UUID schoolId);

	@Transactional
	void deleteAllBySchoolId(UUID schoolId);

}
