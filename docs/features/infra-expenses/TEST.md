# Infra Expenses (Request/Approve/Purchase/Pay) — Test Plan

## Manual test checklist
- [ ] Create a request (category, description, estimated amount) — confirm it starts as `DRAFT`.
- [ ] Submit it — confirm it moves to `SUBMITTED`.
- [ ] Approve it — confirm it moves to `APPROVED`.
- [ ] Record a purchase (vendor, invoice number, actual amount) against the approved request — confirm it moves to `PURCHASED`, and that the actual amount (not the original estimate) is what's stored.
- [ ] Pay the vendor (payment method required) — confirm it moves to `PAID`, a ledger outflow is posted for the actual purchase amount, and the school's fund summary/outflow total increases by that same amount.
- [ ] Create a second request, submit it, then reject it — confirm it moves to `REJECTED`.
- [ ] Resubmit the rejected request — confirm it moves back to `SUBMITTED` and can proceed through approval normally.
- [ ] Attempt to record a purchase against a request that is still `DRAFT` or `SUBMITTED` (not yet `APPROVED`) — confirm it is rejected ("Only approved requests can be purchased").
- [ ] Attempt to record a second purchase against a request that already has one — confirm it is rejected ("Purchase already recorded").
- [ ] Attempt to pay a request that hasn't been purchased yet — confirm it is rejected ("Only purchased requests can be paid").
- [ ] Attempt to pay the same purchase twice — confirm the second attempt is rejected ("Vendor already paid").
- [ ] Attempt to approve a request that was never submitted (still `DRAFT`) — confirm it is rejected ("Only submitted requests can be approved").
- [ ] In the app, open a `DRAFT` request's detail screen and confirm the "Record purchase" and "Mark as paid" buttons are visible even though pressing them will fail — this is a known UI gap, not a pass/fail bug in itself, but worth confirming the resulting error message is legible to a real user.
- [ ] Enter an invalid free-text payment method (e.g. "Cash please") in the "Mark as paid" form — confirm the backend rejects it (the DTO requires a real `PaymentMethod` enum value) and the app surfaces that rejection reasonably.
- [ ] Permission-boundary: call every stage endpoint (create/submit/approve/reject/purchase/pay) with a TEACHER, STUDENT, or PARENT token, and with no token at all — confirm current behavior (all succeed, since there is no role/auth check), and flag this explicitly as a gap.
- [ ] Create a vendor, then edit it via `VendorFormScreen` — confirm the update is reflected the next time that vendor is picked for a purchase.

## Existing automated test coverage
- `D:\Gurukul\Gurukul_bk\src\test\java\com\gurukul\expenses\infrastructure\InfraExpenseIntegrationTest.java` — `infraExpenseWorkflow()`: exercises the full happy path end to end — creates a request (asserts `DRAFT`), submits it (asserts `SUBMITTED`), approves it (asserts `APPROVED`), creates a vendor, records a purchase against the request (asserts `PURCHASED`), reads the finance summary's `totalOutflow` before paying, pays the vendor (asserts `PAID`), then re-reads the finance summary and asserts `totalOutflow` increased by exactly the purchase's `actualAmount` (14500.00). This is the only test file for this feature; it covers the full linear happy path and the ledger side-effect of paying, but does not test rejection, resubmission after rejection, any invalid state transition, double-purchase/double-payment guards, or any permission boundary.

## Suggested additional test coverage
- Rejection and resubmission: submit → reject → resubmit → approve, asserting each status transition and that the original estimate carries over unchanged (no revision history exists to test against, which is itself worth asserting explicitly).
- Invalid state transitions: purchasing a `DRAFT`/`SUBMITTED`/`REJECTED` request, paying a non-`PURCHASED` request, approving a never-submitted request, approving an already-`REJECTED` request without resubmitting — each should assert the specific `IllegalArgumentException` message currently thrown.
- Double-purchase and double-payment guards ("Purchase already recorded", "Vendor already paid") — not exercised by the existing single-pass happy-path test.
- A test proving the two status trackers (`InfraExpenseRequest.status` vs. the `ApprovalRequest`/`WorkflowService` trail) can be inspected independently, and that `recordPurchase`/`payVendor` only ever consult the former — useful as a guard against a future change accidentally introducing drift between the two.
- Role/authorization tests analogous to `FeePaymentServiceOwnershipTest` — none exist for this feature today, and would document (and eventually enforce) that only ADMIN should approve, reject, purchase, or pay.
- A test that the free-text `actor` field on submit/approve/reject is stored as given and not validated against the caller's real identity — to make this known gap explicit and regression-proof, rather than implicitly assumed.
