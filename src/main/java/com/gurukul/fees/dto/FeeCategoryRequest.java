package com.gurukul.fees.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Fee category create/update payload")
public class FeeCategoryRequest {

	@NotBlank
	@Schema(description = "Category code", example = "TUITION")
	private String code;

	@NotBlank
	@Schema(description = "Category name", example = "Tuition Fee")
	private String name;

}
