package com.gurukul.idcards.service;

import com.gurukul.common.pdf.PdfRenderService;
import com.gurukul.idcards.dto.IdCardDtos.IdCardResponse;
import com.gurukul.idcards.entity.IdCardOwnerType;
import com.gurukul.idcards.service.IdCardService.PrintData;
import com.gurukul.idcards.service.IdCardService.PrintEntry;
import com.gurukul.schools.entity.School;
import com.gurukul.schools.service.SchoolLogoService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * ID-card PDFs: one card on a card-sized page (85.6 x 54 mm, CR80), or A4 print sheets of
 * {@value #CARDS_PER_PAGE} cards (2 x 5) for a class-section or all staff. The data, and with it every
 * access check, comes from {@link IdCardService}'s print-data methods; this class only fetches images
 * and renders. Built from whatever people have filled in - a missing photo gets a silhouette, a
 * missing field a dash.
 *
 * <p>Deliberately not @Transactional, so the slow render doesn't hold a DB connection open.
 */
@Service
@RequiredArgsConstructor
public class IdCardPdfService {

	private static final Logger log = LoggerFactory.getLogger(IdCardPdfService.class);

	static final String TEMPLATE = "id-card";
	static final int CARDS_PER_PAGE = 10;
	private static final int COLUMNS = 2;
	private static final String PLACEHOLDER_LOGO = "pdf/gurukul-mark.png";
	/** Photos are downscaled before embedding, so a full class sheet stays small. */
	private static final int PHOTO_MAX_WIDTH = 300;
	private static final int PHOTO_MAX_HEIGHT = 400;

	private final IdCardService idCardService;
	private final PdfRenderService pdfRenderService;
	private final SchoolLogoService schoolLogoService;
	private final ProfilePhotoService photoService;
	private final IdCardQrService qrService;

	private volatile String placeholderLogoDataUri;

	public record PdfFile(String filename, byte[] content) {
	}

	/** One labelled line on a card. */
	public record Field(String label, String value) {
	}

	/** Everything the template draws for one card. */
	public record PrintCard(String kind, String name, List<Field> fields, String photoDataUri,
			String qrDataUri, String validity) {
	}

	public PdfFile studentCard(UUID studentId) {
		PrintData data = idCardService.studentPrintData(studentId);
		return new PdfFile("id-card-%s.pdf".formatted(slug(data.filenameStem(), "student")), render(data, false));
	}

	public PdfFile employeeCard(UUID employeeId) {
		PrintData data = idCardService.employeePrintData(employeeId);
		return new PdfFile("id-card-%s.pdf".formatted(slug(data.filenameStem(), "staff")), render(data, false));
	}

	/** Admin only (checked in IdCardService): every active student of the section. */
	public PdfFile sectionSheet(UUID sectionId) {
		PrintData data = idCardService.sectionPrintData(sectionId);
		return new PdfFile("id-cards-%s.pdf".formatted(slug(data.filenameStem(), "class")), render(data, true));
	}

	/** Admin only (checked in IdCardService): every active employee. */
	public PdfFile staffSheet() {
		PrintData data = idCardService.staffPrintData();
		return new PdfFile("id-cards-staff.pdf", render(data, true));
	}

	// ---------------------------------------------------------------- rendering

	byte[] render(PrintData data, boolean sheet) {
		School school = data.school();
		List<PrintCard> cards = data.entries().stream().map(this::toPrintCard).toList();
		String logoDataUri = schoolLogoService.fetchLogo(school.getId())
				.map(logo -> PdfRenderService.dataUri(logo.bytes(), logo.contentType()))
				.orElse(null);
		Map<String, Object> model = new HashMap<>();
		model.put("sheet", sheet);
		model.put("pages", sheet ? layout(cards) : List.of(List.of(cards)));
		model.put("schoolName", school.getName());
		model.put("schoolAddress", IdCardService.schoolAddress(school));
		model.put("schoolPhone", school.getContactPhone());
		model.put("hasSchoolLogo", logoDataUri != null);
		model.put("logoDataUri", logoDataUri != null ? logoDataUri : placeholderLogo());
		return pdfRenderService.render(TEMPLATE, model);
	}

	private PrintCard toPrintCard(PrintEntry entry) {
		IdCardResponse card = entry.card();
		boolean student = card.getOwnerType() == IdCardOwnerType.STUDENT;
		boolean hasEmergency = card.getEmergencyPhone() != null;
		List<Field> fields = new ArrayList<>();
		if (student) {
			fields.add(new Field("Class", dash(card.getClassSectionLabel())));
			fields.add(new Field("Roll No", dash(card.getRollNumber())));
			fields.add(new Field("Parent", dash(card.getParentName())));
			fields.add(new Field("Blood", dash(card.getBloodGroup())));
			fields.add(new Field(hasEmergency ? "Emergency" : "Parent Ph", dash(card.getCardPhone())));
		} else {
			fields.add(new Field("Role", dash(card.getDesignation())));
			fields.add(new Field("Blood", dash(card.getBloodGroup())));
			fields.add(new Field(hasEmergency ? "Emergency" : "Phone", dash(card.getCardPhone())));
			if (card.getEmergencyContactName() != null) {
				fields.add(new Field("Contact", card.getEmergencyContactName()));
			}
		}
		String photo = photoService.fetch(entry.photoKey())
				.map(image -> downscale(image.bytes()))
				.orElse(null);
		String validity = card.getAcademicYear() != null ? "Valid " + card.getAcademicYear() : null;
		return new PrintCard(student ? "STUDENT" : "STAFF", card.getName(), fields, photo,
				qrService.pngDataUri(card.getQrCode()), validity);
	}

	/** Pages of rows of {@value #COLUMNS} cards; the last row may be short. */
	static <T> List<List<List<T>>> layout(List<T> cards) {
		List<List<List<T>>> pages = new ArrayList<>();
		for (int start = 0; start < cards.size(); start += CARDS_PER_PAGE) {
			List<T> pageCards = cards.subList(start, Math.min(start + CARDS_PER_PAGE, cards.size()));
			List<List<T>> rows = new ArrayList<>();
			for (int row = 0; row < pageCards.size(); row += COLUMNS) {
				rows.add(pageCards.subList(row, Math.min(row + COLUMNS, pageCards.size())));
			}
			pages.add(rows);
		}
		return pages;
	}

	/**
	 * A JPEG data URI of the photo scaled to fit {@value #PHOTO_MAX_WIDTH}x{@value #PHOTO_MAX_HEIGHT}
	 * px (flattened onto white, for PNGs with transparency), or null if it can't be decoded - the card
	 * then shows the silhouette rather than failing.
	 */
	static String downscale(byte[] bytes) {
		try {
			BufferedImage source = ImageIO.read(new ByteArrayInputStream(bytes));
			if (source == null) {
				return null;
			}
			double scale = Math.min(1.0, Math.min(
					(double) PHOTO_MAX_WIDTH / source.getWidth(), (double) PHOTO_MAX_HEIGHT / source.getHeight()));
			int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
			int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
			BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
			Graphics2D g = target.createGraphics();
			try {
				g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
				g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
				g.setColor(Color.WHITE);
				g.fillRect(0, 0, width, height);
				g.drawImage(source, 0, 0, width, height, null);
			} finally {
				g.dispose();
			}
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			if (!ImageIO.write(target, "jpg", out)) {
				return null;
			}
			return PdfRenderService.dataUri(out.toByteArray(), "image/jpeg");
		} catch (Exception | LinkageError e) {
			// Corrupt/unsupported images (e.g. CMYK JPEGs) or a JVM without imaging support.
			log.warn("Could not decode a profile photo, using the placeholder: {}", e.toString());
			return null;
		}
	}

	private String placeholderLogo() {
		if (placeholderLogoDataUri == null) {
			placeholderLogoDataUri = PdfRenderService.classpathImageDataUri(PLACEHOLDER_LOGO, "image/png");
		}
		return placeholderLogoDataUri;
	}

	private static String dash(String value) {
		return value == null || value.isBlank() ? "-" : value;
	}

	static String slug(String value, String fallback) {
		if (value == null) {
			return fallback;
		}
		String slug = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+|-+$)", "");
		return slug.isEmpty() ? fallback : slug;
	}

}
