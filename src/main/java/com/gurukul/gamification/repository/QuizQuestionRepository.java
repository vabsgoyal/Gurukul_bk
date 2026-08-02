package com.gurukul.gamification.repository;

import com.gurukul.gamification.entity.QuizQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface QuizQuestionRepository extends JpaRepository<QuizQuestion, UUID> {

	List<QuizQuestion> findAllBySchoolIdAndSubjectId(UUID schoolId, UUID subjectId);

	long countBySchoolIdAndSubjectId(UUID schoolId, UUID subjectId);

}
