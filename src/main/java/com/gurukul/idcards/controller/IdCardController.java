package com.gurukul.idcards.controller;

import com.gurukul.common.ApiResponse;
import com.gurukul.idcards.dto.IdCardDtos.IdCardResponse;
import com.gurukul.idcards.dto.IdCardDtos.IdCardVerifyResponse;
import com.gurukul.idcards.dto.IdCardDtos.PresignPhotoRequest;
import com.gurukul.idcards.dto.IdCardDtos.PresignPhotoResponse;
import com.gurukul.idcards.dto.IdCardDtos.SetPhotoRequest;
import com.gurukul.idcards.dto.IdCardDtos.UpdateIdCardProfileRequest;
import com.gurukul.idcards.entity.IdCardOwnerType;
import com.gurukul.idcards.service.IdCardPdfService;
import com.gurukul.idcards.service.IdCardPdfService.PdfFile;
import com.gurukul.idcards.service.IdCardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
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
@RequestMapping("/api/v1/id-cards")
@RequiredArgsConstructor
@Tag(name = "ID Cards", description = """
		Profile-driven ID cards. A person fills in their own card details (photo, blood group, emergency
		contact) - for a student, the student or a linked parent. A person sees their own card, a parent
		their linked children's, an admin anyone's in the school. Requires X-School-Id.""")
public class IdCardController {

	private final IdCardService idCardService;
	private final IdCardPdfService idCardPdfService;

	@GetMapping("/me")
	@Operation(summary = "The caller's own ID card(s)",
			description = "A student or staff member gets their own card; a parent gets one per linked child.")
	public ApiResponse<List<IdCardResponse>> me() {
		return ApiResponse.success(idCardService.myCards());
	}

	// ---------------------------------------------------------------- students

	@GetMapping("/students/{studentId}")
	@Operation(summary = "A student's ID card data", description = "The student, a linked parent, or an admin.")
	public ApiResponse<IdCardResponse> student(@PathVariable UUID studentId) {
		return ApiResponse.success(idCardService.getStudentCard(studentId));
	}

	@PutMapping("/students/{studentId}/profile")
	@Operation(summary = "Update a student's ID-card details", description = "The student or a linked parent only.")
	public ApiResponse<IdCardResponse> updateStudent(
			@PathVariable UUID studentId, @Valid @RequestBody UpdateIdCardProfileRequest request) {
		return ApiResponse.success(idCardService.updateStudentProfile(studentId, request), "ID card details saved");
	}

	@PostMapping("/students/{studentId}/photo/presign")
	@Operation(summary = "Presigned upload URL for a student's photo",
			description = "The student or a linked parent only. PNG or JPEG, max 3 MB. 400 if storage isn't configured.")
	public ApiResponse<PresignPhotoResponse> presignStudentPhoto(
			@PathVariable UUID studentId, @Valid @RequestBody PresignPhotoRequest request) {
		return ApiResponse.success(idCardService.presignPhoto(IdCardOwnerType.STUDENT, studentId, request));
	}

	@PutMapping("/students/{studentId}/photo")
	@Operation(summary = "Set a student's photo from an uploaded object", description = "The student or a linked parent only.")
	public ApiResponse<IdCardResponse> setStudentPhoto(
			@PathVariable UUID studentId, @Valid @RequestBody SetPhotoRequest request) {
		return ApiResponse.success(idCardService.setPhoto(IdCardOwnerType.STUDENT, studentId, request.getObjectKey()), "Photo updated");
	}

	@DeleteMapping("/students/{studentId}/photo")
	@Operation(summary = "Remove a student's photo", description = "The student or a linked parent only.")
	public ApiResponse<IdCardResponse> removeStudentPhoto(@PathVariable UUID studentId) {
		return ApiResponse.success(idCardService.removePhoto(IdCardOwnerType.STUDENT, studentId), "Photo removed");
	}

	@GetMapping("/students/{studentId}/card.pdf")
	@Operation(summary = "Download a student's ID card (85.6 x 54 mm PDF)",
			description = "Same access as the card data. Missing details print as placeholders.")
	public ResponseEntity<byte[]> studentPdf(@PathVariable UUID studentId) {
		return pdf(idCardPdfService.studentCard(studentId));
	}

	// ---------------------------------------------------------------- employees

	@GetMapping("/employees/{employeeId}")
	@Operation(summary = "A staff member's ID card data", description = "The staff member themselves, or an admin.")
	public ApiResponse<IdCardResponse> employee(@PathVariable UUID employeeId) {
		return ApiResponse.success(idCardService.getEmployeeCard(employeeId));
	}

	@PutMapping("/employees/{employeeId}/profile")
	@Operation(summary = "Update a staff member's ID-card details", description = "That staff member only.")
	public ApiResponse<IdCardResponse> updateEmployee(
			@PathVariable UUID employeeId, @Valid @RequestBody UpdateIdCardProfileRequest request) {
		return ApiResponse.success(idCardService.updateEmployeeProfile(employeeId, request), "ID card details saved");
	}

	@PostMapping("/employees/{employeeId}/photo/presign")
	@Operation(summary = "Presigned upload URL for a staff photo",
			description = "That staff member only. PNG or JPEG, max 3 MB. 400 if storage isn't configured.")
	public ApiResponse<PresignPhotoResponse> presignEmployeePhoto(
			@PathVariable UUID employeeId, @Valid @RequestBody PresignPhotoRequest request) {
		return ApiResponse.success(idCardService.presignPhoto(IdCardOwnerType.EMPLOYEE, employeeId, request));
	}

	@PutMapping("/employees/{employeeId}/photo")
	@Operation(summary = "Set a staff photo from an uploaded object", description = "That staff member only.")
	public ApiResponse<IdCardResponse> setEmployeePhoto(
			@PathVariable UUID employeeId, @Valid @RequestBody SetPhotoRequest request) {
		return ApiResponse.success(idCardService.setPhoto(IdCardOwnerType.EMPLOYEE, employeeId, request.getObjectKey()), "Photo updated");
	}

	@DeleteMapping("/employees/{employeeId}/photo")
	@Operation(summary = "Remove a staff photo", description = "That staff member only.")
	public ApiResponse<IdCardResponse> removeEmployeePhoto(@PathVariable UUID employeeId) {
		return ApiResponse.success(idCardService.removePhoto(IdCardOwnerType.EMPLOYEE, employeeId), "Photo removed");
	}

	@GetMapping("/employees/{employeeId}/card.pdf")
	@Operation(summary = "Download a staff ID card (85.6 x 54 mm PDF)", description = "Same access as the card data.")
	public ResponseEntity<byte[]> employeePdf(@PathVariable UUID employeeId) {
		return pdf(idCardPdfService.employeeCard(employeeId));
	}

	// ---------------------------------------------------------------- admin sheets + verify

	@GetMapping("/class-sections/{sectionId}/sheet.pdf")
	@Operation(summary = "A4 print sheet of ID cards for a class-section (10 per page)",
			description = "Admin only. Every active student, built from whatever they have filled in.")
	public ResponseEntity<byte[]> sectionSheet(@PathVariable UUID sectionId) {
		return pdf(idCardPdfService.sectionSheet(sectionId));
	}

	@GetMapping("/staff/sheet.pdf")
	@Operation(summary = "A4 print sheet of ID cards for all active staff (10 per page)", description = "Admin only.")
	public ResponseEntity<byte[]> staffSheet() {
		return pdf(idCardPdfService.staffSheet());
	}

	@GetMapping("/verify")
	@Operation(summary = "Who an ID-card QR code belongs to",
			description = "Staff (teacher/admin) of the same school only. Identification only - this does not "
					+ "mark attendance. 404 for a tampered code or one from another school.")
	public ApiResponse<IdCardVerifyResponse> verify(@RequestParam String code) {
		return ApiResponse.success(idCardService.verify(code));
	}

	private static ResponseEntity<byte[]> pdf(PdfFile file) {
		return ResponseEntity.ok()
				.contentType(MediaType.APPLICATION_PDF)
				.header(HttpHeaders.CONTENT_DISPOSITION,
						ContentDisposition.attachment().filename(file.filename()).build().toString())
				.body(file.content());
	}

}
