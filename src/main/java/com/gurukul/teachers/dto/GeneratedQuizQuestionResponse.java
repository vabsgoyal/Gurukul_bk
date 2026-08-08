package com.gurukul.teachers.dto;

import com.gurukul.teachers.entity.QuestionType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
@Schema(description = "AI-generated quiz/test question")
public class GeneratedQuizQuestionResponse {

	private int number;
	private QuestionType questionType;
	private String question;
	private List<String> options;
	private String answer;
	private String explanation;
	private int marks;

}
