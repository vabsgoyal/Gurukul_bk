package com.gurukul.idcards.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.gurukul.common.pdf.PdfRenderService;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;

/**
 * QR images for ID cards, with ZXing core (pure Java, no external API). Drawn pixel by pixel into a
 * BufferedImage (no fonts involved, so it works on any headless JVM), several pixels per module so
 * it stays sharp when a PDF viewer or printer scales it.
 */
@Component
public class IdCardQrService {

	private static final int PIXELS_PER_MODULE = 8;
	private static final int QUIET_ZONE_MODULES = 2;
	private static final int BLACK = 0xFF1F1F2E;
	private static final int WHITE = 0xFFFFFFFF;

	/** A PNG data URI of {@code text} as a QR code. */
	public String pngDataUri(String text) {
		return PdfRenderService.dataUri(png(text), "image/png");
	}

	byte[] png(String text) {
		BitMatrix matrix;
		try {
			// Encoded at 1 px per module (ZXing's minimum size), scaled up below.
			matrix = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, Map.of(
					EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M,
					EncodeHintType.MARGIN, QUIET_ZONE_MODULES));
		} catch (WriterException e) {
			throw new IllegalStateException("Could not encode QR code", e);
		}
		int size = matrix.getWidth() * PIXELS_PER_MODULE;
		BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
		for (int y = 0; y < size; y++) {
			for (int x = 0; x < size; x++) {
				image.setRGB(x, y, matrix.get(x / PIXELS_PER_MODULE, y / PIXELS_PER_MODULE) ? BLACK : WHITE);
			}
		}
		try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			ImageIO.write(image, "png", out);
			return out.toByteArray();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

}
