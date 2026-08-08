package com.gurukul.academics.repository;

import com.gurukul.academics.entity.SectionSubjectTeacher;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SectionSubjectTeacherRepository extends JpaRepository<SectionSubjectTeacher, UUID> {

	@EntityGraph(attributePaths = {"subject", "teacher"})
	List<SectionSubjectTeacher> findAllBySectionId(UUID sectionId);

	@EntityGraph(attributePaths = {"section", "subject"})
	List<SectionSubjectTeacher> findAllByTeacherId(UUID teacherId);

	Optional<SectionSubjectTeacher> findBySectionIdAndSubjectIdAndTeacherId(UUID sectionId, UUID subjectId, UUID teacherId);

	boolean existsBySectionIdAndTeacherId(UUID sectionId, UUID teacherId);

}
