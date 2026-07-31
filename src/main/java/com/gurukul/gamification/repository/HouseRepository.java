package com.gurukul.gamification.repository;

import com.gurukul.gamification.entity.House;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HouseRepository extends JpaRepository<House, UUID> {

	List<House> findAllBySchoolIdOrderByNameAsc(UUID schoolId);

	Optional<House> findByIdAndSchoolId(UUID id, UUID schoolId);

	boolean existsBySchoolIdAndName(UUID schoolId, String name);

}
