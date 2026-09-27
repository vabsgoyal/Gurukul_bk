package com.gurukul.finance.entity;

/**
 * How money moved. Persisted as a STRING into an unconstrained VARCHAR(20) (V2__finance_foundation),
 * so adding a value needs no migration - but every value must stay within 20 characters.
 *
 * <p>CARD/NETBANKING/WALLET exist because a gateway-routed fee payment reports the instrument the
 * payer actually used, and recording a card payment as UPI would put a falsehood in the ledger and
 * on the receipt. CARD additionally fixes a pre-existing mismatch: the payroll screen has offered a
 * "Card" option since before this enum was written, and submitting it rejected the request.
 */
public enum PaymentMethod {
	CASH,
	UPI,
	BANK_TRANSFER,
	CHEQUE,
	CARD,
	NETBANKING,
	WALLET
}
