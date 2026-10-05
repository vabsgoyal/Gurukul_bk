package com.gurukul.leads.controller;

import com.gurukul.common.ApiResponse;
import com.gurukul.common.ClientIp;
import com.gurukul.leads.dto.LeadDtos.CreateLeadRequest;
import com.gurukul.leads.dto.LeadDtos.LeadReceived;
import com.gurukul.leads.dto.LeadDtos.LeadResponse;
import com.gurukul.leads.service.LeadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/leads")
@RequiredArgsConstructor
@Tag(name = "Leads", description = "Demo requests from the public marketing site (smartgurukul.org).")
public class LeadController {

	private final LeadService leadService;

	@PostMapping
	@ResponseStatus(HttpStatus.ACCEPTED)
	@Operation(summary = "Submit a demo request",
			description = "Public, no auth or X-School-Id. Rate-limited per address; returns 429 when over the limit.")
	public ApiResponse<LeadReceived> submit(@Valid @RequestBody CreateLeadRequest request, HttpServletRequest http) {
		leadService.submit(request, ClientIp.of(http));
		return ApiResponse.success(new LeadReceived("received"), "Thanks! We'll be in touch within one working day.");
	}

	@GetMapping
	@Operation(summary = "List demo requests, newest first (max 500)",
			description = "Requires Authorization: Bearer <LEADS_ADMIN_TOKEN>. Returns 404 when no token is configured.")
	public ApiResponse<List<LeadResponse>> list(
			@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
		return ApiResponse.success(leadService.list(authorization));
	}

}
