package com.gurukul.teachers.dto;

import com.gurukul.teachers.entity.AssessmentType;
import com.gurukul.teachers.entity.QuestionType;
import com.gurukul.teachers.entity.QuizDifficulty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Schema(description = "AI quiz/test generation request")
public class AiQuizGenerationRequest {

	@NotNull
	@Schema(description = "Class-section UUID from GET /api/v1/class-sections")
	private UUID classSectionId;

	@NotBlank
	@Schema(description = "Subject for the quiz/test", example = "Science")
	private String subjectName;

	@NotNull
	@Schema(description = "Quiz or test type", example = "QUIZ")
	private AssessmentType assessmentType;

	@NotBlank
	@Schema(description = "Quiz/test title", example = "Photosynthesis Quick Check")
	private String title;

	@NotBlank
	@Schema(description = "Syllabus or topic coverage", example = "Photosynthesis, chlorophyll, stomata, food chain basics")
	private String syllabus;

	@NotNull
	@Schema(description = "Difficulty level", example = "MEDIUM")
	private QuizDifficulty difficulty;

	@NotNull
	@Min(1)
	@Max(50)
	@Schema(description = "Number of questions to generate", example = "10")
	private Integer questionCount;

	@NotNull
	@Min(1)
	@Schema(description = "Total marks for generated quiz/test", example = "20")
	private Integer maxMarks;

	@Schema(description = "Question types to include. Defaults to MCQ and SHORT_ANSWER if omitted.")
	private List<QuestionType> questionTypes;

	@Schema(description = "Optional teacher instruction for the generator", example = "Include two diagram-based questions.")
	private String additionalInstructions;

}
