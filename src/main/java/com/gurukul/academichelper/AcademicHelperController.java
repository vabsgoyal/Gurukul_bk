package com.gurukul.academichelper;

import com.gurukul.academichelper.AcademicHelperDtos.AskRequest;
import com.gurukul.academichelper.AcademicHelperDtos.AskResponse;
import com.gurukul.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/academic-helper")
@RequiredArgsConstructor
public class AcademicHelperController {

	private final AcademicHelperService academicHelperService;

	@PostMapping("/ask")
	public ApiResponse<AskResponse> ask(@Valid @RequestBody AskRequest request) {
		return ApiResponse.success(new AskResponse(academicHelperService.ask(request)));
	}

}
