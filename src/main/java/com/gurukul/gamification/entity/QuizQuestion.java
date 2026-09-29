package com.gurukul.gamification.entity;

import com.gurukul.academics.entity.Subject;
import com.gurukul.common.BaseEntity;
import com.gurukul.employees.entity.Employee;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Options A-D and correctOption are set only for MCQ questions; answerText only for the others. */
@Getter
@Setter
@Entity
@Table(name = "quiz_question")
public class QuizQuestion extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "subject_id", nullable = false)
	private Subject subject;

	@Column(name = "class_name")
	private String className;

	@Column(name = "question_text", nullable = false, length = 500)
	private String questionText;

	@Column(name = "option_a")
	private String optionA;

	@Column(name = "option_b")
	private String optionB;

	@Column(name = "option_c")
	private String optionC;

	@Column(name = "option_d")
	private String optionD;

	/** Null for non-MCQ questions. */
	@Enumerated(EnumType.STRING)
	@Column(name = "correct_option")
	private QuizOption correctOption;

	@Enumerated(EnumType.STRING)
	@Column(name = "question_type", nullable = false)
	private QuizQuestionType questionType = QuizQuestionType.MCQ;

	/** The expected answer for NUMERIC / SHORT_WORD questions; null for MCQ. */
	@Column(name = "answer_text")
	private String answerText;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "created_by_teacher_id", nullable = false)
	private Employee createdByTeacher;

}
