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

@Getter
@Setter
@Entity
@Table(name = "teacher_resource")
public class TeacherResource extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "teacher_id", nullable = false)
	private Teacher teacher;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "class_section_id", nullable = false)
	private ClassSection classSection;

	@Column(name = "subject_name", nullable = false)
	private String subjectName;

	@Enumerated(EnumType.STRING)
	@Column(name = "resource_type", nullable = false)
	private TeacherResourceType resourceType;

	@Column(nullable = false)
	private String title;

	@Column(nullable = false, length = 1000)
	private String description;

	@Column(name = "resource_url", nullable = false, length = 1000)
	private String resourceUrl;

	@Column(name = "available_offline", nullable = false)
	private boolean availableOffline;

	@Column(name = "file_name")
	private String fileName;

	@Column(name = "content_type")
	private String contentType;

	@Column(name = "file_size_bytes")
	private Long fileSizeBytes;

}
