package com.gurukul.schools.repository;

import com.gurukul.schools.entity.School;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SchoolRepository extends JpaRepository<School, UUID> {

	List<School> findAllByOrderByNameAsc();

	List<School> findAllByNameContainingIgnoreCaseOrderByNameAsc(String name);

}
