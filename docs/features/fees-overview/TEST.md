# Fees Overview & My Class Fees — Test Plan

## Manual test checklist
- [ ] As Admin, open Fee Assessments and confirm every student in the school appears, across multiple class-sections.
- [ ] Apply each status filter chip (All/Unpaid/Partial/Overdue/Paid) and confirm the visible list and the summary card's counts match.
- [ ] Search by student name and by roll number and confirm the client-side filter narrows correctly.
- [ ] As a homeroom Teacher, confirm the dashboard shows a "My Class Fees" tile and it opens directly to that teacher's own section with no section picker.
- [ ] As a Teacher who is *not* a homeroom/class teacher of any section, confirm the "My Class Fees" tile does not appear at all.
- [ ] As a Teacher, call `GET /api/v1/class-sections/{id}/fee-status` directly for a section you are **not** the class teacher of — confirm HTTP 403 ("You are not the class teacher of this section").
- [ ] As Admin, call the same endpoint for any section — confirm it succeeds regardless of whether Admin is that section's class teacher.
- [ ] Create a fee assessment, let its due date pass without recording any payment, then check the status filter chips and `reports/dues` — confirm it still shows as "Unpaid," not "Overdue" (status only updates on payment).
- [ ] Make a partial payment on an assessment past its due date, and confirm it now correctly flips to `OVERDUE` (since `computeStatus` runs on `recordPayment`).
- [ ] As a Teacher (or any non-admin role, or no token at all), call `GET /api/v1/fee-assessments` directly with a valid `X-School-Id` — confirm it currently returns the whole school's assessments (flag as a gap, since the UI hides this tile from teachers but the API does not enforce it).
- [ ] Call `GET /api/v1/reports/dues` and confirm `totalUnpaid` matches the sum of all Unpaid/Partial/Overdue assessments' remaining dues, and `totalOverdue`/`overdueAssessments` only include assessments whose stored status is `OVERDUE`.
- [ ] A teacher who is class teacher of two different sections — confirm which section "My Class Fees" resolves to (the dashboard only picks the first match), and treat a second-section view as currently unsupported.

## Existing automated test coverage
- `D:\Gurukul\Gurukul_bk\src\test\java\com\gurukul\fees\ClassSectionFeeStatusIntegrationTest.java` — `classTeacherSeesOwnSectionFeesButAnUnrelatedTeacherCannot()`: creates a class-section, assigns a class teacher, generates a fee assessment for a student in it, then asserts: the assigned class teacher can see the section's fee status (200, correct `totalDue`); an unrelated teacher gets HTTP 403 calling the same endpoint; and Admin can see it regardless. This is the authoritative test for the `GET /api/v1/class-sections/{id}/fee-status` ownership rule that backs "My Class Fees."
- `D:\Gurukul\Gurukul_bk\src\test\java\com\gurukul\reports\ReportOverviewIntegrationTest.java` — `duesReportIncludesUnpaidAssessmentAndItsOverdueSubset()`: creates a class-section, fee category, fee structure, and student, generates an assessment, then calls `GET /api/v1/reports/dues` and asserts `totalUnpaid` is at least the assessment's amount and `unpaidAssessments` is non-empty. Covers the dues-report happy path but not the `overdueAssessments`/`totalOverdue` subset specifically, nor the stale-status limitation noted above.

## Suggested additional test coverage
- A direct test that `GET /api/v1/fee-assessments` and `GET /api/v1/reports/dues` have no role restriction today — call them as TEACHER, STUDENT, PARENT, and with no `Authorization` header at all, and assert they currently succeed, so this gap is documented in the test suite (and the test can be flipped to assert a 403 the moment role enforcement is added).
- A test proving the stale-status limitation directly: create an assessment, advance/mock the clock (or set a due date in the past at creation), and assert its status is still `UNPAID`/not `OVERDUE` until a payment is recorded against it — this is currently undocumented in the test suite and is easy to regress either direction (accidentally "fixing" it without updating the docs, or someone assuming it already auto-updates).
- A test for `classSectionId` filtering on `GET /api/v1/fee-assessments` (currently supported by the API but unused by any screen and not directly asserted by any existing test).
- A test for a teacher who is class teacher of more than one section, to define expected behavior (today the frontend just picks the first match; there is no equivalent backend test of this ambiguity).
- A test asserting `GET /api/v1/students/{studentId}/fee-assessments` correctly rejects a PARENT not linked to that student, and allows one who is — the ownership check exists in code (`parentService.requireLinkedChild`) but no automated test was found exercising it directly in the reports/fees-overview context.
