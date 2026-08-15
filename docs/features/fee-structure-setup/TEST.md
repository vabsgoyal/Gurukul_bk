# Fee Structure Setup & Assessment Generation — Test Plan

## Manual test checklist

### Happy path
- [ ] As admin, create a fee category with a unique `code` (e.g. `TUITION`) and `name`; verify it appears in `FeeCategoriesListScreen`.
- [ ] Create a fee structure for a class-section + academic year with two or more lines (different categories, positive amounts); verify `FeeStructureFormScreen` blocks submit until class-section, academic year, and all lines (category + amount > 0) are filled.
- [ ] Open the created structure in `FeeStructureDetailScreen`; verify the displayed per-line amounts and the computed total match what was entered.
- [ ] Tap "Generate Assessments" with active students in the class-section; verify the response count equals the number of `ACTIVE` students in that section, and each returned assessment has `status = UNPAID`, `totalPaid = 0`, `totalDue` equal to the sum of the structure's line amounts, and `dueDate` roughly one month out.
- [ ] Re-tap "Generate Assessments" a second time with no roster changes; verify the count and the returned assessments are unchanged (no duplicates, no error) — validates idempotency via the `(school_id, student_id, academic_year)` uniqueness constraint.
- [ ] Enroll a brand-new student into a class-section that already has a fee structure for the current academic year; verify an assessment is auto-created for that student without anyone touching the "Generate Assessments" button (via `StudentService.createEntity` → `createAssessmentForStudentIfStructureExists`).

### Edge cases
- [ ] Attempt to create a fee category with a `code` that already exists for the school; expect a 400 with `"Fee category code already exists"`.
- [ ] Attempt to create a fee structure for a `(classSectionId, academicYear)` pair that already has one; expect a 400 with `"Fee structure already exists for this class-section and academic year"`.
- [ ] Attempt to create a fee structure line referencing a `feeCategoryId` from a different school (or a nonexistent id); expect a 404 (`EntityNotFoundException("Fee category not found")`).
- [ ] Attempt to create a fee structure line with `amount = 0` or negative; expect a 400 validation error (`@DecimalMin("0.01")`).
- [ ] Attempt to submit a fee structure with an empty `lines` array; expect a 400 validation error (`@NotEmpty`).
- [ ] Generate assessments for a class-section containing a mix of `ACTIVE`, `ALUMNI`, and `WITHDRAWN` students; verify only `ACTIVE` students receive/have assessments in the response.
- [ ] Call `generate-assessments` on a structure id that doesn't belong to the caller's school (or doesn't exist); expect a 404 (`EntityNotFoundException("Fee structure not found")`).
- [ ] Call any of the four endpoints without an `X-School-Id` header; expect a `MissingSchoolIdException`-driven error response.
- [ ] Call `GET /api/v1/fee-structures/{id}`, `POST /api/v1/fee-categories`, `POST /api/v1/fee-structures`, and `POST /api/v1/fee-structures/{id}/generate-assessments` with **no `Authorization` header at all** (or with a STUDENT/PARENT/TEACHER token) — under the current code, expect all of them to succeed (200), since `SecurityConfig` has no matcher restricting these paths and they fall through to `permitAll()`. This is worth an explicit regression test precisely because it looks like it should be admin-only but isn't enforced anywhere.

### Permission-boundary steps (documenting current behavior, not desired behavior)
- [ ] Verify (and flag if this ever changes silently) that a TEACHER-role token can create fee categories/structures and trigger `generate-assessments` for any class-section in the school, not just their own.
- [ ] Verify that a STUDENT- or PARENT-role token can do the same, since nothing in the code path checks `AuthPrincipal.getRole()` for these four endpoints (contrast with `FeePaymentService`, which does check `Role.PARENT`/`Role.STUDENT`/`Role.TEACHER`/`Role.ADMIN` for payment-related operations).

## Existing automated test coverage
No dedicated `FeeCategory`/`FeeStructure`/assessment-generation test class exists. `Glob` for `**/fees/*Test.java` and `**/*Fee*Test.java` under `D:\Gurukul\Gurukul_bk\src\test` returns only:
- `D:\Gurukul\Gurukul_bk\src\test\java\com\gurukul\fees\FeePaymentIntegrationTest.java` — exercises fee **payment** flows end-to-end, but as test setup it does call `POST /api/v1/fee-categories`, `POST /api/v1/fee-structures`, and `POST /api/v1/fee-structures/{id}/generate-assessments` (with only `X-School-Id`, no `Authorization` header, and asserting `status().isOk()`), then enrolls a student and re-runs `generate-assessments` to assert `data.length() == 1`, `data[0].status == "UNPAID"`, `data[0].totalDue == 10000.00`. This incidentally covers: category creation, structure creation, generation with zero pre-existing students, and generation after a student is enrolled — but it is not a dedicated/exhaustive test of this feature's own edge cases (duplicate codes, duplicate structures, non-ACTIVE students, re-generation idempotency, invalid amounts, cross-school ids).
- `D:\Gurukul\Gurukul_bk\src\test\java\com\gurukul\fees\PaymentAttemptIntegrationTest.java` — similarly uses fee-category/fee-structure/generate-assessments creation purely as setup scaffolding to get to a `StudentFeeAssessment` id, in order to test payment-attempt/gateway logic. No unauthenticated-header assertions or negative-path assertions against these three setup endpoints themselves.
- `D:\Gurukul\Gurukul_bk\src\test\java\com\gurukul\fees\ClassSectionFeeStatusIntegrationTest.java` — tests the separate `/api/v1/class-sections/{id}/fee-status` endpoint (class-teacher visibility), not fee-structure setup or assessment generation.
- `D:\Gurukul\Gurukul_bk\src\test\java\com\gurukul\fees\FeePaymentServiceOwnershipTest.java` — unit-tests ownership checks inside `FeePaymentService` (parent/student/teacher access to payment records), unrelated to fee-category/fee-structure setup.

No test file asserts on: duplicate fee-category code rejection, duplicate fee-structure rejection, invalid/zero amount rejection, non-ACTIVE student exclusion from generation, idempotent re-generation (calling `generate-assessments` twice and asserting no duplicate row / unchanged result), cross-school 404 isolation for fee categories/structures, or the missing-authorization behavior described above.

## Suggested additional test coverage
- A dedicated `FeeCategoryIntegrationTest` / unit test asserting duplicate `code` per school is rejected with 400, and that codes are scoped per school (same code allowed in two different schools).
- A dedicated `FeeStructureIntegrationTest` covering: duplicate `(classSectionId, academicYear)` rejection; line validation (`amount <= 0` rejected, empty `lines` rejected, `feeCategoryId` from another school rejected with 404); `GET /{id}` 404 for cross-school ids.
- An assessment-generation test that seeds a class-section with a mix of `ACTIVE`, `ALUMNI`, and `WITHDRAWN` students and asserts only `ACTIVE` students appear in the response.
- An idempotency test that calls `generate-assessments` twice in a row and asserts the second call returns the same assessment ids/values as the first (no duplicate rows, `totalDue` unchanged even if a line amount is hypothetically altered between calls).
- A test explicitly calling all four endpoints with no `Authorization` header and with a STUDENT/PARENT-role token, documenting the current fully-open authorization posture — ideally paired with a fix once/if these endpoints are restricted to ADMIN (mirroring the role checks already present in `FeePaymentService`), after which this test should be updated to assert 401/403 instead of 200.
- A test for the auto-provisioning hook (`createAssessmentForStudentIfStructureExists`): enroll a student into a class-section that already has both a fee structure and an existing assessment for another student, and confirm the new student gets their own distinct assessment with the correct `totalDue`.
- A regression test for the "student changes section mid-year" edge case noted in FLOW.md: create two structures for two different class-sections in the same academic year, move a student from section A (with an existing assessment) to section B, and confirm whether/how billing behaves (currently the unique `(school_id, student_id, academic_year)` constraint would prevent a second assessment from being created for section B).
