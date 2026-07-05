package com.gurukul.collections.controller;

import com.gurukul.common.ApiResponse;
import com.gurukul.collections.dto.CollectionDtos;
import com.gurukul.collections.service.EventCollectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/events/{eventId}")
@RequiredArgsConstructor
@Tag(name = "Event Collections", description = "Event collection inflows. Requires X-School-Id header.")
public class EventCollectionController {

	private final EventCollectionService eventCollectionService;

	@PostMapping("/participation-fees")
	@Operation(summary = "Add participation fee for an event")
	public ApiResponse<CollectionDtos.ParticipationFeeResponse> addParticipationFee(
			@PathVariable UUID eventId,
			@Valid @RequestBody CollectionDtos.ParticipationFeeRequest request) {
		return ApiResponse.success(eventCollectionService.addParticipationFee(eventId, request), "Participation fee added");
	}

	@GetMapping("/participation-fees")
	@Operation(summary = "List participation fees for an event")
	public ApiResponse<List<CollectionDtos.ParticipationFeeResponse>> listParticipationFees(@PathVariable UUID eventId) {
		return ApiResponse.success(eventCollectionService.listParticipationFees(eventId));
	}

	@PostMapping("/collections")
	@Operation(summary = "Record an event collection payment")
	public ApiResponse<CollectionDtos.CollectionPaymentResponse> recordCollection(
			@PathVariable UUID eventId,
			@Valid @RequestBody CollectionDtos.CollectionPaymentRequest request) {
		return ApiResponse.success(eventCollectionService.recordCollection(eventId, request), "Collection recorded");
	}

	@GetMapping("/collections")
	@Operation(summary = "List collection payments for an event")
	public ApiResponse<List<CollectionDtos.CollectionPaymentResponse>> listCollections(@PathVariable UUID eventId) {
		return ApiResponse.success(eventCollectionService.listCollections(eventId));
	}

	@GetMapping("/balance")
	@Operation(summary = "Get event balance (collections minus expenses)")
	public ApiResponse<CollectionDtos.EventBalanceResponse> getBalance(@PathVariable UUID eventId) {
		return ApiResponse.success(eventCollectionService.getBalance(eventId));
	}

}
