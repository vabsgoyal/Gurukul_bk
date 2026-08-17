# Payroll Overview — Test Plan

## Manual test checklist
- [ ] With no payroll runs at all, load the overview — confirm all counts/amounts are zero and `runs` is empty.
- [ ] Create and process (but don't pay) a run with 2 employees — confirm both are counted in `pendingEmployeeCount`/`pendingAmount`, and the run appears in `runs` with status `PROCESSED`.
- [ ] Pay that run — confirm both employees move to `paidEmployeeCount`/`paidAmount` and the pending counts drop back down accordingly, and the run's status in `runs` flips to `PAID`.
- [ ] Create a second run for a different month, process it, leave it unpaid — confirm the overview now shows a mix: one run's employees counted paid, the other's counted pending, and both appear separately in `runs`.
- [ ] Create a `DRAFT` run only (don't process) — confirm it contributes zero to both counts (no lines exist yet) but still appears in `runs` with `employeeCount: 0`.
- [ ] Confirm `paidAmount`/`pendingAmount` match the hand-summed `net` of the relevant runs' lines.
- [ ] Permission-boundary: call the endpoint with a TEACHER/STUDENT/PARENT token and with none at all — confirm it currently succeeds regardless (flag as a gap, not a pass).
- [ ] Cross-school isolation: confirm a run/line from one school never appears in another school's overview call.

## Existing automated test coverage
- `D:\Gurukul\Gurukul_bk\src\test\java\com\gurukul\reports\ReportOverviewIntegrationTest.java` — `payrollOverviewCountsPaidAndPendingEmployeesAcrossRuns()`: creates an employee and salary structure, creates a payroll run for a deliberately out-of-the-way year (2033, chosen to avoid colliding with seed data or other tests' runs on the shared test school), processes it, calls the overview endpoint and asserts `pendingEmployeeCount >= 1`, then pays the run, calls the overview again, and asserts `paidEmployeeCount >= 1` and `paidAmount >= 21000.00` (matching the salary structure's net). This test directly exercises the per-run paid/pending transition this feature is built on. The same file's `duesReportIncludesUnpaidAssessmentAndItsOverdueSubset()` test covers the separate `/api/v1/reports/dues` endpoint, not payroll.

## Suggested additional test coverage
- A scenario with two simultaneous runs in different states (one `PAID`, one `PROCESSED`) asserted together in one overview call, to directly verify the per-run (not global) bucketing — the existing test only ever has one run in flight at a time.
- Assert the `runs` list's per-entry fields (`month`, `year`, `status`, `employeeCount`, `totalNet`) directly, not just the two headline counts/amounts.
- A run with zero employees (all skipped during processing, e.g. no one had a qualifying salary structure) — confirm it appears in `runs` with `employeeCount: 0` and contributes nothing to either total.
- Because payment is actually recorded per employee (`SalaryPayment` per `PayrollLine`) but this report only reads run-level status: a regression test that would fail the moment someone introduces a genuine partial-run payment path, to catch this report silently becoming inaccurate rather than being updated alongside that change.
- Authorization tests once/if role enforcement is added to this endpoint — none exist today since the endpoint currently allows any caller with a valid `X-School-Id`.
