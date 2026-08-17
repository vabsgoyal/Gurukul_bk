# Fee Structure Setup & Assessment Generation — Flow

## Overview
This feature lets a school configure a catalog of fee categories (e.g. "TUITION", "TRANSPORT"), attach per-category amounts to a fee structure scoped to one class-section and academic year, and then generate a `StudentFeeAssessment` (a per-student bill) for every active student in that class-section. Assessment generation is idempotent per student/academic-year and is also triggered automatically when a new student is enrolled into a class-section that already has a structure. This is the setup stage that upstream feeds the payment/collection flow (a separate feature).

## Actors / roles
- **ADMIN**: In practice the only role exercised by the frontend for this feature (screens live under `src/screens/principal/`, wired into `PrincipalNavigator`). Nothing in the backend code restricts these endpoints to ADMIN specifically — see Backend endpoints below.
- **TEACHER**: Not given any screen for this feature and not mentioned in any authorization rule for these endpoints. A teacher's JWT would still satisfy the URL matcher because there isn't a role-specific matcher at all (see below).
- **STUDENT**: No screen; not referenced by any check in `FeeCategoryService`/`FeeStructureService`.
- **PARENT**: No screen; not referenced by any check in `FeeCategoryService`/`FeeStructureService`.

## Flow diagram

```mermaid
flowchart TD
  A[Admin opens Fees Hub] --> B[Fee Categories screen]
  A --> C[Fee Structures screen]
  B --> B1[Create Fee Category\nPOST /api/v1/fee-categories]
  B1 --> B2{code already exists\nfor this school?}
  B2 -- yes --> B3[400 IllegalArgumentException]
  B2 -- no --> B4[Category saved]

  C --> C1[Tap Add Structure]
  C1 --> D[Fee Structure Form:\npick class-section, academic year,\nadd one or more category+amount lines]
  D --> D1[POST /api/v1/fee-structures]
  D1 --> D2{structure already exists\nfor class-section + academic year?}
  D2 -- yes --> D3[400 IllegalArgumentException]
  D2 -- no --> D4[FeeStructure + FeeStructureLine rows saved]
  D4 --> E[Fee Structure Detail screen]

  E --> F[Tap Generate Assessments]
  F --> G[POST /api/v1/fee-structures/{id}/generate-assessments]
  G --> G1[Sum all FeeStructureLine amounts = totalDue]
  G1 --> G2[Load all students in structure's class-section]
  G2 --> G3{student.status == ACTIVE?}
  G3 -- no --> G4[Skipped - no assessment created]
  G3 -- yes --> G5{assessment already exists\nfor school+student+academicYear?}
  G5 -- yes --> G6[Existing assessment returned unchanged\nno duplicate, no amount update]
  G5 -- no --> G7[New StudentFeeAssessment created:\nstatus=UNPAID, totalPaid=0,\ndueDate=today+1 month]
  G6 --> H[Response: list of assessments\nfor every active student]
  G7 --> H

  I[Separately: a new student is enrolled\ninto a class-section] --> I1[StudentService.createEntity]
  I1 --> I2{FeeStructure exists for\nthat class-section + academic year?}
  I2 -- no --> I3[No assessment created]
  I2 -- yes --> G5
```

## Step-by-step
1. An admin opens the Fees Hub (`FeesHubScreen`) and navigates to Fee Categories to define the catalog (`FeeCategoriesListScreen` → `createFeeCategory` → `POST /api/v1/fee-categories`). Each category has a `code` (unique per school) and a `name`. Duplicate codes are rejected with `IllegalArgumentException("Fee category code already exists")`.
2. The admin navigates to Fee Structures (`FeeStructuresListScreen`) and taps "Add Structure", landing on `FeeStructureFormScreen`. They pick a class-section (`ClassSectionPicker`), an academic year, and one or more `{feeCategoryId, amount}` lines (`FeeCategoryPicker` for category selection; amount must be `> 0`, enforced client-side by `isPositiveNumber` and server-side by `@DecimalMin("0.01")`).
3. Submitting calls `createFeeStructure` → `POST /api/v1/fee-structures`. The server resolves the class-section (schema-scoped via `ClassSectionService.getScopedClassSection`), rejects if a structure already exists for that `(schoolId, classSectionId, academicYear)` tuple (`IllegalArgumentException("Fee structure already exists for this class-section and academic year")`), otherwise persists the `FeeStructure` and one `FeeStructureLine` row per line item, each resolving its `FeeCategory` via `FeeCategoryService.getScopedEntity` (school-scoped lookup, 404 if not found/not owned by this school).
4. On success the app navigates to `FeeStructureDetailScreen`, which lists the fee lines, shows a computed total-per-student (client-side sum of `line.amount`), and exposes a "Generate Assessments" button.
5. Tapping "Generate Assessments" calls `generateAssessments` → `POST /api/v1/fee-structures/{id}/generate-assessments`. The server:
   - Loads the structure's lines and sums their amounts into `totalDue`.
   - Loads every `Student` row for `(schoolId, classSectionId)` — **not** filtered by academic year at the query level.
   - Filters to `student.getStatus() == StudentStatus.ACTIVE` in Java (in-memory), silently skipping `ALUMNI`/`WITHDRAWN` students.
   - For each active student, calls `createOrSkipAssessment`: looks up an existing `StudentFeeAssessment` by `(schoolId, studentId, academicYear)`; if found, returns it as-is (no update to `totalDue`, status, or due date even if the structure's lines changed since); if not found, creates a new one with `totalDue` = the freshly computed sum, `totalPaid = 0`, `status = UNPAID`, `dueDate = LocalDate.now().plusMonths(1)`.
   - Returns the full set of assessments (existing + newly created) for every active student in the section, mapped through `FeeAssessmentResponse` (which also derives `remainingDue = totalDue - totalPaid`).
6. Independently of the "Generate Assessments" button, when a brand-new student is admitted (`StudentService.createEntity`), `FeeStructureService.createAssessmentForStudentIfStructureExists(student)` runs automatically: it looks up a `FeeStructure` for that student's `(schoolId, classSectionId, classSection.academicYear)`; if one exists, it computes `totalDue` from that structure's current lines and calls the same `createOrSkipAssessment` logic, so the new student gets billed without anyone re-running the bulk generator.

## Backend endpoints involved
- `GET /api/v1/fee-categories` (controller: `FeeCategoryController.list`) — lists categories for the caller's school (`X-School-Id`). **Authorization**: no role-specific matcher exists for this path in `SecurityConfig`; it falls through to the catch-all `.anyRequest().permitAll()`. No `@PreAuthorize` or manual role check exists in `FeeCategoryService`. Effectively open to any caller that supplies a valid `X-School-Id` header — authentication is not enforced by Spring Security for this path.
- `POST /api/v1/fee-categories` (controller: `FeeCategoryController.create`) — same as above: no matcher, falls to `permitAll()`, no service-layer check.
- `GET /api/v1/fee-structures` (controller: `FeeStructureController.list`) — same: no matcher for `/api/v1/fee-structures/**` anywhere in `SecurityConfig`; falls to `permitAll()`.
- `GET /api/v1/fee-structures/{id}` (controller: `FeeStructureController.getById`) — same, `permitAll()`.
- `POST /api/v1/fee-structures` (controller: `FeeStructureController.create`) — same, `permitAll()`.
- `POST /api/v1/fee-structures/{id}/generate-assessments` (controller: `FeeStructureController.generateAssessments`) — same, `permitAll()`. This bulk-bills every active student in the class-section, and there is no role or ownership check anywhere in the call chain (`FeeStructureController` → `FeeStructureService.generateAssessments`).
- Confirmed by reading `SecurityConfig.java` in full: it lists explicit `requestMatchers(...)` rules for attendance, staff-attendance, assessments, exam results, grading scale, report cards, class-section fee-status, credentials, chat, and announcements — but **none** for `/api/v1/fee-categories/**` or `/api/v1/fee-structures/**`. The existing backend integration tests (`FeePaymentIntegrationTest`, `PaymentAttemptIntegrationTest`) call these three endpoints with only an `X-School-Id` header and **no `Authorization: Bearer` header at all**, and the calls succeed — corroborating that these endpoints require no authentication today.

## Frontend screens involved
- `FeesHubScreen.tsx` — landing menu linking to Fee Categories, Fee Structures, Fee Assessments, and Fee Payment Settings.
- `FeeCategoriesListScreen.tsx` — lists categories (`listFeeCategories`) and creates new ones (`createFeeCategory`) via an inline form (code + name).
- `FeeStructuresListScreen.tsx` — lists structures (`listFeeStructures`), shows class/section/academic-year and a computed total per row, links to the create form and to structure detail.
- `FeeStructureFormScreen.tsx` — create form: class-section picker, academic-year dropdown, dynamic list of `{feeCategoryId, amount}` lines; calls `createFeeStructure`; validates non-empty lines, chosen category per line, and positive numeric amount before enabling submit.
- `FeeStructureDetailScreen.tsx` — confirmed to be where assessment generation is triggered: shows fee lines and total, and a "Generate Assessments" button that calls `generateAssessments(schoolId, feeStructure.id)`, displaying the resulting count on success or a toast on error.

Frontend API client functions (in `src/api/feeCategories.ts` and `src/api/feeStructures.ts`):
- `listFeeCategories(schoolId)` → `GET /api/v1/fee-categories`
- `createFeeCategory(schoolId, req)` → `POST /api/v1/fee-categories`
- `listFeeStructures(schoolId)` → `GET /api/v1/fee-structures`
- `getFeeStructure(schoolId, id)` → `GET /api/v1/fee-structures/{id}`
- `createFeeStructure(schoolId, req)` → `POST /api/v1/fee-structures`
- `generateAssessments(schoolId, id)` → `POST /api/v1/fee-structures/{id}/generate-assessments`

## Data model
- **FeeCategory**: `code` (unique per `school_id`), `name`.
- **FeeStructure**: `classSection` (many-to-one), `academicYear`; unique on `(school_id, class_section_id, academic_year)`.
- **FeeStructureLine**: belongs to a `FeeStructure`, references a `FeeCategory`, has `amount` (`BigDecimal`, precision 12 scale 2); unique on `(fee_structure_id, fee_category_id)` — a category can only appear once per structure.
- **StudentFeeAssessment**: belongs to a `Student`, `academicYear`, `totalDue`, `totalPaid` (defaults to 0), `status` (`FeeAssessmentStatus`: `UNPAID`, `PARTIAL`, `PAID`, `OVERDUE`), `dueDate`; unique on `(school_id, student_id, academic_year)` — one assessment per student per academic year, regardless of how many fee categories/lines feed into it.
- `FeeAssessmentResponse.remainingDue` is derived (`totalDue - totalPaid`), not stored.

## Known limitations / edge cases
- **No authorization on any of these endpoints.** `SecurityConfig` has zero role-based `requestMatchers` for `/api/v1/fee-categories/**` or `/api/v1/fee-structures/**`; they all fall to the trailing `.anyRequest().permitAll()`. Combined with the absence of any manual role check in `FeeCategoryService`/`FeeStructureService`, any caller who can reach the API with a valid `X-School-Id` header — teacher, student, parent, or an unauthenticated client — can create fee categories, create fee structures, and trigger bulk assessment generation for an entire class-section. This is a real, currently-unmitigated gap in a money-affecting feature.
- **Structures and lines are immutable once created.** There is no `PUT`/`PATCH`/`DELETE` endpoint for `FeeStructure`, `FeeStructureLine`, or `FeeCategory`. If a fee amount was entered wrong, the only stated path is creating a new structure — but the `(school, class-section, academic-year)` uniqueness constraint blocks that until the erroneous one is removed at the database level (no such removal path exists via the API).
- **Regenerating assessments does not re-sync amounts.** `createOrSkipAssessment` only sets `totalDue` when creating a brand-new assessment; if an assessment already exists for a student/year and the admin somehow changes the underlying fee lines (not possible via API today, but relevant if done via direct DB edit or future edit endpoint), re-running "Generate Assessments" will not update `totalDue` on existing rows.
- **Assessment is keyed by academic year only, not by class-section.** If a student changes class-section mid-year within the same academic year, `(school_id, student_id, academic_year)` uniqueness means only one assessment can ever exist for them that year — a second `generateAssessments` run for their new section's structure will just return their existing (old) assessment unchanged, silently not billing the new section's fee lines.
- **`generateAssessments` loads all students in the class-section, not scoped to academic year.** `studentRepository.findAllBySchoolIdAndClassSectionId` has no academic-year filter, so it relies entirely on the `ACTIVE` status filter and the class-section's own current roster; a class-section reused across years (if that occurs) could pull in students who don't belong to the structure's stated `academicYear`.
- **Due date is always "today + 1 month."** `dueDate = LocalDate.now().plusMonths(1)` — there is no way to set a custom due date per structure, per category, or per generation run; it also means running "Generate Assessments" on different days for different (new) students in the same structure gives them different due dates.
- **Students who join after generation are still covered**, but only through the separate auto-creation hook in `StudentService.createEntity` → `FeeStructureService.createAssessmentForStudentIfStructureExists`, not through the "Generate Assessments" button itself. If a student's class-section is changed later via `updateClassSection`, this code path shows no equivalent call to (re-)create an assessment for the new section (not verified beyond the excerpt read, but no such call was found near `createEntity`).
- **Idempotent by design at the per-student level**: re-clicking "Generate Assessments" multiple times is safe — it will not create duplicate `StudentFeeAssessment` rows for the same student/year, matching the DB unique constraint.
