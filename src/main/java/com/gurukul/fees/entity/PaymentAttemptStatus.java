package com.gurukul.fees.entity;

/**
 * UPI application callback is not authoritative proof of payment. Production implementation must
 * verify the transaction using an authorised PSP/payment provider or equivalent server-side
 * verification mechanism. RESPONSE_SUCCESS reflects only what the UPI app claimed when control
 * returned to this app - VERIFIED is reserved for a real, independently-confirmed payment (e.g. via
 * a payment gateway webhook/status-check API) and is not set by anything in this codebase yet.
 */
public enum PaymentAttemptStatus {
	INITIATED,
	RESPONSE_SUCCESS,
	PENDING,
	FAILED,
	CANCELLED,
	UNKNOWN,
	VERIFIED
}
