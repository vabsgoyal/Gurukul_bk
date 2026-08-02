package com.gurukul.chat.controller;

import com.gurukul.auth.security.AuthContext;
import com.gurukul.auth.security.AuthPrincipal;
import com.gurukul.chat.dto.AnnouncementDtos.AnnouncementResponse;
import com.gurukul.chat.dto.AnnouncementDtos.CreateAnnouncementRequest;
import com.gurukul.chat.service.AnnouncementService;
import com.gurukul.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Chat - Announcements", description = "School-wide, grade-level, and section-level announcements. "
		+ "ADMIN can post school-wide; ADMIN or a section's assigned class teacher can post to that section; "
		+ "ADMIN or any class teacher within a grade can post to that whole grade (all its sections together). "
		+ "Requires X-School-Id and Authorization headers.")
public class AnnouncementController {

	private final AnnouncementService announcementService;

	@PostMapping("/api/v1/chat/announcements")
	@Operation(summary = "Post an announcement")
	public ApiResponse<AnnouncementResponse> create(@Valid @RequestBody CreateAnnouncementRequest request) {
		AuthPrincipal principal = AuthContext.current();
		return ApiResponse.success(
				AnnouncementResponse.from(announcementService.create(principal, request)), "Announcement posted");
	}

	@GetMapping("/api/v1/chat/announcements")
	@Operation(summary = "List announcements visible to me",
			description = "Always includes all school-wide announcements. Pass sectionId to also include that "
					+ "section's announcements, and/or className to also include that grade's (all sections "
					+ "together), if you have visibility into them.")
	public ApiResponse<List<AnnouncementResponse>> list(
			@RequestParam(required = false) UUID sectionId,
			@RequestParam(required = false) String className) {
		AuthPrincipal principal = AuthContext.current();
		List<AnnouncementResponse> responses = announcementService.listVisible(principal, sectionId, className).stream()
				.map(AnnouncementResponse::from)
				.toList();
		return ApiResponse.success(responses);
	}

}
