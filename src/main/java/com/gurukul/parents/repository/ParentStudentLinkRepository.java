package com.gurukul.parents.repository;

import com.gurukul.parents.entity.ParentStudentLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ParentStudentLinkRepository extends JpaRepository<ParentStudentLink, UUID> {

	List<ParentStudentLink> findAllByParentId(UUID parentId);

	Optional<ParentStudentLink> findByParentIdAndStudentId(UUID parentId, UUID studentId);

	boolean existsByParentIdAndStudentId(UUID parentId, UUID studentId);

}
