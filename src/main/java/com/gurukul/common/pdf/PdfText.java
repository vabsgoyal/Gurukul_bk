package com.gurukul.common.pdf;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Template helper, exposed to every PDF template as {@code pdf}.
 *
 * <p>OpenHTMLtoPDF writes text glyph-by-glyph with no OpenType shaping, which is fine for Latin but
 * wrong for Devanagari: conjuncts (न्य), reph (र्म) and the pre-base ि matra need GSUB/GPOS shaping.
 * The JDK's own text layout (Java2D, HarfBuzz underneath) does shape them correctly, so any string
 * containing a complex-script character is drawn by Java2D into a high-resolution PNG and embedded
 * as an inline image instead. Plain Latin strings stay real (selectable) PDF text.
 *
 * <p>Templates use it as:
 * <pre>
 * &lt;img th:if="${pdf.needsShaping(name)}" th:src="${pdf.image(name, 14, true)}" th:style="${pdf.style(name, 14, true)}"/&gt;
 * &lt;span th:unless="${pdf.needsShaping(name)}" th:text="${name}"&gt;&lt;/span&gt;
 * </pre>
 * One instance per render (its cache of shaped images is per-document, so it never grows unbounded).
 * If Java2D can't render (e.g. a JVM missing native font libraries), {@link #needsShaping} returns
 * false for the rest of the process and the text falls back to unshaped PDF text - never a failed PDF.
 */
public class PdfText {

	private static final Logger log = LoggerFactory.getLogger(PdfText.class);

	/** Pixels per PDF point - 4x keeps the shaped text crisp when printed. */
	private static final float SCALE = 4f;

	private static final Font REGULAR = loadFont("pdf/fonts/NotoSansDevanagari-Regular.ttf");
	private static final Font BOLD = loadFont("pdf/fonts/NotoSansDevanagari-Bold.ttf");
	private static volatile boolean java2dBroken = REGULAR == null || BOLD == null;

	private final Map<String, Shaped> cache = new ConcurrentHashMap<>();

	private record Shaped(String dataUri, float widthPt, float heightPt) {
	}

	/** True if {@code text} contains an Indic character that plain PDF text would mis-shape. */
	public boolean needsShaping(String text) {
		if (text == null || java2dBroken) {
			return false;
		}
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			// Devanagari (U+0900-097F) and Devanagari Extended (U+A8E0-A8FF).
			if ((c >= 'ऀ' && c <= 'ॿ') || (c >= '꣠' && c <= 'ꣿ')) {
				return shape(text, 12, false) != null;
			}
		}
		return false;
	}

	/** A PNG data URI of {@code text} shaped at {@code sizePt} points. */
	public String image(String text, float sizePt, boolean bold) {
		Shaped shaped = shape(text, sizePt, bold);
		return shaped == null ? "" : shaped.dataUri();
	}

	/** Inline CSS sizing the image so it lines up with surrounding text of the same point size. */
	public String style(String text, float sizePt, boolean bold) {
		Shaped shaped = shape(text, sizePt, bold);
		if (shaped == null) {
			return "";
		}
		return "width:%.2fpt;height:%.2fpt;vertical-align:middle;".formatted(shaped.widthPt(), shaped.heightPt());
	}

	private Shaped shape(String text, float sizePt, boolean bold) {
		if (java2dBroken) {
			return null;
		}
		String key = (bold ? "b|" : "r|") + sizePt + "|" + text;
		Shaped cached = cache.get(key);
		if (cached != null) {
			return cached;
		}
		try {
			Shaped shaped = render(text, sizePt, bold);
			cache.put(key, shaped);
			return shaped;
		} catch (Throwable e) {
			// Headless JVMs without native font support throw Errors (UnsatisfiedLinkError etc.),
			// not just exceptions - either way, fall back to unshaped text for the process lifetime.
			log.warn("Java2D text shaping unavailable, falling back to unshaped PDF text: {}", e.toString());
			java2dBroken = true;
			return null;
		}
	}

	private static Shaped render(String text, float sizePt, boolean bold) throws IOException {
		Font font = (bold ? BOLD : REGULAR).deriveFont(sizePt * SCALE);
		FontRenderContext frc = new FontRenderContext(null, true, true);
		TextLayout layout = new TextLayout(text, font, frc);
		Rectangle2D bounds = layout.getBounds();
		float ascent = layout.getAscent();
		float descent = layout.getDescent();
		int width = (int) Math.ceil(Math.max(bounds.getMaxX(), layout.getAdvance())) + 2;
		int height = (int) Math.ceil(ascent + descent) + 2;

		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		try {
			g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
			g.setColor(new Color(0x1F, 0x1F, 0x2E));
			layout.draw(g, 1, ascent + 1);
		} finally {
			g.dispose();
		}

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(image, "png", out);
		String uri = "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
		return new Shaped(uri, width / SCALE, height / SCALE);
	}

	private static Font loadFont(String classpathLocation) {
		try (InputStream in = PdfText.class.getClassLoader().getResourceAsStream(classpathLocation)) {
			if (in == null) {
				log.warn("PDF font {} missing from the classpath", classpathLocation);
				return null;
			}
			return Font.createFont(Font.TRUETYPE_FONT, in);
		} catch (Throwable e) {
			log.warn("Could not load {} for Java2D shaping: {}", classpathLocation, e.toString());
			return null;
		}
	}

}
