-- Razorpay gateway support, layered onto the existing payment_attempt table rather than a new one:
-- an attempt is an attempt regardless of how it was routed, and reconciliation/receipts already read
-- from here. `provider` tells the two apart. The pre-gateway UPI-intent columns
-- (upi_transaction_id/approval_ref_no/response_code) stay untouched - that path remains as an
-- automatic fallback for as long as app.razorpay.* is unconfigured.
ALTER TABLE payment_attempt ADD COLUMN provider VARCHAR(20) NOT NULL DEFAULT 'UPI_INTENT';
ALTER TABLE payment_attempt ADD COLUMN razorpay_order_id VARCHAR(64);
ALTER TABLE payment_attempt ADD COLUMN razorpay_payment_id VARCHAR(64);
ALTER TABLE payment_attempt ADD COLUMN razorpay_signature VARCHAR(255);
-- Which instrument the payer actually used (upi/card/netbanking/wallet), as reported by Razorpay.
-- Informational only - never trusted for authorization.
ALTER TABLE payment_attempt ADD COLUMN payment_method VARCHAR(30);
ALTER TABLE payment_attempt ADD COLUMN failure_reason VARCHAR(500);

-- The webhook arrives with no X-School-Id header, so the order id is the ONLY handle we have to
-- resolve an attempt (and through it, its school). It must therefore be unique globally, not
-- per-school. Both Postgres and H2 treat NULLs as distinct in a unique index, so the many existing
-- UPI_INTENT rows with a NULL order id do not collide.
CREATE UNIQUE INDEX uq_payment_attempt_rzp_order ON payment_attempt(razorpay_order_id);
