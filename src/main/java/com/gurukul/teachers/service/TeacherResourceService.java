package com.gurukul.teachers.service;

import com.gurukul.common.EntityNotFoundException;
import com.gurukul.common.SchoolContext;
import com.gurukul.storage.FileStorageService;
import com.gurukul.storage.StoredFile;
import com.gurukul.students.entity.ClassSection;
import com.gurukul.students.service.ClassSectionService;
import com.gurukul.teachers.dto.TeacherAssessmentScheduleRequest;
import com.gurukul.teachers.dto.TeacherAssessmentScheduleResponse;
import com.gurukul.teachers.dto.TeacherResourceRequest;
import com.gurukul.teachers.dto.TeacherResourceResponse;
import com.gurukul.teachers.dto.TeacherResourceUploadRequest;
import com.gurukul.teachers.entity.AssessmentStatus;
import com.gurukul.teachers.entity.Teacher;
import com.gurukul.teachers.entity.TeacherAssessmentSchedule;
import com.gurukul.teachers.entity.TeacherResource;
import com.gurukul.teachers.repository.TeacherAssessmentScheduleRepository;
import com.gurukul.teachers.repository.TeacherResourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TeacherResourceService {

	private final TeacherResourceRepository resourceRepository;
	private final TeacherAssessmentScheduleRepository scheduleRepository;
	private final TeacherService teacherService;
	private final ClassSectionService classSectionService;
	private final SchoolContext schoolContext;
	private final FileStorageService fileStorageService;

	@Transactional
	public TeacherResourceResponse createResource(UUID teacherId, TeacherResourceRequest request) {
		UUID schoolId = schoolContext.getSchoolId();
		Teacher teacher = teacherService.getScopedTeacher(teacherId);
		ClassSection classSection = classSectionService.getScopedClassSection(request.getClassSectionId());

		TeacherResource resource = new TeacherResource();
		resource.setSchoolId(schoolId);
		resource.setTeacher(teacher);
		resource.setClassSection(classSection);
		resource.setSubjectName(request.getSubjectName());
		resource.setResourceType(request.getResourceType());
		resource.setTitle(request.getTitle());
		resource.setDescription(request.getDescription());
		resource.setResourceUrl(request.getResourceUrl());
		resource.setAvailableOffline(request.isAvailableOffline());

		return TeacherResourceResponse.from(resourceRepository.save(resource));
	}

	@Transactional
	public TeacherResourceResponse uploadResource(UUID teacherId, TeacherResourceUploadRequest request, MultipartFile file) {
		UUID schoolId = schoolContext.getSchoolId();
		Teacher teacher = teacherService.getScopedTeacher(teacherId);
		ClassSection classSection = classSectionService.getScopedClassSection(request.getClassSectionId());

		StoredFile stored = fileStorageService.upload(file, "teacher-resources/" + schoolId + "/" + teacherId);

		TeacherResource resource = new TeacherResource();
		resource.setSchoolId(schoolId);
		resource.setTeacher(teacher);
		resource.setClassSection(classSection);
		resource.setSubjectName(request.getSubjectName());
		resource.setResourceType(request.getResourceType());
		resource.setTitle(request.getTitle());
		resource.setDescription(request.getDescription());
		resource.setResourceUrl(stored.url());
		resource.setAvailableOffline(request.isAvailableOffline());
		resource.setFileName(stored.originalFilename());
		resource.setContentType(stored.contentType());
		resource.setFileSizeBytes(stored.sizeBytes());

		return TeacherResourceResponse.from(resourceRepository.save(resource));
	}

	public List<TeacherResourceResponse> listResourcesByTeacher(UUID teacherId) {
		teacherService.getScopedTeacher(teacherId);
		return resourceRepository.findAllBySchoolIdAndTeacherIdOrderByCreatedAtDesc(
						schoolContext.getSchoolId(), teacherId)
				.stream()
				.map(TeacherResourceResponse::from)
				.toList();
	}

	public List<TeacherResourceResponse> listResourcesByClassSection(UUID classSectionId) {
		classSectionService.getScopedClassSection(classSectionId);
		return resourceRepository.findAllBySchoolIdAndClassSectionIdOrderByCreatedAtDesc(
						schoolContext.getSchoolId(), classSectionId)
				.stream()
				.map(TeacherResourceResponse::from)
				.toList();
	}

	@Transactional
	public void deleteResource(UUID resourceId) {
		TeacherResource resource = resourceRepository.findByIdAndSchoolId(resourceId, schoolContext.getSchoolId())
				.orElseThrow(() -> new EntityNotFoundException("Teacher resource not found"));
		resourceRepository.delete(resource);
	}

	@Transactional
	public TeacherAssessmentScheduleResponse createSchedule(UUID teacherId, TeacherAssessmentScheduleRequest request) {
		UUID schoolId = schoolContext.getSchoolId();
		Teacher teacher = teacherService.getScopedTeacher(teacherId);
		ClassSection classSection = classSectionService.getScopedClassSection(request.getClassSectionId());

		TeacherAssessmentSchedule schedule = new TeacherAssessmentSchedule();
		schedule.setSchoolId(schoolId);
		schedule.setTeacher(teacher);
		schedule.setClassSection(classSection);
		applyScheduleRequest(schedule, request);
		if (request.getStatus() == null) {
			schedule.setStatus(AssessmentStatus.SCHEDULED);
		}

		return TeacherAssessmentScheduleResponse.from(scheduleRepository.save(schedule));
	}

	@Transactional
	public TeacherAssessmentScheduleResponse updateSchedule(UUID scheduleId, TeacherAssessmentScheduleRequest request) {
		TeacherAssessmentSchedule schedule = scheduleRepository.findByIdAndSchoolId(
						scheduleId, schoolContext.getSchoolId())
				.orElseThrow(() -> new EntityNotFoundException("Teacher assessment schedule not found"));
		classSectionService.getScopedClassSection(request.getClassSectionId());
		applyScheduleRequest(schedule, request);
		if (request.getStatus() != null) {
			schedule.setStatus(request.getStatus());
		}
		return TeacherAssessmentScheduleResponse.from(scheduleRepository.save(schedule));
	}

	public List<TeacherAssessmentScheduleResponse> listSchedulesByTeacher(UUID teacherId) {
		teacherService.getScopedTeacher(teacherId);
		return scheduleRepository.findAllBySchoolIdAndTeacherIdOrderByScheduledAtAsc(
						schoolContext.getSchoolId(), teacherId)
				.stream()
				.map(TeacherAssessmentScheduleResponse::from)
				.toList();
	}

	public List<TeacherAssessmentScheduleResponse> listSchedulesByClassSection(UUID classSectionId) {
		classSectionService.getScopedClassSection(classSectionId);
		return scheduleRepository.findAllBySchoolIdAndClassSectionIdOrderByScheduledAtAsc(
						schoolContext.getSchoolId(), classSectionId)
				.stream()
				.map(TeacherAssessmentScheduleResponse::from)
				.toList();
	}

	@Transactional
	public void deleteSchedule(UUID scheduleId) {
		TeacherAssessmentSchedule schedule = scheduleRepository.findByIdAndSchoolId(
						scheduleId, schoolContext.getSchoolId())
				.orElseThrow(() -> new EntityNotFoundException("Teacher assessment schedule not found"));
		scheduleRepository.delete(schedule);
	}

	private void applyScheduleRequest(
			TeacherAssessmentSchedule schedule,
			TeacherAssessmentScheduleRequest request) {
		ClassSection classSection = classSectionService.getScopedClassSection(request.getClassSectionId());
		schedule.setClassSection(classSection);
		schedule.setSubjectName(request.getSubjectName());
		schedule.setAssessmentType(request.getAssessmentType());
		schedule.setTitle(request.getTitle());
		schedule.setScheduledAt(request.getScheduledAt());
		schedule.setSyllabus(request.getSyllabus());
		schedule.setInstructions(request.getInstructions());
		schedule.setMaxMarks(request.getMaxMarks());
		if (request.getStatus() != null) {
			schedule.setStatus(request.getStatus());
		}
	}

}
