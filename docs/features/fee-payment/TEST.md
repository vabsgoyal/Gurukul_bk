# Fee Payment Recording & the (Disabled) Online Payment Attempt — Test Plan

## Manual test checklist

### Manual recording flow (reachable in current UI, via whichever staff/admin form calls `recordFeePayment`)
- [ ] Record a partial payment (amount < remaining due) and verify assessment status becomes `PARTIAL` and `totalPaid` increases by exactly the paid amount.
- [ ] Record enough additional payments to fully cover `totalDue` and verify status flips to `PAID`.
- [ ] Attempt to record a payment whose amount exceeds the current remaining due and verify the API returns 400 (`IllegalArgumentException` — "Payment amount exceeds remaining due").
- [ ] Attempt to record a payment for an assessment with a `dueDate` in the past while some amount is already paid but not fully; verify status becomes `OVERDUE` rather than `PARTIAL`.
- [ ] Verify each recorded payment produces a receipt number (via the linked `FinancialTransaction`) returned in `FeePaymentResponse.receiptNumber`.
- [ ] Verify `GET /api/v1/fee-payments/{id}` returns the same receipt number for a previously recorded payment.
- [ ] As a STUDENT principal, attempt to record a payment against another student's assessment and verify `AccessDeniedException`/403 ("You can only pay your own fees").
- [ ] As a PARENT principal, attempt to record a payment for a child not linked to them and verify it is rejected (`parentService.requireLinkedChild`).
- [ ] As a PARENT principal linked to the student, verify recording a payment for that child succeeds.
- [ ] Confirm cross-school isolation: a payment/assessment created under one `X-School-Id` is not visible/actionable under a different school's header.

### Online UPI flow (backend/screen still functional but unreachable from normal UI navigation)
- [ ] Confirm the "Pay Fees" button on `FeeAssessmentDetailScreen` only ever shows the "coming soon" toast and never navigates, for every role/fully-paid combination.
- [ ] Confirm no in-app path (menus, deep links surfaced in the UI, notifications) currently routes to the `'PayFees'` screen.
- [ ] (If manually driving the backend/screen directly) Create a payment request for an assessment with no `bankAccountNumber`/`bankIfsc` set on the school and verify `IllegalStateException` ("Your school has not set up a fee payment account yet...").
- [ ] Create a payment request for an already fully-paid assessment and verify `IllegalStateException` ("This fee assessment is already fully paid").
- [ ] Create a payment request and verify a `PaymentAttempt` is persisted with status `INITIATED` and a `transactionRef` starting with `FEE`, before any simulated UPI-app return.
- [ ] Verify `GET /api/v1/fee-assessments/{id}/payment-attempts/pending` returns that attempt while `INITIATED`/`PENDING`, and returns nothing once resolved.
- [ ] Report `RESPONSE_SUCCESS` for the attempt and verify the linked `StudentFeeAssessment` is marked `PAID` and a `FeePayment` row is created (only when `app.fees.unverified-upi-auto-mark-paid=true`).
- [ ] Re-report `RESPONSE_SUCCESS` for the same `transactionRef` and verify no second `FeePayment`/ledger entry is created (idempotency).
- [ ] Report `CANCELLED`/`FAILED`/`UNKNOWN` and verify the assessment is left unpaid and no `FeePayment` is created.
- [ ] Set `app.fees.unverified-upi-auto-mark-paid=false` and verify a `RESPONSE_SUCCESS` report updates the `PaymentAttempt` status only, without auto-marking the fee paid.
- [ ] Verify the UPI deep link uses `upiVpaOverride` when set on the school, and falls back to `<accountNumber>@<handle-from-IFSC>` (defaulting to `@upi` for an unmapped IFSC prefix) when not set.

## Existing automated test coverage
- `D:\Gurukul\Gurukul_bk\src\test\java\com\gurukul\fees\FeePaymentIntegrationTest.java` — full MockMvc integration test of the manual recording flow: creates a class section, fee category, fee structure, generates assessments, enrolls a student, re-generates assessments (asserting `UNPAID` / `totalDue = 10000.00`), records a partial `CASH` payment of 4000.00 (asserts status becomes `PARTIAL`, `totalPaid = 4000.00`, receipt number present), records a further `UPI`-method payment of 6000.00 to fully cover the due (asserts status becomes `PAID`), then attempts to overpay by 1.00 and asserts a 400 Bad Request.
- `D:\Gurukul\Gurukul_bk\src\test\java\com\gurukul\fees\PaymentAttemptIntegrationTest.java` — full MockMvc integration test of the online UPI attempt lifecycle: sets the school's bank account/IFSC, creates a class section/category/structure/student/assessment, confirms no pending attempt initially, calls `POST .../payment-request` (asserts amount = 9000.00, and an `INITIATED` `PaymentAttempt` is persisted with the returned `transactionRef` visible via the attempts-list endpoint), confirms the pending-attempt endpoint now surfaces that attempt, posts a `RESPONSE_SUCCESS` result with a UPI transaction ID and response code (asserts the attempt status updates and the assessment flips to `PAID`), confirms the pending-attempt endpoint clears afterward, then re-posts the same `RESPONSE_SUCCESS` result for the same `transactionRef` and asserts the assessment's `totalPaid` remains exactly 9000.00 (i.e., no double payment recorded).
- `D:\Gurukul\Gurukul_bk\src\test\java\com\gurukul\fees\FeePaymentServiceOwnershipTest.java` — focused Mockito unit test (no MockMvc/JWT) of the ownership check added to `FeePaymentService.createPaymentRequest`/`recordPayment` via `assertCanPayOrRecord`: (1) a STUDENT principal can create a payment request for their own assessment (asserts amount, that the generated `upiUri` starts with the expected `pa=<accountNumber>%40sbi` VPA derived from the `SBIN` IFSC prefix, and that `referenceId` starts with `FEE`); (2) a STUDENT principal authenticated as a *different* student is rejected with `AccessDeniedException` when targeting someone else's assessment; (3) an unauthenticated caller (no `SecurityContext` set) is unaffected by the ownership check and the call succeeds — documented in the test as mirroring "this codebase's existing (pre-existing gap)" of calling fee endpoints without auth.

## Suggested additional test coverage
- No integration test currently exercises the PARENT role's `requireLinkedChild` ownership path for `recordPayment`/`createPaymentRequest`/`recordAttemptResult` — only the STUDENT-vs-STUDENT ownership case is unit-tested (`FeePaymentServiceOwnershipTest`). A PARENT-linked-vs-PARENT-unlinked test is missing.
- No test covers a TEACHER or other non-STUDENT/PARENT authenticated role calling the payment endpoints for a student unrelated to them — since `assertCanPayOrRecord` only branches on STUDENT/PARENT, this should be explicitly tested to confirm/track the documented "pre-existing gap" rather than leaving it implicit.
- No test covers concurrent/racing calls to `recordAttemptResult` for the same `transactionRef` (e.g. two near-simultaneous `RESPONSE_SUCCESS` reports) to confirm whether double-payment can actually occur, given the idempotency check reads `previousStatus` without any observed row-level locking.
- No test covers `createPaymentRequest`'s `IllegalStateException` paths (school missing bank details; assessment already fully paid) — neither integration test exercises these branches.
- No test verifies the `unverifiedUpiAutoMarkPaid=false` configuration path (the property is read via `@Value` with a default of `true`; only the default-true behavior is covered).
- No test verifies the `upiVpaOverride` branch of the UPI URI construction (only the IFSC-fallback branch is covered, in `FeePaymentServiceOwnershipTest`).
- No frontend test (unit or e2e) exists asserting that the "Pay Fees" button on `FeeAssessmentDetailScreen.tsx` only shows the "coming soon" toast and does not navigate — worth adding a regression test/snapshot so a future change doesn't silently re-enable or further break this entry point without an explicit decision.
- No test covers `GET /api/v1/fee-payments/{id}` for a payment belonging to a different school (cross-tenant isolation) or for a nonexistent ID (404/`EntityNotFoundException` path).
