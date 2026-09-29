package com.gurukul.common.pdf;

import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Generic HTML-to-PDF rendering, shared by every PDF this backend produces (report cards today;
 * ID cards, payslips and certificates are meant to reuse it rather than pick another library).
 *
 * <p>{@link #render} fills a Thymeleaf template from {@code classpath:templates/pdf/<name>.html} and
 * hands the resulting XHTML to OpenHTMLtoPDF (PDFBox). Templates must be well-formed XHTML (close
 * every tag, no named entities like {@code &nbsp;}). Every template gets:
 * <ul>
 *   <li>font families {@code "Noto Sans"} and {@code "Noto Sans Devanagari"} (bundled, OFL) - list
 *   both in {@code font-family} so Hindi characters have glyphs;</li>
 *   <li>a {@code pdf} variable ({@link PdfText}) for correctly-shaped Devanagari text.</li>
 * </ul>
 */
@Service
public class PdfRenderService {

	private static final String FONT_DIR = "pdf/fonts/";

	private final TemplateEngine templateEngine;
	/** Font files read once from the jar, then handed to each render as a fresh stream. */
	private final Map<String, byte[]> fontBytes = new ConcurrentHashMap<>();

	public PdfRenderService() {
		ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
		resolver.setPrefix("templates/pdf/");
		resolver.setSuffix(".html");
		resolver.setTemplateMode(TemplateMode.HTML);
		resolver.setCharacterEncoding("UTF-8");
		resolver.setCacheable(true);
		templateEngine = new TemplateEngine();
		templateEngine.setTemplateResolver(resolver);
	}

	/** Renders {@code templateName} with {@code model} and returns the PDF bytes. */
	public byte[] render(String templateName, Map<String, ?> model) {
		Context context = new Context(Locale.ENGLISH);
		model.forEach(context::setVariable);
		// A fresh helper per render: its shaped-image cache only lives for this one document.
		context.setVariable("pdf", new PdfText());
		String html = templateEngine.process(templateName, context);
		return toPdf(html);
	}

	private byte[] toPdf(String html) {
		try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			PdfRendererBuilder builder = new PdfRendererBuilder();
			builder.useFastMode();
			registerFont(builder, "NotoSans-Regular.ttf", "Noto Sans", 400);
			registerFont(builder, "NotoSans-Bold.ttf", "Noto Sans", 700);
			registerFont(builder, "NotoSansDevanagari-Regular.ttf", "Noto Sans Devanagari", 400);
			registerFont(builder, "NotoSansDevanagari-Bold.ttf", "Noto Sans Devanagari", 700);
			builder.withHtmlContent(html, null);
			builder.toStream(out);
			builder.run();
			return out.toByteArray();
		} catch (IOException e) {
			throw new UncheckedIOException("PDF rendering failed", e);
		}
	}

	private void registerFont(PdfRendererBuilder builder, String file, String family, int weight) {
		byte[] bytes = fontBytes.computeIfAbsent(file, f -> readClasspath(FONT_DIR + f));
		builder.useFont(() -> new ByteArrayInputStream(bytes), family, weight, FontStyle.NORMAL, true);
	}

	private static byte[] readClasspath(String location) {
		try (InputStream in = PdfRenderService.class.getClassLoader().getResourceAsStream(location)) {
			if (in == null) {
				throw new IllegalStateException("Missing classpath resource " + location);
			}
			return in.readAllBytes();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/** A {@code data:} URI for a classpath image, for embedding static assets in a template. */
	public static String classpathImageDataUri(String classpathLocation, String mimeType) {
		return dataUri(readClasspath(classpathLocation), mimeType);
	}

	public static String dataUri(byte[] bytes, String mimeType) {
		return "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(bytes);
	}

}
