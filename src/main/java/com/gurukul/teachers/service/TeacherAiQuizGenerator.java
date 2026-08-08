package com.gurukul.teachers.service;

import com.gurukul.students.entity.ClassSection;
import com.gurukul.teachers.dto.AiQuizGenerationRequest;
import com.gurukul.teachers.dto.AiQuizGenerationResponse;
import com.gurukul.teachers.entity.Teacher;

public interface TeacherAiQuizGenerator {

	AiQuizGenerationResponse generate(Teacher teacher, ClassSection classSection, AiQuizGenerationRequest request);

}
