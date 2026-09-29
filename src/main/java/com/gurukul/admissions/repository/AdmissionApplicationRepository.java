package com.gurukul.admissions.repository;

import com.gurukul.admissions.entity.AdmissionApplication;
import com.gurukul.admissions.entity.AdmissionStage;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AdmissionApplicationRepository extends JpaRepository<AdmissionApplication, UUID> {

	List<AdmissionApplication> findAllBySchoolIdOrderByCreatedAtDesc(UUID schoolId);

	List<AdmissionApplication> findAllBySchoolIdAndStageOrderByCreatedAtDesc(UUID schoolId, AdmissionStage stage);

	Optional<AdmissionApplication> findByIdAndSchoolId(UUID id, UUID schoolId);

	/**
	 * Row lock for enrolment: a double-tapped "Enrol" (or two admins at once) queues on this lock, and
	 * the second one then sees ENROLLED and returns the first one's student instead of creating another.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select a from AdmissionApplication a where a.id = :id and a.schoolId = :schoolId")
	Optional<AdmissionApplication> findForUpdate(@Param("id") UUID id, @Param("schoolId") UUID schoolId);

}
