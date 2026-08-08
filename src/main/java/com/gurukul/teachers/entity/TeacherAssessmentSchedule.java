package com.gurukul.teachers.entity;

import com.gurukul.common.BaseEntity;
import com.gurukul.students.entity.ClassSection;
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

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "teacher_assessment_schedule")
public class TeacherAssessmentSchedule extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "teacher_id", nullable = false)
	private Teacher teacher;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "class_section_id", nullable = false)
	private ClassSection classSection;

	@Column(name = "subject_name", nullable = false)
	private String subjectName;

	@Enumerated(EnumType.STRING)
	@Column(name = "assessment_type", nullable = false)
	private AssessmentType assessmentType;

	@Column(nullable = false)
	private String title;

	@Column(name = "scheduled_at", nullable = false)
	private LocalDateTime scheduledAt;

	@Column(nullable = false, length = 2000)
	private String syllabus;

	@Column(nullable = false, length = 1000)
	private String instructions;

	@Column(name = "max_marks", nullable = false)
	private Integer maxMarks;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private AssessmentStatus status;

}
