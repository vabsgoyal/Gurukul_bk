package com.gurukul.teachers.service;

import com.gurukul.common.EntityNotFoundException;
import com.gurukul.common.SchoolContext;
import com.gurukul.students.entity.ClassSection;
import com.gurukul.students.service.ClassSectionService;
import com.gurukul.teachers.dto.TeacherAssignmentRequest;
import com.gurukul.teachers.dto.TeacherAssignmentResponse;
import com.gurukul.teachers.dto.TeacherDashboardResponse;
import com.gurukul.teachers.dto.TeacherFeatureResponse;
import com.gurukul.teachers.dto.TeacherRequest;
import com.gurukul.teachers.dto.TeacherResponse;
import com.gurukul.teachers.entity.Teacher;
import com.gurukul.teachers.entity.TeacherAssignmentRole;
import com.gurukul.teachers.entity.TeacherClassAssignment;
import com.gurukul.teachers.entity.TeacherStatus;
import com.gurukul.teachers.repository.TeacherClassAssignmentRepository;
import com.gurukul.teachers.repository.TeacherAssessmentScheduleRepository;
import com.gurukul.teachers.repository.TeacherRepository;
import com.gurukul.teachers.repository.TeacherResourceRepository;
import com.gurukul.teachers.repository.TeacherStudentAttendanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TeacherService {

	private final TeacherRepository teacherRepository;
	private final TeacherClassAssignmentRepository assignmentRepository;
	private final TeacherResourceRepository resourceRepository;
	private final TeacherAssessmentScheduleRepository scheduleRepository;
	private final TeacherStudentAttendanceRepository attendanceRepository;
	private final SchoolContext schoolContext;
	private final ClassSectionService classSectionService;
	private final TeacherFeatureCatalog featureCatalog;

	public List<TeacherResponse> list() {
		UUID schoolId = schoolContext.getSchoolId();
		return teacherRepository.findAllBySchoolIdOrderByNameAsc(schoolId).stream()
				.map(teacher -> TeacherResponse.summary(
						teacher,
						assignmentRepository.countBySchoolIdAndTeacherId(schoolId, teacher.getId())))
				.toList();
	}

	public TeacherResponse getById(UUID id) {
		Teacher teacher = findScoped(id);
		return TeacherResponse.from(teacher, listAssignments(id));
	}

	@Transactional
	public TeacherResponse create(TeacherRequest request) {
		UUID schoolId = schoolContext.getSchoolId();
		validateUniqueCreate(schoolId, request);

		Teacher teacher = new Teacher();
		teacher.setSchoolId(schoolId);
		applyRequest(teacher, request);
		teacher.setStatus(TeacherStatus.ACTIVE);

		return TeacherResponse.from(teacherRepository.save(teacher), List.of());
	}

	@Transactional
	public TeacherResponse update(UUID id, TeacherRequest request) {
		Teacher teacher = findScoped(id);
		validateUniqueUpdate(teacher.getSchoolId(), id, request);
		applyRequest(teacher, request);
		if (request.getStatus() != null) {
			teacher.setStatus(request.getStatus());
		}
		Teacher saved = teacherRepository.save(teacher);
		return TeacherResponse.from(saved, listAssignments(id));
	}

	@Transactional
	public TeacherAssignmentResponse assignClass(UUID teacherId, TeacherAssignmentRequest request) {
		UUID schoolId = schoolContext.getSchoolId();
		Teacher teacher = findScoped(teacherId);
		ClassSection classSection = classSectionService.getScopedClassSection(request.getClassSectionId());

		if (assignmentRepository.existsBySchoolIdAndTeacherIdAndClassSectionIdAndSubjectNameAndAssignmentRole(
				schoolId,
				teacherId,
				request.getClassSectionId(),
				request.getSubjectName(),
				request.getAssignmentRole())) {
			throw new IllegalArgumentException("Teacher assignment already exists for this school");
		}

		TeacherClassAssignment assignment = new TeacherClassAssignment();
		assignment.setSchoolId(schoolId);
		assignment.setTeacher(teacher);
		assignment.setClassSection(classSection);
		assignment.setSubjectName(request.getSubjectName());
		assignment.setAssignmentRole(request.getAssignmentRole());

		return TeacherAssignmentResponse.from(assignmentRepository.save(assignment));
	}

	public List<TeacherAssignmentResponse> listAssignments(UUID teacherId) {
		findScoped(teacherId);
		return assignmentRepository.findAllBySchoolIdAndTeacherIdOrderBySubjectNameAsc(
						schoolContext.getSchoolId(), teacherId)
				.stream()
				.map(TeacherAssignmentResponse::from)
				.toList();
	}

	public List<TeacherAssignmentResponse> listAssignmentsByClassSection(UUID classSectionId) {
		classSectionService.getScopedClassSection(classSectionId);
		return assignmentRepository.findAllBySchoolIdAndClassSectionIdOrderBySubjectNameAsc(
						schoolContext.getSchoolId(), classSectionId)
				.stream()
				.map(TeacherAssignmentResponse::from)
				.toList();
	}

	@Transactional
	public void deleteAssignment(UUID assignmentId) {
		TeacherClassAssignment assignment = assignmentRepository
				.findByIdAndSchoolId(assignmentId, schoolContext.getSchoolId())
				.orElseThrow(() -> new EntityNotFoundException("Teacher assignment not found"));
		assignmentRepository.delete(assignment);
	}

	@Transactional
	public void delete(UUID id) {
		Teacher teacher = findScoped(id);
		attendanceRepository.deleteAllBySchoolIdAndTeacherId(teacher.getSchoolId(), id);
		resourceRepository.deleteAllBySchoolIdAndTeacherId(teacher.getSchoolId(), id);
		scheduleRepository.deleteAllBySchoolIdAndTeacherId(teacher.getSchoolId(), id);
		assignmentRepository.deleteAllBySchoolIdAndTeacherId(teacher.getSchoolId(), id);
		teacherRepository.delete(teacher);
	}

	public TeacherDashboardResponse dashboard() {
		UUID schoolId = schoolContext.getSchoolId();
		List<TeacherFeatureResponse> features = featureCatalog.list();
		return new TeacherDashboardResponse(
				schoolId,
				teacherRepository.countBySchoolId(schoolId),
				teacherRepository.countBySchoolIdAndStatus(schoolId, TeacherStatus.ACTIVE),
				assignmentRepository.countBySchoolIdAndAssignmentRole(schoolId, TeacherAssignmentRole.CLASS_TEACHER),
				assignmentRepository.countBySchoolIdAndAssignmentRole(schoolId, TeacherAssignmentRole.SUBJECT_TEACHER),
				assignmentRepository.countDistinctClassSectionsBySchoolId(schoolId),
				features
		);
	}

	public List<TeacherFeatureResponse> features() {
		return featureCatalog.list();
	}

	public Teacher getScopedTeacher(UUID id) {
		return findScoped(id);
	}

	private Teacher findScoped(UUID id) {
		return teacherRepository.findByIdAndSchoolId(id, schoolContext.getSchoolId())
				.orElseThrow(() -> new EntityNotFoundException("Teacher not found"));
	}

	private void validateUniqueCreate(UUID schoolId, TeacherRequest request) {
		if (teacherRepository.existsBySchoolIdAndEmployeeCode(schoolId, request.getEmployeeCode())) {
			throw new IllegalArgumentException("Employee code already exists for this school");
		}
		if (teacherRepository.existsBySchoolIdAndEmail(schoolId, request.getEmail())) {
			throw new IllegalArgumentException("Teacher email already exists for this school");
		}
	}

	private void validateUniqueUpdate(UUID schoolId, UUID teacherId, TeacherRequest request) {
		if (teacherRepository.existsBySchoolIdAndEmployeeCodeAndIdNot(
				schoolId, request.getEmployeeCode(), teacherId)) {
			throw new IllegalArgumentException("Employee code already exists for this school");
		}
		if (teacherRepository.existsBySchoolIdAndEmailAndIdNot(schoolId, request.getEmail(), teacherId)) {
			throw new IllegalArgumentException("Teacher email already exists for this school");
		}
	}

	private void applyRequest(Teacher teacher, TeacherRequest request) {
		teacher.setEmployeeCode(request.getEmployeeCode());
		teacher.setName(request.getName());
		teacher.setEmail(request.getEmail());
		teacher.setPhone(request.getPhone());
		teacher.setQualification(request.getQualification());
		teacher.setSpecialization(request.getSpecialization());
		teacher.setJoiningDate(request.getJoiningDate());
	}

}
