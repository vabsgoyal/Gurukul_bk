package com.gurukul.common.crypto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-GCM at-rest encryption for secrets that must be stored (not just hashed), like a Google
 * OAuth refresh token - unlike a password, we need the plaintext back to actually use it, so
 * hashing (as Credential does) isn't an option here. Key comes from app.security.encryption-key, a
 * base64-encoded 256-bit key - generate one with `openssl rand -base64 32`. A fresh random 12-byte
 * IV is generated per encrypt call and stored alongside the ciphertext (standard GCM practice -
 * reusing an IV with the same key breaks GCM's confidentiality guarantee).
 */
@Component
public class TokenCipher {

	private static final int GCM_TAG_LENGTH_BITS = 128;
	private static final int IV_LENGTH_BYTES = 12;

	private final SecureRandom random = new SecureRandom();
	private final byte[] keyBytes;

	public TokenCipher(@Value("${app.security.encryption-key:}") String base64Key) {
		this.keyBytes = base64Key.isBlank() ? null : Base64.getDecoder().decode(base64Key);
	}

	public String encrypt(String plaintext) {
		requireConfigured();
		try {
			byte[] iv = new byte[IV_LENGTH_BYTES];
			random.nextBytes(iv);
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(keyBytes, "AES"), new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
			byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
			byte[] combined = new byte[iv.length + ciphertext.length];
			System.arraycopy(iv, 0, combined, 0, iv.length);
			System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
			return Base64.getEncoder().encodeToString(combined);
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException("Failed to encrypt token", e);
		}
	}

	public String decrypt(String encoded) {
		requireConfigured();
		try {
			byte[] combined = Base64.getDecoder().decode(encoded);
			byte[] iv = new byte[IV_LENGTH_BYTES];
			System.arraycopy(combined, 0, iv, 0, IV_LENGTH_BYTES);
			byte[] ciphertext = new byte[combined.length - IV_LENGTH_BYTES];
			System.arraycopy(combined, IV_LENGTH_BYTES, ciphertext, 0, ciphertext.length);
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(keyBytes, "AES"), new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
			return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException("Failed to decrypt token", e);
		}
	}

	private void requireConfigured() {
		if (keyBytes == null) {
			throw new IllegalStateException("app.security.encryption-key is not configured");
		}
	}

}
