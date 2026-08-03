-- Same bug class as V33/V34, remaining instances found by a full audit of every
-- @Enumerated(EnumType.STRING) field in the codebase against V25's seed data.

-- financial_transaction.direction: TransactionDirection is INFLOW|OUTFLOW. Every single row V25
-- ever inserted used 'CREDIT'/'DEBIT' instead - this affects 100% of financial_transaction rows,
-- the most severe of the bugs found (any "list transactions" read crashes immediately).
UPDATE financial_transaction SET direction = 'INFLOW' WHERE direction = 'CREDIT';
UPDATE financial_transaction SET direction = 'OUTFLOW' WHERE direction = 'DEBIT';

-- financial_transaction.source_type: SourceType is
-- FEE_PAYMENT|EVENT_COLLECTION|SPONSORSHIP|SALARY|VENDOR_PAYMENT|MANUAL. FEE_PAYMENT and
-- EVENT_COLLECTION were already correct; these four weren't.
UPDATE financial_transaction SET source_type = 'SALARY' WHERE source_type = 'SALARY_PAYMENT';
UPDATE financial_transaction SET source_type = 'SPONSORSHIP' WHERE source_type = 'SPONSORSHIP_PAYMENT';
UPDATE financial_transaction SET source_type = 'VENDOR_PAYMENT' WHERE source_type IN ('INFRA_VENDOR_PAYMENT', 'EVENT_VENDOR_PAYMENT');

-- sponsorship.purpose: SponsorshipPurpose is SPORTS|LIBRARY|ANNUAL_DAY|GENERAL - neither seeded
-- value ('SCHOLARSHIP'/'INFRASTRUCTURE') matches; GENERAL is the closest available fit.
UPDATE sponsorship SET purpose = 'GENERAL' WHERE purpose IN ('SCHOLARSHIP', 'INFRASTRUCTURE');

-- sponsorship.status: SponsorshipStatus is PLEDGED|PARTIAL|RECEIVED, not 'ACTIVE'. Every seeded
-- sponsorship has exactly one payment for half its pledged_amount ("First installment" in V25),
-- so PARTIAL is the accurate status, not a guess.
UPDATE sponsorship SET status = 'PARTIAL' WHERE status = 'ACTIVE';

-- assessment.type: AssessmentType is ASSIGNMENT|QUIZ|TEST|EXAM. V25's exam-cycle labels/marks
-- make the right mapping clear: "Unit Test 1" (25 marks) -> TEST; "Mid Term" (80 marks) and
-- "Final Exam" (100 marks) are both substantial term exams -> EXAM.
UPDATE assessment SET type = 'TEST' WHERE type = 'UNIT_TEST';
UPDATE assessment SET type = 'EXAM' WHERE type IN ('MID_TERM', 'FINAL');

-- receipt_sequence.sequence_type: ReceiptSequenceType is only RCPT|PAYSLIP - a running counter
-- per (school, type, academic_year), not "one counter per revenue category" as V25 modeled it
-- (FEE/EVENT/SPONSORSHIP/PAYROLL). Unlike every other fix above, these rows are dead data rather
-- than an active crash: DocumentNumberGenerator.nextReceiptNumber only ever queries by the real
-- enum value and self-heals (creates a fresh lastValue=0 row) when none exists, so it's simplest
-- and safest to delete rather than remap them (remapping risks colliding with a real RCPT row for
-- the same academic_year under the table's own unique constraint).
DELETE FROM receipt_sequence WHERE sequence_type IN ('FEE', 'EVENT', 'SPONSORSHIP', 'PAYROLL');
