package com.gurukul.gamification.repository;

import com.gurukul.gamification.entity.QuizQuestion;
import com.gurukul.gamification.entity.QuizQuestionType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Arena games (challenges, battle rooms, practice) are tap-one-of-four, so their question pools
 * must come from the ...AndQuestionType finders called with MCQ - never the unfiltered ones, which
 * also return NUMERIC / SHORT_WORD questions that have no options. The unfiltered finders are for
 * the teacher-facing question-bank list.
 */
public interface QuizQuestionRepository extends JpaRepository<QuizQuestion, UUID> {

	List<QuizQuestion> findAllBySchoolIdAndSubjectId(UUID schoolId, UUID subjectId);

	long countBySchoolIdAndSubjectId(UUID schoolId, UUID subjectId);

	List<QuizQuestion> findAllBySchoolIdAndSubjectIdAndClassName(UUID schoolId, UUID subjectId, String className);

	List<QuizQuestion> findAllBySchoolIdAndSubjectIdAndClassNameAndCreatedByTeacherId(
			UUID schoolId, UUID subjectId, String className, UUID createdByTeacherId);

	List<QuizQuestion> findAllBySchoolIdAndSubjectIdAndQuestionType(UUID schoolId, UUID subjectId, QuizQuestionType questionType);

	List<QuizQuestion> findAllBySchoolIdAndSubjectIdAndClassNameAndQuestionType(
			UUID schoolId, UUID subjectId, String className, QuizQuestionType questionType);

}
