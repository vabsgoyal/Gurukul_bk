package com.gurukul.fees.gateway;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

/**
 * Thin wrapper over the two Razorpay REST calls this app needs, plus the two HMAC verifications.
 *
 * <p>Deliberately not using the official {@code com.razorpay:razorpay-java} SDK: it would be a third
 * transitive source of org.json/OkHttp on a classpath that has already been bitten twice by
 * transitive conflicts (see the swagger-annotations exclusions in pom.xml), in exchange for wrapping
 * two HTTP calls and ten lines of javax.crypto. RestClient is already on the classpath and is what
 * every other outbound integration here uses.
 *
 * <p>JsonNode here is Jackson 3 (tools.jackson) - the version Spring Boot 4 registers its message
 * converters for. Jackson 2 (com.fasterxml.jackson) is also on the classpath transitively, and
 * using it here compiles but fails at runtime with "Type definition error: [simple type, class
 * com.fasterxml.jackson.databind.JsonNode]" the first time a response is actually deserialized.
 */
@Component
@RequiredArgsConstructor
public class RazorpayClient {

	private static final Logger log = LoggerFactory.getLogger(RazorpayClient.class);

	private static final String HMAC_SHA256 = "HmacSHA256";

	/** Razorpay rejects a receipt longer than this. Our FEE-prefixed refs are ~15 chars. */
	private static final int MAX_RECEIPT_LENGTH = 40;

	// Resolved by bean name (two RestClient beans exist - this one and openRouterRestClient), the
	// same way OpenRouterAiProvider does it. There is no lombok.config declaring @Qualifier as a
	// copyable annotation, so a field-level @Qualifier would be silently dropped from the generated
	// constructor - the field name is what actually does the disambiguating here.
	private final RestClient razorpayRestClient;
	private final RazorpayProperties properties;

	/** An order, as Razorpay created it. amountPaise echoes back what we asked for. */
	public record RazorpayOrder(String id, long amountPaise, String currency, String status) {}

	/**
	 * A payment, as Razorpay currently sees it. {@code status} is the authoritative one - notably
	 * "captured" (money taken) vs "authorized" (held but NOT yet taken; only happens when
	 * auto-capture is off on the account).
	 */
	public record RazorpayPayment(
			String id, String orderId, String status, String method, long amountPaise, String errorDescription) {}

	public boolean isConfigured() {
		return properties.isConfigured();
	}

	public String keyId() {
		return properties.keyId();
	}

	public String currency() {
		return properties.currency();
	}

	/**
	 * Razorpay deals exclusively in the currency's smallest unit. Rounding is HALF_UP on a value that
	 * is already 2dp in the DB, so this is a scale change rather than a real rounding decision -
	 * but it is asserted rather than assumed, since paying a rounded-down amount would leave a fee
	 * permanently a paisa short of PAID.
	 */
	public static long toPaise(BigDecimal rupees) {
		return rupees.setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact();
	}

	public static BigDecimal toRupees(long paise) {
		return BigDecimal.valueOf(paise).movePointLeft(2);
	}

	public RazorpayOrder createOrder(long amountPaise, String receipt, Map<String, String> notes) {
		requireConfigured();
		if (receipt.length() > MAX_RECEIPT_LENGTH) {
			throw new IllegalArgumentException("Razorpay receipt exceeds " + MAX_RECEIPT_LENGTH + " characters");
		}
		Map<String, Object> body = Map.of(
				"amount", amountPaise,
				"currency", properties.currency(),
				"receipt", receipt,
				"notes", notes);
		try {
			JsonNode response = razorpayRestClient.post()
					.uri("/v1/orders")
					.contentType(MediaType.APPLICATION_JSON)
					.body(body)
					.retrieve()
					.body(JsonNode.class);
			if (response == null || !response.hasNonNull("id")) {
				throw new PaymentGatewayException("Payment gateway returned no order. Please try again.");
			}
			return new RazorpayOrder(
					response.get("id").asString(),
					response.path("amount").asLong(),
					response.path("currency").asString(properties.currency()),
					response.path("status").asString(null));
		} catch (RestClientException ex) {
			// The exception message can echo the request/response, which for a 401 includes nothing
			// secret but is still gateway internals - log it, don't surface it to a student.
			log.error("Razorpay order creation failed (amountPaise={}, receipt={})", amountPaise, receipt, ex);
			throw new PaymentGatewayException("Could not reach the payment gateway. Please try again in a moment.", ex);
		}
	}

	public RazorpayPayment fetchPayment(String paymentId) {
		requireConfigured();
		try {
			JsonNode response = razorpayRestClient.get()
					.uri("/v1/payments/{id}", paymentId)
					.retrieve()
					.body(JsonNode.class);
			if (response == null || !response.hasNonNull("id")) {
				throw new PaymentGatewayException("Payment gateway returned no payment details. Please try again.");
			}
			return toPayment(response);
		} catch (RestClientException ex) {
			log.error("Razorpay payment fetch failed (paymentId={})", paymentId, ex);
			throw new PaymentGatewayException("Could not confirm the payment with the gateway. Please try again in a moment.", ex);
		}
	}

	/** Maps a Razorpay payment entity - from either the fetch API or a webhook payload; same shape. */
	public RazorpayPayment toPayment(JsonNode entity) {
		return new RazorpayPayment(
				entity.path("id").asString(null),
				entity.path("order_id").asString(null),
				entity.path("status").asString(null),
				entity.path("method").asString(null),
				entity.path("amount").asLong(),
				entity.path("error_description").asString(null));
	}

	/**
	 * Proves the checkout result came from Razorpay and was not forged or replayed with a different
	 * order by a modified client: signature = HMAC-SHA256("<orderId>|<paymentId>", keySecret).
	 */
	public boolean isValidCheckoutSignature(String orderId, String paymentId, String signature) {
		requireConfigured();
		return matchesHmac(orderId + "|" + paymentId, signature, properties.keySecret());
	}

	/**
	 * Verified over the EXACT bytes Razorpay sent. The caller must pass the raw request body - a
	 * body that has been deserialized and re-serialized will differ in key order and whitespace, and
	 * every signature check would fail.
	 */
	public boolean isValidWebhookSignature(String rawBody, String signature) {
		if (!properties.isWebhookConfigured()) {
			return false;
		}
		return matchesHmac(rawBody, signature, properties.webhookSecret());
	}

	private boolean matchesHmac(String payload, String signature, String secret) {
		if (signature == null || signature.isBlank()) {
			return false;
		}
		String expected = hmacSha256Hex(payload, secret);
		// Constant-time: a plain String.equals leaks how many leading characters matched, which is
		// enough to forge a signature byte by byte given enough attempts.
		return MessageDigest.isEqual(
				expected.getBytes(StandardCharsets.UTF_8),
				signature.getBytes(StandardCharsets.UTF_8));
	}

	private static String hmacSha256Hex(String payload, String secret) {
		try {
			Mac mac = Mac.getInstance(HMAC_SHA256);
			mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
			return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
		} catch (java.security.GeneralSecurityException ex) {
			// HmacSHA256 is mandated by the JDK - unreachable unless the key itself is unusable.
			throw new IllegalStateException("Unable to compute payment signature", ex);
		}
	}

	private void requireConfigured() {
		if (!properties.isConfigured()) {
			throw new IllegalStateException(
					"Online fee payment is not set up for this school yet. Please contact your school admin.");
		}
	}

}
