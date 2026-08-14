package com.gurukul.fees.repository;

import com.gurukul.fees.entity.FeeAssessmentStatus;
import com.gurukul.fees.entity.StudentFeeAssessment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StudentFeeAssessmentRepository extends JpaRepository<StudentFeeAssessment, UUID> {

	List<StudentFeeAssessment> findAllBySchoolIdAndStatus(UUID schoolId, FeeAssessmentStatus status);

	List<StudentFeeAssessment> findAllBySchoolId(UUID schoolId);

	List<StudentFeeAssessment> findAllBySchoolIdAndStudentId(UUID schoolId, UUID studentId);

	List<StudentFeeAssessment> findAllBySchoolIdAndStudent_ClassSection_Id(UUID schoolId, UUID classSectionId);

	/** Paginated variants for the public listAssessments endpoint - one per optional-filter combination,
	 *  so the classSectionId filter runs in the DB query instead of in-memory (which would otherwise
	 *  break page boundaries: filtering after paging would return incomplete/short pages). */
	Page<StudentFeeAssessment> findAllBySchoolId(UUID schoolId, Pageable pageable);

	Page<StudentFeeAssessment> findAllBySchoolIdAndStatus(UUID schoolId, FeeAssessmentStatus status, Pageable pageable);

	Page<StudentFeeAssessment> findAllBySchoolIdAndStudent_ClassSection_Id(UUID schoolId, UUID classSectionId, Pageable pageable);

	Page<StudentFeeAssessment> findAllBySchoolIdAndStatusAndStudent_ClassSection_Id(
			UUID schoolId, FeeAssessmentStatus status, UUID classSectionId, Pageable pageable);

	Optional<StudentFeeAssessment> findByIdAndSchoolId(UUID id, UUID schoolId);

	Optional<StudentFeeAssessment> findBySchoolIdAndStudentIdAndAcademicYear(
			UUID schoolId, UUID studentId, String academicYear);

}
