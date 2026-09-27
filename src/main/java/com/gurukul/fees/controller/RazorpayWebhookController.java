package com.gurukul.fees.controller;

import com.gurukul.common.ApiResponse;
import com.gurukul.fees.service.RazorpayPaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Razorpay's server-to-server payment notification.
 *
 * <p>This is the reason a payment can't be lost. The checkout callback only fires if the app is
 * still alive and online when Checkout finishes; a student who force-quits, loses signal, or has
 * their battery die between paying and returning would otherwise have their money taken and their
 * fee left showing unpaid. The webhook arrives regardless.
 *
 * <p>Unauthenticated by necessity - Razorpay has no login here, no JWT, and cannot send our
 * X-School-Id header. Authenticity comes from the HMAC signature over the raw body instead, so this
 * endpoint is exempted in BOTH SecurityConfig and SchoolContextFilter. The school is resolved from
 * the order id.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Payment Webhooks", description = "Gateway callbacks. Not called by the app; authenticated by signature.")
public class RazorpayWebhookController {

	public static final String WEBHOOK_PATH = "/api/v1/webhooks/razorpay";

	private static final Logger log = LoggerFactory.getLogger(RazorpayWebhookController.class);

	private final RazorpayPaymentService razorpayPaymentService;

	/**
	 * The body is taken as a raw String, never bound to a DTO. Jackson would happily deserialize it,
	 * but re-serializing to check the signature produces different bytes (key order, whitespace) and
	 * every verification would fail. These must be the exact bytes Razorpay signed.
	 *
	 * <p>Answers 200 even for events about orders this system has never heard of: Razorpay retries
	 * on any non-2xx, and retrying an event that can never be matched just generates noise forever.
	 * A bad signature is the one case that does fail loudly - that one should never be acknowledged.
	 */
	@PostMapping(WEBHOOK_PATH)
	@Operation(summary = "Razorpay payment webhook (payment.captured / payment.failed)",
			description = "Verified via HMAC-SHA256 of the raw body against the webhook secret. "
					+ "Not for client use - the app has no way to produce a valid signature.")
	public ResponseEntity<ApiResponse<Void>> handleRazorpayWebhook(
			@RequestBody String rawBody,
			@RequestHeader(name = "X-Razorpay-Signature", required = false) String signature) {
		boolean handled = razorpayPaymentService.handleWebhook(rawBody, signature);
		if (!handled) {
			log.info("Razorpay webhook acknowledged without action");
		}
		return ResponseEntity.status(HttpStatus.OK)
				.body(ApiResponse.success(null, handled ? "Processed" : "Acknowledged"));
	}

}
