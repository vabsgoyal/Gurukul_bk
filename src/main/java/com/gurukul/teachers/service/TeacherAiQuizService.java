package com.gurukul.teachers.service;

import com.gurukul.students.entity.ClassSection;
import com.gurukul.students.service.ClassSectionService;
import com.gurukul.teachers.dto.AiQuizGenerationRequest;
import com.gurukul.teachers.dto.AiQuizGenerationResponse;
import com.gurukul.teachers.entity.Teacher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TeacherAiQuizService {

	private final TeacherService teacherService;
	private final ClassSectionService classSectionService;
	private final TeacherAiQuizGenerator quizGenerator;

	public AiQuizGenerationResponse generate(UUID teacherId, AiQuizGenerationRequest request) {
		Teacher teacher = teacherService.getScopedTeacher(teacherId);
		ClassSection classSection = classSectionService.getScopedClassSection(request.getClassSectionId());
		return quizGenerator.generate(teacher, classSection, request);
	}

}
