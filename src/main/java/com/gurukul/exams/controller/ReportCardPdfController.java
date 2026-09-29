package com.gurukul.exams.controller;

import com.gurukul.exams.service.ReportCardPdfService;
import com.gurukul.exams.service.ReportCardPdfService.PdfFile;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Report Cards")
public class ReportCardPdfController {

	private final ReportCardPdfService reportCardPdfService;

	@GetMapping("/api/v1/students/{studentId}/report-card.pdf")
	@Operation(summary = "Download a student's report card for a term as a PDF",
			description = "Same access rules as GET /api/v1/students/{studentId}/report-card: a STUDENT only "
					+ "their own and a PARENT only a linked child's, both only once published; TEACHER/ADMIN may "
					+ "preview any time, and an unpublished card carries a DRAFT watermark.")
	public ResponseEntity<byte[]> student(@PathVariable UUID studentId, @RequestParam String term) {
		return pdf(reportCardPdfService.studentReportCard(studentId, term));
	}

	@GetMapping("/api/v1/class-sections/{sectionId}/report-cards.pdf")
	@Operation(summary = "Download every student's report card in a section as one PDF (one page each)",
			description = "Admin, or that section's class teacher only - same rules as "
					+ "GET /api/v1/class-sections/{sectionId}/report-cards. DRAFT watermark until published.")
	public ResponseEntity<byte[]> section(@PathVariable UUID sectionId, @RequestParam String term) {
		return pdf(reportCardPdfService.sectionReportCards(sectionId, term));
	}

	private static ResponseEntity<byte[]> pdf(PdfFile file) {
		return ResponseEntity.ok()
				.contentType(MediaType.APPLICATION_PDF)
				.header(HttpHeaders.CONTENT_DISPOSITION,
						ContentDisposition.attachment().filename(file.filename()).build().toString())
				.body(file.content());
	}

}
