package com.gurukul.admissions.controller;

import com.gurukul.admissions.dto.AdmissionDtos.AdmissionRequest;
import com.gurukul.admissions.dto.AdmissionDtos.AdmissionResponse;
import com.gurukul.admissions.dto.AdmissionDtos.ConvertRequest;
import com.gurukul.admissions.dto.AdmissionDtos.ConvertResponse;
import com.gurukul.admissions.dto.AdmissionDtos.PresignDocumentRequest;
import com.gurukul.admissions.dto.AdmissionDtos.PresignDocumentResponse;
import com.gurukul.admissions.dto.AdmissionDtos.RegisterDocumentRequest;
import com.gurukul.admissions.dto.AdmissionDtos.StageChangeRequest;
import com.gurukul.admissions.entity.AdmissionStage;
import com.gurukul.admissions.service.AdmissionService;
import com.gurukul.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admissions")
@RequiredArgsConstructor
@Tag(name = "Admissions", description = "Admin-only admission applications and enrolment. Requires X-School-Id header.")
public class AdmissionController {

	private final AdmissionService admissionService;

	@GetMapping
	@Operation(summary = "List admission applications, newest first, optionally filtered by stage")
	public ApiResponse<List<AdmissionResponse>> list(
			@Parameter(description = "Only this stage") @RequestParam(required = false) AdmissionStage stage) {
		return ApiResponse.success(admissionService.list(stage));
	}

	@GetMapping("/{id}")
	@Operation(summary = "Get an application with its documents and any possible duplicate students")
	public ApiResponse<AdmissionResponse> get(@PathVariable UUID id) {
		return ApiResponse.success(admissionService.get(id));
	}

	@PostMapping
	@Operation(summary = "Record a new admission application (stage NEW)")
	public ApiResponse<AdmissionResponse> create(@Valid @RequestBody AdmissionRequest request) {
		return ApiResponse.success(admissionService.create(request), "Admission saved");
	}

	@PutMapping("/{id}")
	@Operation(summary = "Edit an application (not once enrolled)")
	public ApiResponse<AdmissionResponse> update(@PathVariable UUID id, @Valid @RequestBody AdmissionRequest request) {
		return ApiResponse.success(admissionService.update(id, request), "Admission updated");
	}

	@DeleteMapping("/{id}")
	@Operation(summary = "Delete an application (not once enrolled)")
	public ApiResponse<Void> delete(@PathVariable UUID id) {
		admissionService.delete(id);
		return ApiResponse.success(null, "Admission deleted");
	}

	@PatchMapping("/{id}/stage")
	@Operation(summary = "Move an application to another stage",
			description = "NEW->UNDER_REVIEW|REJECTED, UNDER_REVIEW->APPROVED|REJECTED, APPROVED->UNDER_REVIEW, "
					+ "REJECTED->UNDER_REVIEW. ENROLLED only via /convert.")
	public ApiResponse<AdmissionResponse> changeStage(@PathVariable UUID id, @Valid @RequestBody StageChangeRequest request) {
		return ApiResponse.success(admissionService.changeStage(id, request.getStage()), "Stage updated");
	}

	@PostMapping("/{id}/documents/presign")
	@Operation(summary = "Get a presigned upload URL for a supporting document (private, time-limited)")
	public ApiResponse<PresignDocumentResponse> presignDocument(
			@PathVariable UUID id, @Valid @RequestBody PresignDocumentRequest request) {
		return ApiResponse.success(admissionService.presignDocument(id, request));
	}

	@PostMapping("/{id}/documents")
	@Operation(summary = "Attach an uploaded document to the application")
	public ApiResponse<AdmissionResponse> registerDocument(
			@PathVariable UUID id, @Valid @RequestBody RegisterDocumentRequest request) {
		return ApiResponse.success(admissionService.registerDocument(id, request), "Document added");
	}

	@DeleteMapping("/{id}/documents/{documentId}")
	@Operation(summary = "Remove a document from the application")
	public ApiResponse<AdmissionResponse> deleteDocument(@PathVariable UUID id, @PathVariable UUID documentId) {
		return ApiResponse.success(admissionService.deleteDocument(id, documentId), "Document removed");
	}

	@PostMapping("/{id}/convert")
	@Operation(summary = "Enrol an approved application",
			description = "Creates the student in the chosen section (which creates the fee assessment when the "
					+ "section has a fee structure). Idempotent: an already-enrolled application returns its "
					+ "existing student with alreadyEnrolled=true. 409 POSSIBLE_DUPLICATE when a student with the "
					+ "same name, DOB and parent phone exists, unless allowDuplicate is true.")
	public ApiResponse<ConvertResponse> convert(@PathVariable UUID id, @Valid @RequestBody ConvertRequest request) {
		ConvertResponse response = admissionService.convert(id, request);
		return ApiResponse.success(response, response.isAlreadyEnrolled() ? "Already enrolled" : "Student enrolled");
	}

}
