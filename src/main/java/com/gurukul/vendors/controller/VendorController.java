package com.gurukul.vendors.controller;

import com.gurukul.common.ApiResponse;
import com.gurukul.vendors.dto.VendorRequest;
import com.gurukul.vendors.dto.VendorResponse;
import com.gurukul.vendors.service.VendorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/vendors")
@RequiredArgsConstructor
@Tag(name = "Vendors", description = "Vendor master data. Requires X-School-Id header.")
public class VendorController {

	private final VendorService vendorService;

	@GetMapping
	@Operation(summary = "List vendors")
	public ApiResponse<List<VendorResponse>> list() {
		return ApiResponse.success(vendorService.list());
	}

	@GetMapping("/{id}")
	@Operation(summary = "Get vendor by ID")
	public ApiResponse<VendorResponse> getById(@PathVariable UUID id) {
		return ApiResponse.success(vendorService.getById(id));
	}

	@PostMapping
	@Operation(summary = "Create vendor")
	public ApiResponse<VendorResponse> create(@Valid @RequestBody VendorRequest request) {
		return ApiResponse.success(vendorService.create(request), "Vendor created");
	}

	@PutMapping("/{id}")
	@Operation(summary = "Update vendor")
	public ApiResponse<VendorResponse> update(@PathVariable UUID id, @Valid @RequestBody VendorRequest request) {
		return ApiResponse.success(vendorService.update(id, request), "Vendor updated");
	}

}
