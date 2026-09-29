package com.gurukul.timetable.repository;

import com.gurukul.timetable.entity.PeriodDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface PeriodDefinitionRepository extends JpaRepository<PeriodDefinition, UUID> {

	List<PeriodDefinition> findAllBySchoolIdOrderByPeriodNumberAsc(UUID schoolId);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("DELETE FROM PeriodDefinition p WHERE p.schoolId = :schoolId")
	void deleteAllBySchoolId(@Param("schoolId") UUID schoolId);

}
