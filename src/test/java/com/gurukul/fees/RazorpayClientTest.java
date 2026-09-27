package com.gurukul.fees;

import com.gurukul.fees.gateway.RazorpayClient;
import com.gurukul.fees.gateway.RazorpayProperties;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Signature verification and money conversion - the two places a mistake silently costs real money
 * rather than throwing. No HTTP is exercised here; the RestClient is a mock that is never called.
 *
 * <p>The expected HMACs below are not copied from Razorpay's docs; they are the values this
 * algorithm must produce for these inputs, and exist to catch an accidental change of key, encoding
 * or hex casing.
 */
class RazorpayClientTest {

	private static final String KEY_SECRET = "test_key_secret";
	private static final String WEBHOOK_SECRET = "test_webhook_secret";

	private RazorpayClient clientWith(String keySecret, String webhookSecret) {
		RazorpayProperties properties = new RazorpayProperties(
				true, "https://api.razorpay.com", "rzp_test_key", keySecret, webhookSecret, "INR", 20);
		return new RazorpayClient(mock(org.springframework.web.client.RestClient.class), properties);
	}

	@Test
	void acceptsACorrectCheckoutSignature() {
		RazorpayClient client = clientWith(KEY_SECRET, WEBHOOK_SECRET);
		// HMAC-SHA256("order_TEST123|pay_TEST123", "test_key_secret")
		String signature = hmac("order_TEST123|pay_TEST123", KEY_SECRET);

		assertThat(client.isValidCheckoutSignature("order_TEST123", "pay_TEST123", signature)).isTrue();
	}

	@Test
	void rejectsASignatureFromADifferentOrder() {
		RazorpayClient client = clientWith(KEY_SECRET, WEBHOOK_SECRET);
		// A signature that is perfectly valid - for someone else's order. Without binding the
		// signature to the order id, this is exactly how one student's real payment could be
		// replayed to clear another student's fee.
		String otherOrdersSignature = hmac("order_OTHER|pay_TEST123", KEY_SECRET);

		assertThat(client.isValidCheckoutSignature("order_TEST123", "pay_TEST123", otherOrdersSignature)).isFalse();
	}

	@Test
	void rejectsASignatureComputedWithTheWrongSecret() {
		RazorpayClient client = clientWith(KEY_SECRET, WEBHOOK_SECRET);
		String forged = hmac("order_TEST123|pay_TEST123", "attacker_guess");

		assertThat(client.isValidCheckoutSignature("order_TEST123", "pay_TEST123", forged)).isFalse();
	}

	@Test
	void rejectsMissingOrBlankSignatures() {
		RazorpayClient client = clientWith(KEY_SECRET, WEBHOOK_SECRET);

		assertThat(client.isValidCheckoutSignature("order_TEST123", "pay_TEST123", null)).isFalse();
		assertThat(client.isValidCheckoutSignature("order_TEST123", "pay_TEST123", "  ")).isFalse();
	}

	@Test
	void verifiesWebhookSignatureOverTheExactRawBody() {
		RazorpayClient client = clientWith(KEY_SECRET, WEBHOOK_SECRET);
		String rawBody = "{\"event\":\"payment.captured\",\"payload\":{\"payment\":{\"entity\":{\"id\":\"pay_1\"}}}}";

		assertThat(client.isValidWebhookSignature(rawBody, hmac(rawBody, WEBHOOK_SECRET))).isTrue();
		// One byte of whitespace difference - what re-serializing a parsed body would produce.
		assertThat(client.isValidWebhookSignature(rawBody + " ", hmac(rawBody, WEBHOOK_SECRET))).isFalse();
	}

	@Test
	void webhookSignatureUsesTheWebhookSecretNotTheKeySecret() {
		RazorpayClient client = clientWith(KEY_SECRET, WEBHOOK_SECRET);
		String rawBody = "{\"event\":\"payment.captured\"}";

		assertThat(client.isValidWebhookSignature(rawBody, hmac(rawBody, KEY_SECRET))).isFalse();
		assertThat(client.isValidWebhookSignature(rawBody, hmac(rawBody, WEBHOOK_SECRET))).isTrue();
	}

	@Test
	void rejectsEveryWebhookWhenNoWebhookSecretIsConfigured() {
		RazorpayClient client = clientWith(KEY_SECRET, "");
		String rawBody = "{\"event\":\"payment.captured\"}";

		// Fail closed: unable to verify must never mean "trust it".
		assertThat(client.isValidWebhookSignature(rawBody, hmac(rawBody, WEBHOOK_SECRET))).isFalse();
	}

	@Test
	void convertsRupeesToPaiseWithoutLosingAPaisa() {
		assertThat(RazorpayClient.toPaise(new BigDecimal("1250.50"))).isEqualTo(125050L);
		assertThat(RazorpayClient.toPaise(new BigDecimal("0.01"))).isEqualTo(1L);
		assertThat(RazorpayClient.toPaise(new BigDecimal("10000.00"))).isEqualTo(1000000L);
		assertThat(RazorpayClient.toRupees(125050L)).isEqualByComparingTo("1250.50");
	}

	@Test
	void refusesToSilentlyRoundAnAmountFinerThanAPaisa() {
		// Charging a rounded amount would leave the fee permanently short of PAID. Better to fail
		// loudly than to quietly take the wrong number.
		assertThatThrownBy(() -> RazorpayClient.toPaise(new BigDecimal("100.005")))
				.isInstanceOf(ArithmeticException.class);
	}

	@Test
	void isNotConfiguredWhenDisabledOrCredentialsAreBlank() {
		assertThat(new RazorpayProperties(false, "u", "k", "s", "w", "INR", 20).isConfigured()).isFalse();
		assertThat(new RazorpayProperties(true, "u", "", "s", "w", "INR", 20).isConfigured()).isFalse();
		assertThat(new RazorpayProperties(true, "u", "k", "  ", "w", "INR", 20).isConfigured()).isFalse();
		assertThat(new RazorpayProperties(true, "u", "k", "s", "w", "INR", 20).isConfigured()).isTrue();
		// Keys present but the webhook not yet registered is a legitimate mid-setup state.
		assertThat(new RazorpayProperties(true, "u", "k", "s", "", "INR", 20).isConfigured()).isTrue();
		assertThat(new RazorpayProperties(true, "u", "k", "s", "", "INR", 20).isWebhookConfigured()).isFalse();
	}

	private static String hmac(String payload, String secret) {
		try {
			javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
			mac.init(new javax.crypto.spec.SecretKeySpec(
					secret.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
			return java.util.HexFormat.of()
					.formatHex(mac.doFinal(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
		} catch (java.security.GeneralSecurityException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
