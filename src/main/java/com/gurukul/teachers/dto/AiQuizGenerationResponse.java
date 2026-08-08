package com.gurukul.teachers.dto;

import com.gurukul.teachers.entity.AssessmentType;
import com.gurukul.teachers.entity.QuizDifficulty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

@Getter
@AllArgsConstructor
@Schema(description = "AI-generated quiz/test draft")
public class AiQuizGenerationResponse {

	private UUID schoolId;
	private UUID teacherId;
	private String teacherName;
	private UUID classSectionId;
	private String classSectionLabel;
	private String subjectName;
	private AssessmentType assessmentType;
	private String title;
	private String syllabus;
	private QuizDifficulty difficulty;
	private Integer maxMarks;
	private Integer questionCount;
	private String generatorMode;
	private String reviewNote;
	private List<GeneratedQuizQuestionResponse> questions;

}
