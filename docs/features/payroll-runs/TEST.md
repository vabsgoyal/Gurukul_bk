# Payroll — Salary Structures & Runs — Test Plan

## Manual test checklist
- [ ] Create a salary structure for an employee with basic only (allowances/deductions omitted) — confirm it defaults both to 0.
- [ ] Create a second, later-dated salary structure for the same employee — confirm a payroll run processed for a month after the new `effectiveFrom` uses the new structure, and a run for an earlier month uses the old one.
- [ ] Create a payroll run for a month/year; confirm it starts as `DRAFT`.
- [ ] Attempt to create a second run for the same month/year — confirm it is rejected ("Payroll run already exists for this month").
- [ ] Process the run — confirm a `PayrollLine` is created for every `ACTIVE` employee who has a qualifying salary structure, with `gross = basic + allowances` and `net = gross - deductions` matching hand-calculated values.
- [ ] Add an employee with no salary structure at all, then process a run — confirm that employee is silently omitted from the resulting lines (no error, no line).
- [ ] Add an employee whose only salary structure has an `effectiveFrom` date after the run's month — confirm they are also omitted.
- [ ] Attempt to process the same run twice — confirm the second call is rejected ("Only draft runs can be processed").
- [ ] Attempt to pay a `DRAFT` run (skip processing) — confirm it is rejected ("Only processed runs can be paid").
- [ ] Pay a processed run — confirm every line gets a `SalaryPayment` and a `Payslip`, a `FinancialTransaction` is posted per line with `SourceType.SALARY`, and the run becomes `PAID`.
- [ ] Fetch a payslip (`GET /api/v1/payroll/lines/{id}/payslip`) before paying — confirm it fails or 404s (no `Payslip` row exists yet).
- [ ] Fetch salary history for an employee spanning multiple runs/months — confirm every line appears, tagged with its own run's status.
- [ ] Permission-boundary: call every endpoint in this feature (`salary-structures`, `payroll/runs/*`, `employees/*/salary-history`) with a TEACHER, STUDENT, or PARENT token, and with no token at all — confirm current behavior (all succeed, since there is no role/auth check), and flag this explicitly as a gap rather than a pass/fail expectation.
- [ ] Edge case: an employee with two salary structures effective on the exact same date — confirm which one "wins" (ordering is by `effectiveFrom` descending only; ties are broken by whatever order the query returns, which is not explicitly deterministic).

## Existing automated test coverage
- `D:\Gurukul\Gurukul_bk\src\test\java\com\gurukul\payroll\PayrollIntegrationTest.java` — `payrollRunProcessAndPay()`: creates an employee, gives them a salary structure (`basic=30000, allowances=5000, deductions=2000`), creates a payroll run for a month/year, processes it and asserts status `PROCESSED`, fetches the run's lines and asserts the computed `net` (`33000.00`), then pays the run and asserts status `PAID`. This is the only test file for this feature and covers the full happy-path lifecycle end to end for a single employee/single run; it does not test duplicate-run rejection, invalid state transitions (pay-before-process, process-twice), multiple salary structures per employee, employees excluded from a run, or any permission boundary.

## Suggested additional test coverage
- Duplicate payroll run rejection for the same `(school, month, year)`.
- Invalid state transitions: paying a `DRAFT` run, processing an already-`PROCESSED` or `PAID` run.
- Multiple salary structures per employee with different `effectiveFrom` dates, verifying the correct one is picked per run month, including a boundary case where `effectiveFrom` is exactly the 1st of the run's month.
- An employee with no qualifying salary structure being excluded from a processed run, and confirming this is visible/discoverable somewhere (currently it is not).
- Paying a run twice (calling `/pay` again after it's already `PAID`) — confirm the `status != PROCESSED` guard actually blocks this (not explicitly covered today).
- Cross-school isolation: a payroll run, salary structure, or salary-history lookup for one school should not be visible/actionable via another school's `X-School-Id`.
- Role/authorization tests analogous to `FeePaymentServiceOwnershipTest` — since none currently exist for payroll, add tests documenting (and eventually enforcing) that only ADMIN should manage salary structures and payroll runs.
- A test asserting `PayrollLine`/`SalaryStructure` never contain or compute any statutory deduction component, to guard against silently drifting into partial/incorrect statutory support without a deliberate design decision.
