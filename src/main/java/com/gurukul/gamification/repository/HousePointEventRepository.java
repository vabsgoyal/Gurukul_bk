package com.gurukul.gamification.repository;

import com.gurukul.gamification.entity.HousePointEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface HousePointEventRepository extends JpaRepository<HousePointEvent, UUID> {

	@Query("SELECT COALESCE(SUM(e.amount), 0) FROM HousePointEvent e WHERE e.house.id = :houseId")
	long sumAmountForHouse(@Param("houseId") UUID houseId);

	List<HousePointEvent> findTop15BySchoolIdOrderByCreatedAtDesc(UUID schoolId);

}
