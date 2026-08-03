-- V25__seed_jnv_full_dataset.sql used status string literals that don't match the actual Java
-- enums backing these columns (Hibernate's @Enumerated(EnumType.STRING) conversion only ever
-- rejects this at READ time, not at INSERT time, since these rows were written by raw SQL, not
-- through the app - so the bad values sat undetected until something tried to read them back,
-- surfacing as "No enum constant ..." IllegalArgumentException / 500s on Payroll, Fees,
-- Infrastructure Expenses, Event Expenses, and the shared approval-workflow screens).
--
-- Scoped by value, not by school_id: every app-written row is guaranteed to already hold a valid
-- enum string (Hibernate would refuse to persist otherwise), so these UPDATEs can only ever touch
-- the raw-SQL-inserted JNV seed rows - nothing else matches these now-invalid literals.

-- student_fee_assessment.status: FeeAssessmentStatus is UNPAID|PARTIAL|PAID|OVERDUE.
UPDATE student_fee_assessment SET status = 'UNPAID' WHERE status = 'PENDING';
UPDATE student_fee_assessment SET status = 'PARTIAL' WHERE status = 'PARTIALLY_PAID';

-- payroll_run.status: PayrollRunStatus is DRAFT|PROCESSED|PAID.
UPDATE payroll_run SET status = 'DRAFT' WHERE status = 'PENDING';

-- school_event.status: EventStatus is DRAFT|ACTIVE|CLOSED.
UPDATE school_event SET status = 'CLOSED' WHERE status = 'COMPLETED';
UPDATE school_event SET status = 'DRAFT' WHERE status = 'PLANNED';

-- approval_request.status / approval_history.from_status/to_status use the separate, coarser
-- ApprovalStatus enum (DRAFT|SUBMITTED|APPROVED|REJECTED) - fix these BEFORE remapping
-- infra_expense_request/event_expense_request below to their own richer terminal values
-- (PURCHASED/PAID), which ApprovalStatus has no equivalent for.
UPDATE approval_history SET to_status = 'SUBMITTED' WHERE to_status = 'PENDING';
UPDATE approval_history SET from_status = 'SUBMITTED' WHERE from_status = 'PENDING';
UPDATE approval_history SET to_status = 'APPROVED' WHERE to_status = 'COMPLETED';
UPDATE approval_request SET status = 'SUBMITTED' WHERE status = 'PENDING';
UPDATE approval_request SET status = 'APPROVED' WHERE status = 'COMPLETED';

-- infra_expense_request.status: InfraExpenseStatus is
-- DRAFT|SUBMITTED|APPROVED|REJECTED|PURCHASED|PAID. Rows already have a matching
-- infra_purchase_record + infra_vendor_payment, so PAID (not just PURCHASED) is the accurate
-- terminal state.
UPDATE infra_expense_request SET status = 'SUBMITTED' WHERE status = 'PENDING';
UPDATE infra_expense_request SET status = 'PAID' WHERE status = 'COMPLETED';

-- event_expense_request.status: EventExpenseStatus is DRAFT|SUBMITTED|APPROVED|REJECTED|PAID.
UPDATE event_expense_request SET status = 'SUBMITTED' WHERE status = 'PENDING';
