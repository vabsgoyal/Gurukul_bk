package com.gurukul.fees.service;

import com.gurukul.common.EntityNotFoundException;
import com.gurukul.common.SchoolContext;
import com.gurukul.fees.dto.FeeAssessmentResponse;
import com.gurukul.fees.dto.FeeStructureRequest;
import com.gurukul.fees.dto.FeeStructureResponse;
import com.gurukul.fees.entity.FeeAssessmentStatus;
import com.gurukul.fees.entity.FeeStructure;
import com.gurukul.fees.entity.FeeStructureLine;
import com.gurukul.fees.entity.StudentFeeAssessment;
import com.gurukul.fees.repository.FeeStructureLineRepository;
import com.gurukul.fees.repository.FeeStructureRepository;
import com.gurukul.fees.repository.StudentFeeAssessmentRepository;
import com.gurukul.students.entity.ClassSection;
import com.gurukul.students.entity.Student;
import com.gurukul.students.entity.StudentStatus;
import com.gurukul.students.repository.StudentRepository;
import com.gurukul.students.service.ClassSectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FeeStructureService {

	private final FeeStructureRepository feeStructureRepository;
	private final FeeStructureLineRepository feeStructureLineRepository;
	private final StudentFeeAssessmentRepository assessmentRepository;
	private final StudentRepository studentRepository;
	private final FeeCategoryService feeCategoryService;
	private final ClassSectionService classSectionService;
	private final SchoolContext schoolContext;

	@Transactional(readOnly = true)
	public List<FeeStructureResponse> list() {
		return feeStructureRepository.findAllBySchoolId(schoolContext.getSchoolId()).stream()
				.map(this::toResponse)
				.toList();
	}

	@Transactional(readOnly = true)
	public FeeStructureResponse getById(UUID id) {
		return toResponse(findScoped(id));
	}

	@Transactional
	public FeeStructureResponse create(FeeStructureRequest request) {
		UUID schoolId = schoolContext.getSchoolId();
		ClassSection classSection = classSectionService.getScopedClassSection(request.getClassSectionId());

		if (feeStructureRepository.findBySchoolIdAndClassSectionIdAndAcademicYear(
				schoolId, classSection.getId(), request.getAcademicYear()).isPresent()) {
			throw new IllegalArgumentException("Fee structure already exists for this class-section and academic year");
		}

		FeeStructure structure = new FeeStructure();
		structure.setSchoolId(schoolId);
		structure.setClassSection(classSection);
		structure.setAcademicYear(request.getAcademicYear());
		structure = feeStructureRepository.save(structure);

		for (FeeStructureRequest.FeeStructureLineRequest lineRequest : request.getLines()) {
			FeeStructureLine line = new FeeStructureLine();
			line.setSchoolId(schoolId);
			line.setFeeStructure(structure);
			line.setFeeCategory(feeCategoryService.getScopedEntity(lineRequest.getFeeCategoryId()));
			line.setAmount(lineRequest.getAmount());
			feeStructureLineRepository.save(line);
		}

		return toResponse(structure);
	}

	@Transactional
	public List<FeeAssessmentResponse> generateAssessments(UUID structureId) {
		FeeStructure structure = findScoped(structureId);
		List<FeeStructureLine> lines = feeStructureLineRepository.findAllByFeeStructureId(structure.getId());
		BigDecimal totalDue = lines.stream()
				.map(FeeStructureLine::getAmount)
				.reduce(BigDecimal.ZERO, BigDecimal::add);

		List<Student> students = studentRepository.findAllBySchoolIdAndClassSectionId(
				schoolContext.getSchoolId(), structure.getClassSection().getId());

		return students.stream()
				.filter(s -> s.getStatus() == StudentStatus.ACTIVE)
				.map(student -> createOrSkipAssessment(student, structure.getAcademicYear(), totalDue))
				.map(FeeAssessmentResponse::from)
				.toList();
	}

	@Transactional
	public void createAssessmentForStudentIfStructureExists(Student student) {
		ClassSection classSection = student.getClassSection();
		feeStructureRepository.findBySchoolIdAndClassSectionIdAndAcademicYear(
				student.getSchoolId(), classSection.getId(), classSection.getAcademicYear())
				.ifPresent(structure -> {
					List<FeeStructureLine> lines = feeStructureLineRepository.findAllByFeeStructureId(structure.getId());
					BigDecimal totalDue = lines.stream()
							.map(FeeStructureLine::getAmount)
							.reduce(BigDecimal.ZERO, BigDecimal::add);
					createOrSkipAssessment(student, structure.getAcademicYear(), totalDue);
				});
	}

	private StudentFeeAssessment createOrSkipAssessment(Student student, String academicYear, BigDecimal totalDue) {
		return assessmentRepository.findBySchoolIdAndStudentIdAndAcademicYear(
				student.getSchoolId(), student.getId(), academicYear)
				.orElseGet(() -> {
					StudentFeeAssessment assessment = new StudentFeeAssessment();
					assessment.setSchoolId(student.getSchoolId());
					assessment.setStudent(student);
					assessment.setAcademicYear(academicYear);
					assessment.setTotalDue(totalDue);
					assessment.setTotalPaid(BigDecimal.ZERO);
					assessment.setStatus(FeeAssessmentStatus.UNPAID);
					assessment.setDueDate(LocalDate.now().plusMonths(1));
					return assessmentRepository.save(assessment);
				});
	}

	private FeeStructure findScoped(UUID id) {
		return feeStructureRepository.findByIdAndSchoolId(id, schoolContext.getSchoolId())
				.orElseThrow(() -> new EntityNotFoundException("Fee structure not found"));
	}

	private FeeStructureResponse toResponse(FeeStructure structure) {
		List<FeeStructureLine> lines = feeStructureLineRepository.findAllByFeeStructureId(structure.getId());
		return FeeStructureResponse.from(structure, lines);
	}

}
