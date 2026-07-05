package com.gurukul.fees.repository;

import com.gurukul.fees.entity.FeeAssessmentStatus;
import com.gurukul.fees.entity.StudentFeeAssessment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StudentFeeAssessmentRepository extends JpaRepository<StudentFeeAssessment, UUID> {

	List<StudentFeeAssessment> findAllBySchoolIdAndStatus(UUID schoolId, FeeAssessmentStatus status);

	List<StudentFeeAssessment> findAllBySchoolId(UUID schoolId);

	List<StudentFeeAssessment> findAllBySchoolIdAndStudentId(UUID schoolId, UUID studentId);

	Optional<StudentFeeAssessment> findByIdAndSchoolId(UUID id, UUID schoolId);

	Optional<StudentFeeAssessment> findBySchoolIdAndStudentIdAndAcademicYear(
			UUID schoolId, UUID studentId, String academicYear);

}
