package com.gurukul.fees.controller;

import com.gurukul.common.ApiResponse;
import com.gurukul.fees.dto.FeeCategoryRequest;
import com.gurukul.fees.dto.FeeCategoryResponse;
import com.gurukul.fees.service.FeeCategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/fee-categories")
@RequiredArgsConstructor
@Tag(name = "Fee Categories", description = "Configurable fee categories. Requires X-School-Id header.")
public class FeeCategoryController {

	private final FeeCategoryService feeCategoryService;

	@GetMapping
	@Operation(summary = "List fee categories")
	public ApiResponse<List<FeeCategoryResponse>> list() {
		return ApiResponse.success(feeCategoryService.list());
	}

	@PostMapping
	@Operation(summary = "Create fee category")
	public ApiResponse<FeeCategoryResponse> create(@Valid @RequestBody FeeCategoryRequest request) {
		return ApiResponse.success(feeCategoryService.create(request), "Fee category created");
	}

}
