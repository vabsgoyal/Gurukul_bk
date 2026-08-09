package com.gurukul.parents.repository;

import com.gurukul.parents.entity.Parent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ParentRepository extends JpaRepository<Parent, UUID> {

	Optional<Parent> findByIdAndSchoolId(UUID id, UUID schoolId);

}
