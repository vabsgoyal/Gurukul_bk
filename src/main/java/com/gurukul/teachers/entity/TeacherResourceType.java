package com.gurukul.teachers.entity;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Type of resource shared by a teacher")
public enum TeacherResourceType {
	BOOK,
	NOTES,
	WORKSHEET,
	PRESENTATION,
	VIDEO,
	LINK,
	OTHER
}
