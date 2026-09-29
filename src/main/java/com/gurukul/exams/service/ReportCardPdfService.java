package com.gurukul.exams.service;

import com.gurukul.common.EntityNotFoundException;
import com.gurukul.common.SchoolContext;
import com.gurukul.common.pdf.PdfRenderService;
import com.gurukul.exams.dto.ReportCardDtos.ReportCardResponse;
import com.gurukul.schools.entity.School;
import com.gurukul.schools.repository.SchoolRepository;
import com.gurukul.schools.service.SchoolLogoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Report-card PDFs. Access control is deliberately NOT implemented here: the data always comes from
 * {@link ReportCardService#getReportCard} / {@link ReportCardService#getSectionReportCards}, so a PDF
 * can never be seen by anyone who couldn't already see the JSON (student own-only and published-only,
 * parent linked-child-only, section view admin/class-teacher-only).
 *
 * <p>Deliberately not @Transactional: the report-card reads run in their own read-only
 * transactions, and the (comparatively slow) PDF render shouldn't hold a DB connection open.
 */
@Service
@RequiredArgsConstructor
public class ReportCardPdfService {

	static final String TEMPLATE = "report-card";
	private static final String PLACEHOLDER_LOGO = "pdf/gurukul-mark.png";
	private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

	private final ReportCardService reportCardService;
	private final PdfRenderService pdfRenderService;
	private final SchoolLogoService schoolLogoService;
	private final SchoolRepository schoolRepository;
	private final SchoolContext schoolContext;

	private volatile String placeholderLogoDataUri;

	public record PdfFile(String filename, byte[] content) {
	}

	public PdfFile studentReportCard(UUID studentId, String term) {
		ReportCardResponse card = reportCardService.getReportCard(studentId, term);
		String filename = "report-card-%s-%s.pdf".formatted(slug(card.getStudentName()), slug(term));
		return new PdfFile(filename, render(List.of(card), term));
	}

	public PdfFile sectionReportCards(UUID sectionId, String term) {
		List<ReportCardResponse> cards = reportCardService.getSectionReportCards(sectionId, term);
		if (cards.isEmpty()) {
			throw new IllegalStateException("This class-section has no students yet");
		}
		ReportCardResponse first = cards.get(0);
		String filename = "report-cards-%s-%s-%s.pdf".formatted(
				slug(first.getClassName()), slug(first.getSection()), slug(term));
		return new PdfFile(filename, render(cards, term));
	}

	private byte[] render(List<ReportCardResponse> cards, String term) {
		UUID schoolId = schoolContext.getSchoolId();
		School school = schoolRepository.findById(schoolId)
				.orElseThrow(() -> new EntityNotFoundException("School not found"));

		// Fetched once per request - the class PDF reuses it on every page.
		String logoDataUri = schoolLogoService.fetchLogo(schoolId)
				.map(logo -> PdfRenderService.dataUri(logo.bytes(), logo.contentType()))
				.orElse(null);

		Instant publishedAt = cards.stream().map(ReportCardResponse::getPublishedAt)
				.filter(Objects::nonNull).findFirst().orElse(null);

		Map<String, Object> model = new HashMap<>();
		model.put("title", "Report Card - " + term);
		model.put("cards", cards);
		model.put("schoolName", school.getName());
		model.put("schoolAddress", Stream.of(school.getAddress(), school.getCity(), school.getState(), school.getPincode())
				.filter(part -> part != null && !part.isBlank())
				.collect(Collectors.joining(", ")));
		model.put("hasSchoolLogo", logoDataUri != null);
		model.put("logoDataUri", logoDataUri != null ? logoDataUri : placeholderLogo());
		model.put("generatedOn", LocalDate.now(IST).format(DATE));
		model.put("publishedOn", publishedAt != null ? publishedAt.atZone(IST).toLocalDate().format(DATE) : null);
		return pdfRenderService.render(TEMPLATE, model);
	}

	private String placeholderLogo() {
		if (placeholderLogoDataUri == null) {
			placeholderLogoDataUri = PdfRenderService.classpathImageDataUri(PLACEHOLDER_LOGO, "image/png");
		}
		return placeholderLogoDataUri;
	}

	/** ASCII-only, filesystem-safe filename part ("Term 1" -> "term-1"; a Hindi-only name -> "student"). */
	static String slug(String value) {
		if (value == null) {
			return "student";
		}
		String slug = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+|-+$)", "");
		return slug.isEmpty() ? "student" : slug;
	}

}
