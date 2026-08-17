package com.gurukul.fees.repository;

import com.gurukul.fees.entity.FeeAssessmentStatus;
import com.gurukul.fees.entity.StudentFeeAssessment;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StudentFeeAssessmentRepository extends JpaRepository<StudentFeeAssessment, UUID> {

	/** student is a LAZY @ManyToOne, and FeeAssessmentResponse.from() reads student.getName()/
	 *  getRollNumber() per row - every list-returning query here needs it eager-fetched, or every
	 *  row (typically a distinct student, unlike the class-section case) triggers its own separate
	 *  lazy-load round-trip. */
	@EntityGraph(attributePaths = "student")
	List<StudentFeeAssessment> findAllBySchoolIdAndStatus(UUID schoolId, FeeAssessmentStatus status);

	@EntityGraph(attributePaths = "student")
	List<StudentFeeAssessment> findAllBySchoolId(UUID schoolId);

	@EntityGraph(attributePaths = "student")
	List<StudentFeeAssessment> findAllBySchoolIdAndStudentId(UUID schoolId, UUID studentId);

	@EntityGraph(attributePaths = "student")
	List<StudentFeeAssessment> findAllBySchoolIdAndStudent_ClassSection_Id(UUID schoolId, UUID classSectionId);

	/** Paginated variants for the public listAssessments endpoint - one per optional-filter combination,
	 *  so the classSectionId filter runs in the DB query instead of in-memory (which would otherwise
	 *  break page boundaries: filtering after paging would return incomplete/short pages). Slice, not
	 *  Page: avoids Spring Data's automatic separate COUNT(*) query on every page - see
	 *  StudentRepository's equivalent note. Total count fetched separately only on page 0, via the
	 *  matching countXxx method below. */
	@EntityGraph(attributePaths = "student")
	Slice<StudentFeeAssessment> findAllBySchoolId(UUID schoolId, Pageable pageable);

	@EntityGraph(attributePaths = "student")
	Slice<StudentFeeAssessment> findAllBySchoolIdAndStatus(UUID schoolId, FeeAssessmentStatus status, Pageable pageable);

	@EntityGraph(attributePaths = "student")
	Slice<StudentFeeAssessment> findAllBySchoolIdAndStudent_ClassSection_Id(UUID schoolId, UUID classSectionId, Pageable pageable);

	@EntityGraph(attributePaths = "student")
	Slice<StudentFeeAssessment> findAllBySchoolIdAndStatusAndStudent_ClassSection_Id(
			UUID schoolId, FeeAssessmentStatus status, UUID classSectionId, Pageable pageable);

	long countBySchoolId(UUID schoolId);

	long countBySchoolIdAndStatus(UUID schoolId, FeeAssessmentStatus status);

	long countBySchoolIdAndStudent_ClassSection_Id(UUID schoolId, UUID classSectionId);

	long countBySchoolIdAndStatusAndStudent_ClassSection_Id(UUID schoolId, FeeAssessmentStatus status, UUID classSectionId);

	Optional<StudentFeeAssessment> findByIdAndSchoolId(UUID id, UUID schoolId);

	Optional<StudentFeeAssessment> findBySchoolIdAndStudentIdAndAcademicYear(
			UUID schoolId, UUID studentId, String academicYear);

}
