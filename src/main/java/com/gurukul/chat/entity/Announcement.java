package com.gurukul.chat.entity;

import com.gurukul.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * Exactly one of sectionId/className is set, matching scope (SCHOOL: neither; CLASS: sectionId
 * only; GRADE: className only) - see V19/V31 CHECK constraint. sectionId is not FK-mapped to
 * ClassSection on the Java side to avoid a lazy-load surprise in the chat hot path - plain UUID,
 * FK enforced at the DB level only.
 */
@Getter
@Setter
@Entity
@Table(name = "announcement")
public class Announcement extends BaseEntity {

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private AnnouncementScope scope;

	@Column(name = "section_id")
	private UUID sectionId;

	@Column(name = "class_name")
	private String className;

	@Column(name = "author_employee_id", nullable = false)
	private UUID authorEmployeeId;

	@Column(nullable = false)
	private String title;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String body;

}
