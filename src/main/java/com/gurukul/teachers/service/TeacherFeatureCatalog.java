package com.gurukul.teachers.service;

import com.gurukul.teachers.dto.TeacherFeatureResponse;
import com.gurukul.teachers.entity.TeacherFeature;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TeacherFeatureCatalog {

	public List<TeacherFeatureResponse> list() {
		return List.of(
				new TeacherFeatureResponse(
						TeacherFeature.CLASS_TIMETABLE,
						"Class timetable",
						"View class and subject schedules for assigned sections.",
						true),
				new TeacherFeatureResponse(
						TeacherFeature.SMART_REMINDERS,
						"Smart reminders",
						"Receive automated pre-class alerts and personal schedule reminders.",
						false),
				new TeacherFeatureResponse(
						TeacherFeature.ATTENDANCE_MARKING,
						"One-tap attendance",
						"Log student presence in seconds and generate auto-reports.",
						true),
				new TeacherFeatureResponse(
						TeacherFeature.TEACHER_ATTENDANCE_LEAVE,
						"Teacher attendance and leave",
						"Track teacher attendance, leave requests, and class coverage.",
						false),
				new TeacherFeatureResponse(
						TeacherFeature.ASSIGNMENT_CREATION,
						"Assignment creation",
						"Create homework, attach resources, and set due dates.",
						false),
				new TeacherFeatureResponse(
						TeacherFeature.HOMEWORK_REVIEW,
						"Homework review",
						"Review submissions and return feedback to students.",
						false),
				new TeacherFeatureResponse(
						TeacherFeature.GRADEBOOK,
						"Gradebook",
						"Record test marks, rubrics, and subject-level performance.",
						false),
				new TeacherFeatureResponse(
						TeacherFeature.STUDENT_PERFORMANCE_INSIGHTS,
						"Student performance insights",
						"See weak topics, progress patterns, and intervention flags.",
						false),
				new TeacherFeatureResponse(
						TeacherFeature.AI_CLASS_SUMMARIES,
						"AI class summaries",
						"Auto-generate class summaries after lessons.",
						false),
				new TeacherFeatureResponse(
						TeacherFeature.AI_NOTES_GENERATOR,
						"AI notes generator",
						"Create syllabus-aligned study notes for students.",
						false),
				new TeacherFeatureResponse(
						TeacherFeature.AI_QUIZ_GENERATOR,
						"AI quiz generator",
						"Generate a reviewable quiz or test draft from class, subject, syllabus, marks, and difficulty.",
						true),
				new TeacherFeatureResponse(
						TeacherFeature.AI_QUESTION_PAPER_GENERATOR,
						"AI question paper generator",
						"Create exam papers aligned to syllabus and difficulty level.",
						false),
				new TeacherFeatureResponse(
						TeacherFeature.AI_LESSON_PLANNER,
						"AI lesson planner",
						"Generate lesson plans from syllabus, class level, and outcomes.",
						false),
				new TeacherFeatureResponse(
						TeacherFeature.AI_DOUBT_MODERATION,
						"AI doubt moderation",
						"Review AI-assisted student doubt threads before final answers.",
						false),
				new TeacherFeatureResponse(
						TeacherFeature.PARENT_COMMUNICATION,
						"Parent communication",
						"Send structured updates and meeting notes to guardians.",
						false),
				new TeacherFeatureResponse(
						TeacherFeature.RESOURCE_LIBRARY,
						"Resource library",
						"Share books, notes, worksheets, links, and important study material with class-sections.",
						true),
				new TeacherFeatureResponse(
						TeacherFeature.QUIZ_TEST_SCHEDULER,
						"Quiz and test scheduler",
						"Publish the next quiz, test, syllabus, date, marks, and instructions for students.",
						true),
				new TeacherFeatureResponse(
						TeacherFeature.DISCUSSION_MODERATION,
						"Discussion moderation",
						"Moderate subject-wise student discussion spaces.",
						false),
				new TeacherFeatureResponse(
						TeacherFeature.DIGITAL_CERTIFICATES,
						"Digital certificates",
						"Recommend or issue academic and co-curricular certificates.",
						false),
				new TeacherFeatureResponse(
						TeacherFeature.WELLNESS_ESCALATION_VIEW,
						"Wellness escalation view",
						"See counselor-approved wellbeing alerts requiring teacher support.",
						false),
				new TeacherFeatureResponse(
						TeacherFeature.OFFLINE_RESOURCE_SHARING,
						"Offline resource sharing",
						"Publish notes and resources that students can download for offline use.",
						false),
				new TeacherFeatureResponse(
						TeacherFeature.REGIONAL_LANGUAGE_SUPPORT,
						"Regional language support",
						"Create and review content in Hindi or regional languages.",
						false)
		);
	}

}
