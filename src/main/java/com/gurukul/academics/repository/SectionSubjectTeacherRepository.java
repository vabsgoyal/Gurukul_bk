package com.gurukul.academics.repository;

import com.gurukul.academics.entity.SectionSubjectTeacher;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SectionSubjectTeacherRepository extends JpaRepository<SectionSubjectTeacher, UUID> {

	List<SectionSubjectTeacher> findAllBySectionId(UUID sectionId);

	Optional<SectionSubjectTeacher> findBySectionIdAndSubjectIdAndTeacherId(UUID sectionId, UUID subjectId, UUID teacherId);

}
