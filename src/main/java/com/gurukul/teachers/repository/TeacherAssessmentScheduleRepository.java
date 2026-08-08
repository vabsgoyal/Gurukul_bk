package com.gurukul.teachers.repository;

import com.gurukul.teachers.entity.AssessmentStatus;
import com.gurukul.teachers.entity.TeacherAssessmentSchedule;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TeacherAssessmentScheduleRepository extends JpaRepository<TeacherAssessmentSchedule, UUID> {

	@EntityGraph(attributePaths = {"teacher", "classSection"})
	List<TeacherAssessmentSchedule> findAllBySchoolIdAndTeacherIdOrderByScheduledAtAsc(UUID schoolId, UUID teacherId);

	@EntityGraph(attributePaths = {"teacher", "classSection"})
	List<TeacherAssessmentSchedule> findAllBySchoolIdAndClassSectionIdOrderByScheduledAtAsc(UUID schoolId, UUID classSectionId);

	@EntityGraph(attributePaths = {"teacher", "classSection"})
	Optional<TeacherAssessmentSchedule> findByIdAndSchoolId(UUID id, UUID schoolId);

	void deleteAllBySchoolIdAndTeacherId(UUID schoolId, UUID teacherId);

	long countBySchoolId(UUID schoolId);

	long countBySchoolIdAndStatusAndScheduledAtGreaterThanEqual(
			UUID schoolId, AssessmentStatus status, LocalDateTime scheduledAt);

}
