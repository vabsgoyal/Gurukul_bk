package com.gurukul.fees.dto;

import com.gurukul.fees.entity.PaymentAttemptStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "The parsed result of a UPI app's response, or the app's own best guess "
		+ "(e.g. self-reported by the user) when the UPI app returned no data at all")
public class PaymentAttemptResultRequest {

	@NotNull
	@Schema(description = "Parsed/self-reported outcome", example = "RESPONSE_SUCCESS")
	private PaymentAttemptStatus status;

	@Schema(description = "Transaction ID reported by the UPI app, if any")
	private String upiTransactionId;

	@Schema(description = "Approval reference number reported by the UPI app, if any")
	private String approvalRefNo;

	@Schema(description = "Response code reported by the UPI app, if any")
	private String responseCode;

	@Schema(description = "Raw response string received from the UPI app, if any, for audit/debugging")
	private String rawResponse;

}
