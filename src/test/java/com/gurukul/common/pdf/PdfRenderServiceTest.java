package com.gurukul.common.pdf;

import com.gurukul.exams.dto.ReportCardDtos.ReportCardResponse;
import com.gurukul.exams.dto.ReportCardDtos.SubjectResultResponse;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.File;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real renders through the shared PDF pipeline (Thymeleaf + OpenHTMLtoPDF + bundled Noto fonts),
 * using the report-card template. Run with -Dpdf.preview=true to also write the PDFs and a PNG of
 * each first page to target/pdf-preview/ for eyeballing (e.g. Devanagari conjunct shaping).
 */
class PdfRenderServiceTest {

	private final PdfRenderService service = new PdfRenderService();

	@Test
	void rendersHindiStudentAndSchoolNamesToAValidPdf() throws Exception {
		byte[] pdf = service.render("report-card", model(
				List.of(card("प्रिया शर्मा", true), card("Aarav Kumar", true)), "श्री विद्या निकेतन"));

		assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
		try (PDDocument document = Loader.loadPDF(pdf)) {
			assertThat(document.getNumberOfPages()).isEqualTo(2);
			String text = new PDFTextStripper().getText(document);
			assertThat(text).contains("Report Card - Term 1", "Aarav Kumar", "Mathematics", "Attendance");
			assertThat(text).doesNotContain("DRAFT");
			preview("hindi-published", pdf, document);
		}
	}

	@Test
	void unpublishedCardCarriesADraftWatermark() throws Exception {
		byte[] pdf = service.render("report-card", model(List.of(card("Aarav Kumar", false)), "Sunrise Public School"));

		try (PDDocument document = Loader.loadPDF(pdf)) {
			assertThat(document.getNumberOfPages()).isEqualTo(1);
			String text = new PDFTextStripper().getText(document);
			assertThat(text).contains("DRAFT", "Draft preview");
			preview("latin-draft", pdf, document);
		}
	}

	@Test
	void seventyStudentClassPdfRendersOnePagePerStudent() throws Exception {
		List<ReportCardResponse> cards = new ArrayList<>();
		for (int i = 1; i <= 70; i++) {
			cards.add(card(i % 2 == 0 ? "छात्र संख्या " + i : "Student " + i, true));
		}
		service.render("report-card", model(cards.subList(0, 2), "Sunrise Public School")); // warm-up
		long start = System.nanoTime();
		byte[] pdf = service.render("report-card", model(cards, "Sunrise Public School"));
		long millis = (System.nanoTime() - start) / 1_000_000;

		try (PDDocument document = Loader.loadPDF(pdf)) {
			assertThat(document.getNumberOfPages()).isEqualTo(70);
		}
		System.out.printf("70-page class report-card PDF: %d KB in %d ms%n", pdf.length / 1024, millis);
	}

	@Test
	void devanagariDetection() {
		PdfText text = new PdfText();
		assertThat(text.needsShaping("Aarav Kumar")).isFalse();
		assertThat(text.needsShaping(null)).isFalse();
		assertThat(text.needsShaping("प्रिया")).isTrue();
		assertThat(text.image("प्रिया", 12, false)).startsWith("data:image/png;base64,");
	}

	private static Map<String, Object> model(List<ReportCardResponse> cards, String schoolName) {
		Map<String, Object> model = new HashMap<>();
		model.put("title", "Report Card - Term 1");
		model.put("cards", cards);
		model.put("schoolName", schoolName);
		model.put("schoolAddress", "45 Ring Road, Jaipur, Rajasthan, 302001");
		model.put("hasSchoolLogo", false);
		model.put("logoDataUri", PdfRenderService.classpathImageDataUri("pdf/gurukul-mark.png", "image/png"));
		model.put("generatedOn", "29 Sep 2026");
		model.put("publishedOn", "28 Sep 2026");
		return model;
	}

	private static ReportCardResponse card(String name, boolean published) {
		List<SubjectResultResponse> subjects = List.of(
				new SubjectResultResponse(UUID.randomUUID(), "Hindi", "HIN", new BigDecimal("100"), new BigDecimal("88"), new BigDecimal("88.00"), "A"),
				new SubjectResultResponse(UUID.randomUUID(), "Mathematics", "MAT", new BigDecimal("100"), new BigDecimal("92"), new BigDecimal("92.00"), "A+"),
				new SubjectResultResponse(UUID.randomUUID(), "Science", "SCI", new BigDecimal("100"), new BigDecimal("75"), new BigDecimal("75.00"), "B"));
		return new ReportCardResponse(UUID.randomUUID(), name, "7", "Grade 7", "A", "2026-27", "Term 1",
				subjects, new BigDecimal("300"), new BigDecimal("255"), new BigDecimal("85.00"), "A",
				new BigDecimal("94.50"), published, published ? Instant.now() : null);
	}

	private static void preview(String name, byte[] pdf, PDDocument document) throws Exception {
		if (!Boolean.getBoolean("pdf.preview")) {
			return;
		}
		File dir = new File("target/pdf-preview");
		dir.mkdirs();
		java.nio.file.Files.write(new File(dir, name + ".pdf").toPath(), pdf);
		ImageIO.write(new PDFRenderer(document).renderImageWithDPI(0, 110), "png", new File(dir, name + ".png"));
	}

}
