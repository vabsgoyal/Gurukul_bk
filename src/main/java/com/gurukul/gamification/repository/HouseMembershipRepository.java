package com.gurukul.gamification.repository;

import com.gurukul.gamification.entity.HouseMembership;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HouseMembershipRepository extends JpaRepository<HouseMembership, UUID> {

	@EntityGraph(attributePaths = "house")
	Optional<HouseMembership> findBySchoolIdAndStudentId(UUID schoolId, UUID studentId);

	List<HouseMembership> findAllBySchoolIdAndHouseId(UUID schoolId, UUID houseId);

	long countBySchoolIdAndHouseId(UUID schoolId, UUID houseId);

}
