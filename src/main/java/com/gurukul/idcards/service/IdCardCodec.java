package com.gurukul.idcards.service;

import com.gurukul.idcards.entity.IdCardOwnerType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/**
 * The signed identifier printed in an ID card's QR code:
 * {@code GK1.<S|E>.<ownerId>.<base64url(HMAC-SHA256(key, "GK1|type|ownerId|schoolId"))[0..16 bytes]>}.
 *
 * <p>The school id is part of what's signed but not part of the code, so a code from another school
 * (or with any character changed) simply fails verification - there's nothing to parse or trust.
 * It only identifies a person; there is no expiry or revocation (a verify call reports the person's
 * current status instead).
 *
 * <p>Key: {@code app.id-cards.qr-secret} if set, otherwise derived from {@code app.jwt.secret} with a
 * fixed label, so production needs no new secret. Rotating the JWT secret then invalidates printed
 * QRs - set APP_ID_CARDS_QR_SECRET to decouple them.
 */
@Component
public class IdCardCodec {

	static final String VERSION = "GK1";
	private static final String DERIVATION_LABEL = "gurukul-id-card-qr-v1";
	private static final int SIGNATURE_BYTES = 16;

	private final byte[] key;

	public record Subject(IdCardOwnerType type, UUID ownerId) {
	}

	public IdCardCodec(
			@Value("${app.id-cards.qr-secret:}") String qrSecret,
			@Value("${app.jwt.secret}") String jwtSecret) {
		this.key = qrSecret != null && !qrSecret.isBlank()
				? qrSecret.getBytes(StandardCharsets.UTF_8)
				: hmac(jwtSecret.getBytes(StandardCharsets.UTF_8), DERIVATION_LABEL);
	}

	public String encode(IdCardOwnerType type, UUID ownerId, UUID schoolId) {
		return VERSION + "." + type.code() + "." + ownerId + "." + signature(type, ownerId, schoolId);
	}

	/** The subject a code identifies, if it was signed by this server for {@code schoolId}. */
	public Optional<Subject> decode(String code, UUID schoolId) {
		if (code == null) {
			return Optional.empty();
		}
		String[] parts = code.trim().split("\\.");
		if (parts.length != 4 || !VERSION.equals(parts[0])) {
			return Optional.empty();
		}
		IdCardOwnerType type = IdCardOwnerType.fromCode(parts[1]);
		if (type == null) {
			return Optional.empty();
		}
		UUID ownerId;
		try {
			ownerId = UUID.fromString(parts[2]);
		} catch (IllegalArgumentException e) {
			return Optional.empty();
		}
		// UUID.fromString is lenient (e.g. leading zeros dropped) - only the canonical form is valid.
		if (!ownerId.toString().equals(parts[2])) {
			return Optional.empty();
		}
		byte[] expected = signature(type, ownerId, schoolId).getBytes(StandardCharsets.US_ASCII);
		byte[] actual = parts[3].getBytes(StandardCharsets.US_ASCII);
		return MessageDigest.isEqual(expected, actual) ? Optional.of(new Subject(type, ownerId)) : Optional.empty();
	}

	private String signature(IdCardOwnerType type, UUID ownerId, UUID schoolId) {
		String message = String.join("|", VERSION, type.code(), ownerId.toString(), schoolId.toString());
		byte[] mac = Arrays.copyOf(hmac(key, message), SIGNATURE_BYTES);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(mac);
	}

	private static byte[] hmac(byte[] key, String message) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(key, "HmacSHA256"));
			return mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException("HmacSHA256 unavailable", e);
		}
	}

}
