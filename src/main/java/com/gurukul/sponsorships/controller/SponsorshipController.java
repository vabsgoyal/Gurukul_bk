package com.gurukul.sponsorships.controller;

import com.gurukul.common.ApiResponse;
import com.gurukul.sponsorships.dto.SponsorshipDtos;
import com.gurukul.sponsorships.entity.SponsorshipPurpose;
import com.gurukul.sponsorships.service.SponsorshipService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Sponsorships", description = "Sponsor and sponsorship management. Requires X-School-Id header.")
public class SponsorshipController {

	private final SponsorshipService sponsorshipService;

	@GetMapping("/api/v1/sponsors")
	@Operation(summary = "List sponsors")
	public ApiResponse<List<SponsorshipDtos.SponsorResponse>> listSponsors() {
		return ApiResponse.success(sponsorshipService.listSponsors());
	}

	@PostMapping("/api/v1/sponsors")
	@Operation(summary = "Create sponsor")
	public ApiResponse<SponsorshipDtos.SponsorResponse> createSponsor(@Valid @RequestBody SponsorshipDtos.SponsorRequest request) {
		return ApiResponse.success(sponsorshipService.createSponsor(request), "Sponsor created");
	}

	@GetMapping("/api/v1/sponsorships")
	@Operation(summary = "List sponsorships")
	public ApiResponse<List<SponsorshipDtos.SponsorshipResponse>> listSponsorships(
			@RequestParam(required = false) SponsorshipPurpose purpose) {
		return ApiResponse.success(sponsorshipService.listSponsorships(purpose));
	}

	@PostMapping("/api/v1/sponsorships")
	@Operation(summary = "Create sponsorship")
	public ApiResponse<SponsorshipDtos.SponsorshipResponse> createSponsorship(
			@Valid @RequestBody SponsorshipDtos.SponsorshipRequest request) {
		return ApiResponse.success(sponsorshipService.createSponsorship(request), "Sponsorship created");
	}

	@PostMapping("/api/v1/sponsorships/{id}/payments")
	@Operation(summary = "Record sponsorship payment")
	public ApiResponse<SponsorshipDtos.SponsorshipPaymentResponse> recordPayment(
			@PathVariable UUID id,
			@Valid @RequestBody SponsorshipDtos.SponsorshipPaymentRequest request) {
		return ApiResponse.success(sponsorshipService.recordPayment(id, request), "Payment recorded");
	}

}
