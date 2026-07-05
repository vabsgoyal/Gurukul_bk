package com.gurukul.events.repository;

import com.gurukul.events.entity.SchoolEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventRepository extends JpaRepository<SchoolEvent, UUID> {

	List<SchoolEvent> findAllBySchoolIdOrderByEventDateDesc(UUID schoolId);

	Optional<SchoolEvent> findByIdAndSchoolId(UUID id, UUID schoolId);

}
