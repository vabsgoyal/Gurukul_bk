package com.gurukul.teachers.dto;

import com.gurukul.teachers.entity.TeacherFeature;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
@Schema(description = "Teacher-facing feature descriptor")
public class TeacherFeatureResponse {

	private TeacherFeature feature;
	private String title;
	private String description;
	private boolean availableInCurrentSlice;

}
