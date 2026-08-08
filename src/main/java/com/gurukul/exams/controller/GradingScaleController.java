package com.gurukul.exams.controller;

import com.gurukul.common.ApiResponse;
import com.gurukul.exams.dto.GradingBandDtos.GradingBandRequest;
import com.gurukul.exams.dto.GradingBandDtos.GradingBandResponse;
import com.gurukul.exams.service.GradingScaleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "Grading Scale", description = "Marks-percentage to letter-grade bands. Requires X-School-Id header.")
public class GradingScaleController {

	private final GradingScaleService gradingScaleService;

	@GetMapping("/api/v1/grading-scale")
	@Operation(summary = "List this school's grading bands (or the built-in defaults if never configured)")
	public ApiResponse<List<GradingBandResponse>> list() {
		return ApiResponse.success(gradingScaleService.list());
	}

	@PutMapping("/api/v1/grading-scale")
	@Operation(summary = "Replace this school's entire grading scale", description = "Admin only. Replaces all bands atomically.")
	public ApiResponse<List<GradingBandResponse>> replace(@Valid @RequestBody List<GradingBandRequest> bands) {
		return ApiResponse.success(gradingScaleService.replaceBands(bands), "Grading scale updated");
	}

}
