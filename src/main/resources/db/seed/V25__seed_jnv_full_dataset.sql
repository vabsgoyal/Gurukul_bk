-- Full synthetic dataset for a new school "JNV", covering every module (academics, attendance,
-- exams, fees, finance, payroll, events, expenses, sponsorships, workflow approvals, chat,
-- announcements, credentials) so the whole feature set can be exercised against one tenant.
-- Idempotent: every insert is gated on jnv_seed_guard.should_seed, which is false if a school
-- with jnv_school_id already exists (e.g. this migration was already applied and is being replayed
-- by a repair). All generated child rows use gen_random_uuid(); only the school id is a fixed
-- literal so it can be found again (e.g. gurukul.dev.school-id style lookups).

CREATE TEMP TABLE jnv_seed_guard AS
SELECT NOT EXISTS (
    SELECT 1 FROM school WHERE id = '99999999-9999-9999-9999-999999999999'
) AS should_seed;

INSERT INTO school (id, name, address, city, state, pincode, contact_email, contact_phone, principal_name, director_name, created_at, updated_at)
SELECT
    '99999999-9999-9999-9999-999999999999',
    'JNV',
    'Navodaya Vidyalaya Campus, NH-8',
    'Udaipur',
    'Rajasthan',
    '313001',
    'admin@jnv.demo',
    '9820012345',
    'Dr. Anjali Verma',
    'Mr. Suresh Nair',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE (SELECT should_seed FROM jnv_seed_guard);

-- ===========================================================================================
-- PART 1: class sections (7 grades x 3 sections, single academic year)
-- ===========================================================================================
CREATE TEMP TABLE t_class_section (
    id UUID DEFAULT gen_random_uuid(),
    grade TEXT,
    section TEXT,
    academic_year TEXT,
    class_teacher_id UUID
);

INSERT INTO t_class_section (grade, section, academic_year)
SELECT grade, section, '2025-26'
FROM (SELECT unnest(ARRAY['6','7','8','9','10','11','12']) AS grade) g
CROSS JOIN (SELECT unnest(ARRAY['A','B','C']) AS section) s
WHERE (SELECT should_seed FROM jnv_seed_guard);

-- ===========================================================================================
-- PART 2: subjects
-- ===========================================================================================
CREATE TEMP TABLE t_subject (
    id UUID DEFAULT gen_random_uuid(),
    code TEXT,
    name TEXT,
    core BOOLEAN
);

INSERT INTO t_subject (code, name, core) VALUES
    ('HIN', 'Hindi', TRUE),
    ('ENG', 'English', TRUE),
    ('MAT', 'Mathematics', TRUE),
    ('SCI', 'Science', TRUE),
    ('SST', 'Social Science', TRUE),
    ('SAN', 'Sanskrit', FALSE),
    ('CS', 'Computer Science', FALSE),
    ('PE', 'Physical Education', FALSE);

-- ===========================================================================================
-- PART 3: employees (21 class teachers, 10 specialists, 15 non-teaching)
-- ===========================================================================================
CREATE TEMP TABLE t_employee (
    id UUID DEFAULT gen_random_uuid(),
    rn BIGINT,
    name TEXT,
    designation TEXT,
    employee_type TEXT,
    role_tag TEXT,
    join_date DATE,
    contact_phone TEXT,
    contact_email TEXT,
    experience_years INT,
    experience_months INT,
    rating NUMERIC(3,2),
    basic NUMERIC(12,2)
);

CREATE TEMP TABLE t_names (
    idx BIGINT GENERATED ALWAYS AS IDENTITY,
    first_name TEXT,
    last_name TEXT
);
INSERT INTO t_names (first_name, last_name)
SELECT fn, ln
FROM unnest(ARRAY['Aarav','Vivaan','Aditya','Vihaan','Arjun','Sai','Reyansh','Krishna','Ishaan','Rohan',
                   'Priya','Ananya','Diya','Saanvi','Aadhya','Kavya','Myra','Anika','Isha','Neha',
                   'Rahul','Amit','Vikram','Sanjay','Rajesh','Deepak','Manoj','Ashok','Ramesh','Suresh',
                   'Pooja','Sunita','Kiran','Meena','Geeta','Rekha','Shalini','Nisha','Swati','Vandana']) WITH ORDINALITY AS a(fn, i1)
CROSS JOIN LATERAL (
    SELECT unnest(ARRAY['Sharma','Verma','Gupta','Singh','Kumar','Yadav','Mishra','Pandey','Reddy','Nair',
                         'Iyer','Chauhan','Rathore','Joshi','Bhatt','Menon','Das','Ghosh','Patel','Shah']) AS ln
) b
LIMIT 40;

INSERT INTO t_employee (rn, name, designation, employee_type, role_tag, join_date, contact_phone, contact_email, experience_years, experience_months, rating, basic)
SELECT
    rn,
    n.first_name || ' ' || n.last_name,
    CASE
        WHEN rn <= 21 THEN 'Teacher (Class Teacher)'
        WHEN rn <= 31 THEN 'Subject Teacher'
        WHEN rn = 32 THEN 'Vice Principal'
        WHEN rn = 33 THEN 'Accountant'
        WHEN rn = 34 THEN 'Accountant'
        WHEN rn = 35 THEN 'Librarian'
        WHEN rn <= 37 THEN 'Lab Assistant'
        WHEN rn = 38 THEN 'Sports Coach'
        WHEN rn <= 41 THEN 'Peon'
        WHEN rn <= 43 THEN 'Security Guard'
        WHEN rn = 44 THEN 'School Nurse'
        WHEN rn = 45 THEN 'IT Administrator'
        ELSE 'HR Manager'
    END,
    CASE WHEN rn <= 31 THEN 'TEACHING' ELSE 'NON_TEACHING' END,
    CASE WHEN rn <= 21 THEN 'CLASS_TEACHER' WHEN rn <= 31 THEN 'SPECIALIST' ELSE 'NON_TEACHING' END,
    CURRENT_DATE - ((365 * (1 + (rn % 15))) || ' days')::interval,
    '98' || lpad((100000 + rn)::text, 6, '0'),
    lower(replace(n.first_name || '.' || n.last_name, ' ', '')) || '@jnv.demo',
    1 + (rn % 15),
    rn % 12,
    round((3.0 + (rn % 20) * 0.1)::numeric, 2),
    CASE
        WHEN rn <= 31 THEN 35000 + (rn % 10) * 1500
        WHEN rn IN (32) THEN 55000
        WHEN rn IN (33,34) THEN 32000
        ELSE 18000 + (rn % 6) * 1000
    END
FROM generate_series(1, 46) AS rn
JOIN t_names n ON n.idx = 1 + ((rn - 1) % 40)
WHERE (SELECT should_seed FROM jnv_seed_guard);

INSERT INTO employee (id, school_id, name, designation, join_date, bank_account, contact_phone, status, created_at, updated_at, academic_background, experience_years, experience_months, rating, contact_email, employee_type)
SELECT
    id, '99999999-9999-9999-9999-999999999999', name, designation, join_date,
    'JNVBANK' || lpad(rn::text, 8, '0'), contact_phone, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
    'B.Ed, M.A.', experience_years, experience_months, rating, contact_email, employee_type
FROM t_employee
WHERE (SELECT should_seed FROM jnv_seed_guard);

-- Assign one distinct class teacher per section (1:1, satisfies uq_class_section_teacher).
WITH ordered_sections AS (
    SELECT id, row_number() OVER (ORDER BY id) AS rn FROM t_class_section
),
ordered_teachers AS (
    SELECT id, row_number() OVER (ORDER BY id) AS rn FROM t_employee WHERE role_tag = 'CLASS_TEACHER'
)
UPDATE t_class_section cs
SET class_teacher_id = ot.id
FROM ordered_sections os
JOIN ordered_teachers ot ON ot.rn = os.rn
WHERE cs.id = os.id;

INSERT INTO class_section (id, school_id, class_name, section, academic_year, created_at, updated_at, class_teacher_id)
SELECT id, '99999999-9999-9999-9999-999999999999', 'Grade ' || grade, section, academic_year, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, class_teacher_id
FROM t_class_section
WHERE (SELECT should_seed FROM jnv_seed_guard);

INSERT INTO subject (id, school_id, code, name, description, created_at, updated_at)
SELECT id, '99999999-9999-9999-9999-999999999999', code, name, name || ' curriculum', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM t_subject
WHERE (SELECT should_seed FROM jnv_seed_guard);

-- Core subjects taught by the section's own class teacher; specialist subjects round-robin
-- across the specialist teacher pool.
CREATE TEMP TABLE t_section_subject_teacher (
    id UUID DEFAULT gen_random_uuid(),
    section_id UUID,
    subject_id UUID,
    teacher_id UUID
);

INSERT INTO t_section_subject_teacher (section_id, subject_id, teacher_id)
SELECT cs.id, sub.id, cs.class_teacher_id
FROM t_class_section cs
CROSS JOIN t_subject sub
WHERE sub.core;

WITH specialists AS (
    SELECT id, row_number() OVER (ORDER BY id) - 1 AS rn FROM t_employee WHERE role_tag = 'SPECIALIST'
),
targets AS (
    SELECT cs.id AS section_id, sub.id AS subject_id, row_number() OVER (ORDER BY cs.id, sub.id) - 1 AS rn
    FROM t_class_section cs CROSS JOIN t_subject sub WHERE NOT sub.core
)
INSERT INTO t_section_subject_teacher (section_id, subject_id, teacher_id)
SELECT t.section_id, t.subject_id, sp.id
FROM targets t
JOIN specialists sp ON sp.rn = t.rn % (SELECT count(*) FROM specialists);

INSERT INTO section_subject_teacher (id, school_id, section_id, subject_id, teacher_id, created_at, updated_at)
SELECT id, '99999999-9999-9999-9999-999999999999', section_id, subject_id, teacher_id, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM t_section_subject_teacher
WHERE (SELECT should_seed FROM jnv_seed_guard);

-- ===========================================================================================
-- PART 4: students (50 per section, ~1050 total)
-- ===========================================================================================
CREATE TEMP TABLE t_student (
    id UUID DEFAULT gen_random_uuid(),
    class_section_id UUID,
    seq_in_section BIGINT,
    global_rn BIGINT,
    name TEXT,
    dob DATE,
    gender TEXT,
    parent_name TEXT,
    parent_contact TEXT
);

WITH sections_numbered AS (
    SELECT id, row_number() OVER (ORDER BY id) AS section_rn FROM t_class_section
),
seats AS (
    SELECT sn.id AS class_section_id, gs AS seq_in_section,
           row_number() OVER (ORDER BY sn.section_rn, gs) AS global_rn
    FROM sections_numbered sn
    CROSS JOIN generate_series(1, 50) AS gs
)
INSERT INTO t_student (class_section_id, seq_in_section, global_rn, name, dob, gender, parent_name, parent_contact)
SELECT
    s.class_section_id, s.seq_in_section, s.global_rn,
    n.first_name || ' ' || n.last_name,
    DATE '2010-01-01' - ((s.global_rn % 2000) || ' days')::interval,
    CASE WHEN s.global_rn % 2 = 0 THEN 'MALE' ELSE 'FEMALE' END,
    pn.first_name || ' ' || n.last_name,
    '97' || lpad((200000 + s.global_rn)::text, 6, '0')
FROM seats s
JOIN t_names n ON n.idx = 1 + ((s.global_rn - 1) % 40)
JOIN t_names pn ON pn.idx = 1 + (s.global_rn % 40);

INSERT INTO student (id, school_id, roll_number, name, dob, gender, address, parent_name, parent_contact, class_section_id, admission_date, status, created_at, updated_at)
SELECT
    id, '99999999-9999-9999-9999-999999999999',
    'JNV-' || lpad(global_rn::text, 5, '0'),
    name, dob, gender,
    'House ' || global_rn || ', JNV Campus Colony, Udaipur, Rajasthan',
    parent_name, parent_contact, class_section_id,
    DATE '2025-04-01', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM t_student
WHERE (SELECT should_seed FROM jnv_seed_guard);

-- ===========================================================================================
-- PART 5: attendance (school days = weekdays Apr 2025 - Mar 2026, ~260 days)
-- ===========================================================================================
CREATE TEMP TABLE t_school_day AS
SELECT d::date AS day
FROM generate_series(DATE '2025-04-01', DATE '2026-03-31', interval '1 day') d
WHERE extract(dow FROM d) NOT IN (0, 6);

INSERT INTO attendance_record (id, school_id, student_id, section_id, attendance_date, status, marked_by_teacher_id, created_at, updated_at)
SELECT
    gen_random_uuid(), '99999999-9999-9999-9999-999999999999', st.id, st.class_section_id, sd.day,
    CASE WHEN (st.global_rn + extract(doy FROM sd.day)::bigint) % 20 = 0 THEN 'ABSENT'
         WHEN (st.global_rn + extract(doy FROM sd.day)::bigint) % 37 = 0 THEN 'LEAVE'
         ELSE 'PRESENT' END,
    cs.class_teacher_id, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM t_student st
JOIN t_class_section cs ON cs.id = st.class_section_id
CROSS JOIN t_school_day sd
WHERE (SELECT should_seed FROM jnv_seed_guard);

INSERT INTO staff_attendance_record (id, school_id, employee_id, attendance_date, status, marked_by_employee_id, created_at, updated_at)
SELECT
    gen_random_uuid(), '99999999-9999-9999-9999-999999999999', e.id, sd.day,
    CASE WHEN (e.rn + extract(doy FROM sd.day)::bigint) % 25 = 0 THEN 'ABSENT'
         WHEN (e.rn + extract(doy FROM sd.day)::bigint) % 40 = 0 THEN 'LEAVE'
         ELSE 'PRESENT' END,
    (SELECT id FROM t_employee WHERE role_tag = 'NON_TEACHING' ORDER BY id LIMIT 1),
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM t_employee e
CROSS JOIN t_school_day sd
WHERE (SELECT should_seed FROM jnv_seed_guard);

-- ===========================================================================================
-- PART 6: assessments (3 exam cycles x subjects taught per section)
-- ===========================================================================================
INSERT INTO assessment (id, school_id, section_id, subject_id, type, title, assessment_date, max_marks, description, created_by_teacher_id, created_at, updated_at)
SELECT
    gen_random_uuid(), '99999999-9999-9999-9999-999999999999', sst.section_id, sst.subject_id,
    cyc.type, cyc.type_label || ' - ' || sub.name, cyc.exam_date, cyc.max_marks,
    sub.name || ' ' || cyc.type_label || ' assessment', sst.teacher_id, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM t_section_subject_teacher sst
JOIN t_subject sub ON sub.id = sst.subject_id
CROSS JOIN (VALUES
    ('UNIT_TEST', 'Unit Test 1', DATE '2025-07-15', 25.00),
    ('MID_TERM', 'Mid Term', DATE '2025-10-10', 80.00),
    ('FINAL', 'Final Exam', DATE '2026-03-05', 100.00)
) AS cyc(type, type_label, exam_date, max_marks)
WHERE (SELECT should_seed FROM jnv_seed_guard);

-- ===========================================================================================
-- PART 7: finance foundation + fees
-- ===========================================================================================
CREATE TEMP TABLE t_fund_account (id UUID DEFAULT gen_random_uuid(), code TEXT, name TEXT, account_type TEXT);
INSERT INTO t_fund_account (code, name, account_type) VALUES
    ('GENERAL', 'General Fund', 'OPERATING'),
    ('PAYROLL', 'Payroll Fund', 'OPERATING'),
    ('EVENTS', 'Events Fund', 'RESTRICTED'),
    ('SPONSORSHIP', 'Sponsorship Fund', 'RESTRICTED');

INSERT INTO fund_account (id, school_id, code, name, account_type, created_at, updated_at)
SELECT id, '99999999-9999-9999-9999-999999999999', code, name, account_type, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM t_fund_account
WHERE (SELECT should_seed FROM jnv_seed_guard);

INSERT INTO receipt_sequence (id, school_id, sequence_type, academic_year, last_value, created_at, updated_at)
SELECT gen_random_uuid(), '99999999-9999-9999-9999-999999999999', seq_type, '2025-26', last_val, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM (VALUES ('FEE', 1650), ('EVENT', 30), ('SPONSORSHIP', 15), ('PAYROLL', 874)) AS v(seq_type, last_val)
WHERE (SELECT should_seed FROM jnv_seed_guard);

CREATE TEMP TABLE t_fee_category (id UUID DEFAULT gen_random_uuid(), code TEXT, name TEXT, amount NUMERIC(12,2));
INSERT INTO t_fee_category (code, name, amount) VALUES
    ('TUITION', 'Tuition Fee', 8000),
    ('TRANSPORT', 'Transport Fee', 1500),
    ('HOSTEL', 'Hostel Fee', 6000),
    ('LIBRARY', 'Library Fee', 300),
    ('LAB', 'Lab Fee', 500),
    ('SPORTS', 'Sports Fee', 400),
    ('EXAM', 'Examination Fee', 600),
    ('MISC', 'Miscellaneous Fee', 250);

INSERT INTO fee_category (id, school_id, code, name, created_at, updated_at)
SELECT id, '99999999-9999-9999-9999-999999999999', code, name, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM t_fee_category
WHERE (SELECT should_seed FROM jnv_seed_guard);

CREATE TEMP TABLE t_fee_structure (id UUID DEFAULT gen_random_uuid(), class_section_id UUID);
INSERT INTO t_fee_structure (class_section_id) SELECT id FROM t_class_section;

INSERT INTO fee_structure (id, school_id, class_section_id, academic_year, created_at, updated_at)
SELECT id, '99999999-9999-9999-9999-999999999999', class_section_id, '2025-26', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM t_fee_structure
WHERE (SELECT should_seed FROM jnv_seed_guard);

INSERT INTO fee_structure_line (id, school_id, fee_structure_id, fee_category_id, amount, created_at, updated_at)
SELECT gen_random_uuid(), '99999999-9999-9999-9999-999999999999', fs.id, fc.id, fc.amount, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM t_fee_structure fs
CROSS JOIN t_fee_category fc
WHERE (SELECT should_seed FROM jnv_seed_guard);

CREATE TEMP TABLE t_student_fee_assessment (
    id UUID DEFAULT gen_random_uuid(),
    student_id UUID,
    global_rn BIGINT,
    total_due NUMERIC(12,2),
    total_paid NUMERIC(12,2),
    status TEXT
);
INSERT INTO t_student_fee_assessment (student_id, global_rn, total_due, total_paid, status)
SELECT
    st.id, st.global_rn,
    17550.00,
    CASE
        WHEN st.global_rn % 10 = 0 THEN 0.00
        WHEN st.global_rn % 5 = 0 THEN 8000.00
        ELSE 17550.00
    END,
    CASE
        WHEN st.global_rn % 10 = 0 THEN 'PENDING'
        WHEN st.global_rn % 5 = 0 THEN 'PARTIALLY_PAID'
        ELSE 'PAID'
    END
FROM t_student st;

INSERT INTO student_fee_assessment (id, school_id, student_id, academic_year, total_due, total_paid, status, due_date, created_at, updated_at)
SELECT id, '99999999-9999-9999-9999-999999999999', student_id, '2025-26', total_due, total_paid, status, DATE '2025-12-31', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM t_student_fee_assessment
WHERE (SELECT should_seed FROM jnv_seed_guard);

CREATE TEMP TABLE t_fee_txn (id UUID DEFAULT gen_random_uuid(), assessment_id UUID, amount NUMERIC(12,2), pay_date DATE);
INSERT INTO t_fee_txn (assessment_id, amount, pay_date)
SELECT sfa.id, sfa.total_paid,
       DATE '2025-06-15' + ((sfa.global_rn % 90) || ' days')::interval
FROM t_student_fee_assessment sfa
WHERE sfa.total_paid > 0;

INSERT INTO financial_transaction (id, school_id, direction, source_type, source_id, amount, payment_method, payment_reference, transaction_date, receipt_number, status, fund_account_id, notes, created_at, updated_at)
SELECT
    id, '99999999-9999-9999-9999-999999999999', 'CREDIT', 'FEE_PAYMENT', assessment_id, amount,
    (ARRAY['CASH','UPI','BANK_TRANSFER','CHEQUE'])[width_bucket(random(),0,1,4)],
    'REF-FEE-' || left(id::text, 8), pay_date, 'RCPT-FEE-' || left(id::text, 8), 'COMPLETED',
    (SELECT id FROM t_fund_account WHERE code = 'GENERAL'), 'Fee payment', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM t_fee_txn
WHERE (SELECT should_seed FROM jnv_seed_guard);

INSERT INTO fee_payment (id, school_id, assessment_id, amount, transaction_id, created_at, updated_at)
SELECT gen_random_uuid(), '99999999-9999-9999-9999-999999999999', assessment_id, amount, id, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM t_fee_txn
WHERE (SELECT should_seed FROM jnv_seed_guard);

-- ===========================================================================================
-- PART 8: vendors
-- ===========================================================================================
CREATE TEMP TABLE t_vendor (id UUID DEFAULT gen_random_uuid(), name TEXT);
INSERT INTO t_vendor (name) VALUES
    ('Rajasthan Book Depot'), ('Udaipur Lab Supplies'), ('Sunrise Sports Store'),
    ('Modern Furniture Works'), ('TechZone IT Solutions'), ('Green Campus Landscaping'),
    ('National School Uniforms'), ('Everfresh Canteen Supplies'), ('CityBus Transport Co.'),
    ('BuildRight Contractors');

INSERT INTO vendor (id, school_id, name, contact_phone, contact_email, bank_account, upi_id, address, created_at, updated_at)
SELECT id, '99999999-9999-9999-9999-999999999999', name,
       '9' || lpad((900000000 + (row_number() OVER ()))::text, 9, '0'),
       lower(replace(name, ' ', '')) || '@vendor.demo',
       'VEND' || lpad((row_number() OVER ())::text, 8, '0'),
       lower(replace(name, ' ', '')) || '@upi',
       'Industrial Area, Udaipur, Rajasthan', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM t_vendor
WHERE (SELECT should_seed FROM jnv_seed_guard);

-- ===========================================================================================
-- PART 9: payroll (19 months, Jan 2025 - Jul 2026)
-- ===========================================================================================
CREATE TEMP TABLE t_payroll_run (id UUID DEFAULT gen_random_uuid(), month_start DATE, payroll_month INT, payroll_year INT, status TEXT);
INSERT INTO t_payroll_run (month_start, payroll_month, payroll_year, status)
SELECT d::date, extract(month FROM d)::int, extract(year FROM d)::int,
       CASE WHEN d >= DATE '2026-07-01' THEN 'PENDING' ELSE 'PAID' END
FROM generate_series(DATE '2025-01-01', DATE '2026-07-01', interval '1 month') d;

INSERT INTO payroll_run (id, school_id, payroll_month, payroll_year, status, created_at, updated_at)
SELECT id, '99999999-9999-9999-9999-999999999999', payroll_month, payroll_year, status, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM t_payroll_run
WHERE (SELECT should_seed FROM jnv_seed_guard);

INSERT INTO salary_structure (id, school_id, employee_id, basic, allowances, deductions, effective_from, created_at, updated_at)
SELECT gen_random_uuid(), '99999999-9999-9999-9999-999999999999', id, basic, round(basic * 0.3, 2), round(basic * 0.08, 2), DATE '2025-01-01', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM t_employee
WHERE (SELECT should_seed FROM jnv_seed_guard);

CREATE TEMP TABLE t_payroll_line (
    id UUID DEFAULT gen_random_uuid(),
    run_id UUID,
    employee_id UUID,
    gross NUMERIC(12,2),
    deductions NUMERIC(12,2),
    net NUMERIC(12,2),
    paid BOOLEAN
);
INSERT INTO t_payroll_line (run_id, employee_id, gross, deductions, net, paid)
SELECT pr.id, e.id, round(e.basic * 1.3, 2), round(e.basic * 0.08, 2), round(e.basic * 1.22, 2), (pr.status = 'PAID')
FROM t_payroll_run pr
CROSS JOIN t_employee e;

INSERT INTO payroll_line (id, school_id, run_id, employee_id, gross, deductions, net, created_at, updated_at)
SELECT id, '99999999-9999-9999-9999-999999999999', run_id, employee_id, gross, deductions, net, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM t_payroll_line
WHERE (SELECT should_seed FROM jnv_seed_guard);

CREATE TEMP TABLE t_salary_txn (id UUID DEFAULT gen_random_uuid(), payroll_line_id UUID, amount NUMERIC(12,2));
INSERT INTO t_salary_txn (payroll_line_id, amount)
SELECT id, net FROM t_payroll_line WHERE paid;

INSERT INTO financial_transaction (id, school_id, direction, source_type, source_id, amount, payment_method, payment_reference, transaction_date, receipt_number, status, fund_account_id, notes, created_at, updated_at)
SELECT
    st.id, '99999999-9999-9999-9999-999999999999', 'DEBIT', 'SALARY_PAYMENT', st.payroll_line_id, st.amount,
    'BANK_TRANSFER', 'REF-SAL-' || left(st.id::text, 8),
    (SELECT month_start + interval '4 days' FROM t_payroll_run pr JOIN t_payroll_line pl ON pl.run_id = pr.id WHERE pl.id = st.payroll_line_id),
    'RCPT-SAL-' || left(st.id::text, 8), 'COMPLETED',
    (SELECT id FROM t_fund_account WHERE code = 'PAYROLL'), 'Monthly salary disbursement', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM t_salary_txn st
WHERE (SELECT should_seed FROM jnv_seed_guard);

INSERT INTO salary_payment (id, school_id, payroll_line_id, transaction_id, created_at, updated_at)
SELECT gen_random_uuid(), '99999999-9999-9999-9999-999999999999', payroll_line_id, id, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM t_salary_txn
WHERE (SELECT should_seed FROM jnv_seed_guard);

INSERT INTO payslip (id, school_id, payroll_line_id, document_ref, created_at, updated_at)
SELECT gen_random_uuid(), '99999999-9999-9999-9999-999999999999', payroll_line_id, 'payslips/' || left(payroll_line_id::text, 8) || '.pdf', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM t_salary_txn
WHERE (SELECT should_seed FROM jnv_seed_guard);

-- ===========================================================================================
-- PART 10: school events + collections
-- ===========================================================================================
CREATE TEMP TABLE t_school_event (id UUID DEFAULT gen_random_uuid(), name TEXT, event_date DATE, status TEXT, inflow_enabled BOOLEAN, outflow_enabled BOOLEAN);
INSERT INTO t_school_event (name, event_date, status, inflow_enabled, outflow_enabled) VALUES
    ('Independence Day Celebration', DATE '2025-08-15', 'COMPLETED', FALSE, TRUE),
    ('Annual Sports Day', DATE '2025-09-20', 'COMPLETED', TRUE, TRUE),
    ('Founders Day', DATE '2025-10-05', 'COMPLETED', TRUE, TRUE),
    ('Science Exhibition', DATE '2025-11-12', 'COMPLETED', TRUE, TRUE),
    ('Annual Day Function', DATE '2025-12-20', 'COMPLETED', TRUE, TRUE),
    ('Republic Day Celebration', DATE '2026-01-26', 'COMPLETED', FALSE, TRUE),
    ('Winter Picnic', DATE '2026-01-10', 'COMPLETED', TRUE, TRUE),
    ('Inter-School Debate', DATE '2026-02-08', 'COMPLETED', FALSE, FALSE),
    ('Art & Craft Fair', DATE '2026-02-22', 'COMPLETED', TRUE, TRUE),
    ('Farewell Party (Grade 12)', DATE '2026-03-15', 'PLANNED', TRUE, TRUE),
    ('Summer Camp', DATE '2026-05-10', 'PLANNED', TRUE, TRUE),
    ('Parent-Teacher Meet', DATE '2026-06-01', 'PLANNED', FALSE, FALSE);

INSERT INTO school_event (id, school_id, name, description, event_date, status, inflow_enabled, outflow_enabled, created_at, updated_at)
SELECT id, '99999999-9999-9999-9999-999999999999', name, name || ' organized by JNV', event_date, status, inflow_enabled, outflow_enabled, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM t_school_event
WHERE (SELECT should_seed FROM jnv_seed_guard);

INSERT INTO event_participation_fee (id, school_id, event_id, participant_type, amount, created_at, updated_at)
SELECT gen_random_uuid(), '99999999-9999-9999-9999-999999999999', id, pt, amt, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM t_school_event
CROSS JOIN (VALUES ('STUDENT', 100.00), ('GUEST', 250.00)) AS v(pt, amt)
WHERE inflow_enabled AND (SELECT should_seed FROM jnv_seed_guard);

CREATE TEMP TABLE t_event_collection (id UUID DEFAULT gen_random_uuid(), event_id UUID, payer_name TEXT, amount NUMERIC(12,2), pay_date DATE);
INSERT INTO t_event_collection (event_id, payer_name, amount, pay_date)
SELECT ev.id, n.first_name || ' ' || n.last_name, 100.00 + (g * 10), ev.event_date - (g || ' days')::interval
FROM t_school_event ev
CROSS JOIN generate_series(1, 5) g
JOIN t_names n ON n.idx = 1 + (g % 40)
WHERE ev.inflow_enabled;

INSERT INTO financial_transaction (id, school_id, direction, source_type, source_id, amount, payment_method, payment_reference, transaction_date, receipt_number, status, fund_account_id, notes, created_at, updated_at)
SELECT
    id, '99999999-9999-9999-9999-999999999999', 'CREDIT', 'EVENT_COLLECTION', event_id, amount,
    'UPI', 'REF-EVT-' || left(id::text, 8), pay_date, 'RCPT-EVT-' || left(id::text, 8), 'COMPLETED',
    (SELECT id FROM t_fund_account WHERE code = 'EVENTS'), 'Event participation collection', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM t_event_collection
WHERE (SELECT should_seed FROM jnv_seed_guard);

INSERT INTO event_collection_payment (id, school_id, event_id, payer_name, payer_reference, amount, transaction_id, created_at, updated_at)
SELECT gen_random_uuid(), '99999999-9999-9999-9999-999999999999', event_id, payer_name, 'PARENT-' || left(id::text, 6), amount, id, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM t_event_collection
WHERE (SELECT should_seed FROM jnv_seed_guard);

-- ===========================================================================================
-- PART 11: sponsorships
-- ===========================================================================================
CREATE TEMP TABLE t_sponsor (id UUID DEFAULT gen_random_uuid(), name TEXT, pan TEXT);
INSERT INTO t_sponsor (name, pan) VALUES
    ('Rajasthan Education Trust', 'AAAPT1234A'), ('Nair Family Foundation', 'AAAPN5678B'),
    ('TechForGood Foundation', 'AAAPT9012C'), ('Udaipur Rotary Club', 'AAAPU3456D'),
    ('Sharma Charitable Trust', 'AAAPS7890E'), ('Global Education Alliance', 'AAAPG2345F'),
    ('State Bank CSR Wing', 'AAAPS6789G'), ('Verma Alumni Association', 'AAAPV0123H');

INSERT INTO sponsor (id, school_id, name, contact_phone, contact_email, pan, created_at, updated_at)
SELECT id, '99999999-9999-9999-9999-999999999999', name,
       '9' || lpad((910000000 + row_number() OVER ())::text, 9, '0'),
       lower(replace(name, ' ', '')) || '@sponsor.demo', pan, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM t_sponsor
WHERE (SELECT should_seed FROM jnv_seed_guard);

CREATE TEMP TABLE t_sponsorship (id UUID DEFAULT gen_random_uuid(), sponsor_id UUID, purpose TEXT, pledged_amount NUMERIC(12,2), status TEXT);
INSERT INTO t_sponsorship (sponsor_id, purpose, pledged_amount, status)
SELECT sp.id, purpose, amt, status
FROM t_sponsor sp
CROSS JOIN LATERAL (
    SELECT * FROM (VALUES
        ('SCHOLARSHIP', 50000.00, 'ACTIVE'),
        ('INFRASTRUCTURE', 120000.00, 'ACTIVE')
    ) AS v(purpose, amt, status)
) x
LIMIT 10;

INSERT INTO sponsorship (id, school_id, sponsor_id, purpose, pledged_amount, fund_account_id, status, created_at, updated_at)
SELECT id, '99999999-9999-9999-9999-999999999999', sponsor_id, purpose, pledged_amount,
       (SELECT id FROM t_fund_account WHERE code = 'SPONSORSHIP'), status, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM t_sponsorship
WHERE (SELECT should_seed FROM jnv_seed_guard);

CREATE TEMP TABLE t_sponsorship_txn (id UUID DEFAULT gen_random_uuid(), sponsorship_id UUID, amount NUMERIC(12,2));
INSERT INTO t_sponsorship_txn (sponsorship_id, amount)
SELECT id, round(pledged_amount * 0.5, 2) FROM t_sponsorship;

INSERT INTO financial_transaction (id, school_id, direction, source_type, source_id, amount, payment_method, payment_reference, transaction_date, receipt_number, status, fund_account_id, notes, created_at, updated_at)
SELECT
    id, '99999999-9999-9999-9999-999999999999', 'CREDIT', 'SPONSORSHIP_PAYMENT', sponsorship_id, amount,
    'BANK_TRANSFER', 'REF-SPN-' || left(id::text, 8), DATE '2025-09-01', 'RCPT-SPN-' || left(id::text, 8), 'COMPLETED',
    (SELECT id FROM t_fund_account WHERE code = 'SPONSORSHIP'), 'First installment', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM t_sponsorship_txn
WHERE (SELECT should_seed FROM jnv_seed_guard);

INSERT INTO sponsorship_payment (id, school_id, sponsorship_id, amount, transaction_id, created_at, updated_at)
SELECT gen_random_uuid(), '99999999-9999-9999-9999-999999999999', sponsorship_id, amount, id, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM t_sponsorship_txn
WHERE (SELECT should_seed FROM jnv_seed_guard);

-- ===========================================================================================
-- PART 12: infrastructure expenses
-- ===========================================================================================
CREATE TEMP TABLE t_infra_category (id UUID DEFAULT gen_random_uuid(), code TEXT, name TEXT);
INSERT INTO t_infra_category (code, name) VALUES
    ('LIBRARY', 'Library'), ('LAB', 'Laboratory'), ('SPORTS', 'Sports Equipment'),
    ('FURNITURE', 'Furniture'), ('CLASSROOM', 'Classroom Maintenance'), ('IT', 'IT Equipment');

INSERT INTO infra_expense_category (id, school_id, code, name, created_at, updated_at)
SELECT id, '99999999-9999-9999-9999-999999999999', code, name, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM t_infra_category
WHERE (SELECT should_seed FROM jnv_seed_guard);

CREATE TEMP TABLE t_infra_request (id UUID DEFAULT gen_random_uuid(), category_id UUID, description TEXT, estimated_amount NUMERIC(12,2), status TEXT, rn BIGINT);
INSERT INTO t_infra_request (category_id, description, estimated_amount, status, rn)
SELECT c.id, c.name || ' restock/upgrade request #' || g, 5000 + (g * 750),
       CASE WHEN g % 3 = 0 THEN 'PENDING' WHEN g % 3 = 1 THEN 'APPROVED' ELSE 'COMPLETED' END,
       row_number() OVER ()
FROM t_infra_category c
CROSS JOIN generate_series(1, 3) g;

INSERT INTO infra_expense_request (id, school_id, category_id, description, estimated_amount, status, created_at, updated_at)
SELECT id, '99999999-9999-9999-9999-999999999999', category_id, description, estimated_amount, status, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM t_infra_request
WHERE (SELECT should_seed FROM jnv_seed_guard);

CREATE TEMP TABLE t_infra_purchase (id UUID DEFAULT gen_random_uuid(), request_id UUID, vendor_id UUID, actual_amount NUMERIC(12,2));
INSERT INTO t_infra_purchase (request_id, vendor_id, actual_amount)
SELECT r.id, v.id, r.estimated_amount * 0.95
FROM t_infra_request r
JOIN LATERAL (SELECT id FROM t_vendor ORDER BY random() LIMIT 1) v ON TRUE
WHERE r.status IN ('APPROVED', 'COMPLETED');

INSERT INTO infra_purchase_record (id, school_id, request_id, vendor_id, invoice_number, actual_amount, created_at, updated_at)
SELECT id, '99999999-9999-9999-9999-999999999999', request_id, vendor_id, 'INV-' || left(id::text, 8), actual_amount, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM t_infra_purchase
WHERE (SELECT should_seed FROM jnv_seed_guard);

INSERT INTO financial_transaction (id, school_id, direction, source_type, source_id, amount, payment_method, payment_reference, transaction_date, receipt_number, status, fund_account_id, notes, created_at, updated_at)
SELECT
    gen_random_uuid(), '99999999-9999-9999-9999-999999999999', 'DEBIT', 'INFRA_VENDOR_PAYMENT', id, actual_amount,
    'BANK_TRANSFER', 'REF-INF-' || left(id::text, 8), DATE '2025-09-15', 'RCPT-INF-' || left(id::text, 8), 'COMPLETED',
    (SELECT id FROM t_fund_account WHERE code = 'GENERAL'), 'Infrastructure purchase settlement', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM t_infra_purchase
WHERE (SELECT should_seed FROM jnv_seed_guard);

INSERT INTO infra_vendor_payment (id, school_id, purchase_id, transaction_id, created_at, updated_at)
SELECT gen_random_uuid(), '99999999-9999-9999-9999-999999999999', ip.id, ft.id, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM t_infra_purchase ip
JOIN financial_transaction ft ON ft.source_id = ip.id AND ft.source_type = 'INFRA_VENDOR_PAYMENT'
WHERE (SELECT should_seed FROM jnv_seed_guard);

-- ===========================================================================================
-- PART 13: event expenses
-- ===========================================================================================
CREATE TEMP TABLE t_event_budget (id UUID DEFAULT gen_random_uuid(), event_id UUID);
INSERT INTO t_event_budget (event_id) SELECT id FROM t_school_event WHERE outflow_enabled;

INSERT INTO event_budget (id, school_id, event_id, created_at, updated_at)
SELECT id, '99999999-9999-9999-9999-999999999999', event_id, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM t_event_budget
WHERE (SELECT should_seed FROM jnv_seed_guard);

CREATE TEMP TABLE t_event_budget_line (id UUID DEFAULT gen_random_uuid(), budget_id UUID, description TEXT, planned_amount NUMERIC(12,2));
INSERT INTO t_event_budget_line (budget_id, description, planned_amount)
SELECT b.id, ln.description, ln.amt
FROM t_event_budget b
CROSS JOIN (VALUES
    ('Decoration & Stage Setup', 8000.00),
    ('Refreshments & Catering', 12000.00),
    ('Prizes & Certificates', 5000.00),
    ('Sound & Lighting', 7000.00)
) AS ln(description, amt);

INSERT INTO event_budget_line (id, school_id, budget_id, description, planned_amount, created_at, updated_at)
SELECT id, '99999999-9999-9999-9999-999999999999', budget_id, description, planned_amount, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM t_event_budget_line
WHERE (SELECT should_seed FROM jnv_seed_guard);

CREATE TEMP TABLE t_event_expense_request (id UUID DEFAULT gen_random_uuid(), budget_line_id UUID, estimated_amount NUMERIC(12,2), status TEXT, rn BIGINT);
INSERT INTO t_event_expense_request (budget_line_id, estimated_amount, status, rn)
SELECT id, planned_amount * 0.9,
       CASE WHEN row_number() OVER () % 3 = 0 THEN 'PENDING' ELSE 'APPROVED' END,
       row_number() OVER ()
FROM t_event_budget_line;

INSERT INTO event_expense_request (id, school_id, budget_line_id, description, estimated_amount, status, created_at, updated_at)
SELECT id, '99999999-9999-9999-9999-999999999999', budget_line_id, 'Expense for budget line', estimated_amount, status, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM t_event_expense_request
WHERE (SELECT should_seed FROM jnv_seed_guard);

CREATE TEMP TABLE t_event_vendor_payment (id UUID DEFAULT gen_random_uuid(), request_id UUID, vendor_id UUID, amount NUMERIC(12,2));
INSERT INTO t_event_vendor_payment (request_id, vendor_id, amount)
SELECT r.id, v.id, r.estimated_amount
FROM t_event_expense_request r
JOIN LATERAL (SELECT id FROM t_vendor ORDER BY random() LIMIT 1) v ON TRUE
WHERE r.status = 'APPROVED';

INSERT INTO financial_transaction (id, school_id, direction, source_type, source_id, amount, payment_method, payment_reference, transaction_date, receipt_number, status, fund_account_id, notes, created_at, updated_at)
SELECT
    gen_random_uuid(), '99999999-9999-9999-9999-999999999999', 'DEBIT', 'EVENT_VENDOR_PAYMENT', id, amount,
    'CHEQUE', 'REF-EVX-' || left(id::text, 8), DATE '2025-09-18', 'RCPT-EVX-' || left(id::text, 8), 'COMPLETED',
    (SELECT id FROM t_fund_account WHERE code = 'EVENTS'), 'Event vendor settlement', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM t_event_vendor_payment
WHERE (SELECT should_seed FROM jnv_seed_guard);

INSERT INTO event_vendor_payment (id, school_id, request_id, vendor_id, transaction_id, created_at, updated_at)
SELECT gen_random_uuid(), '99999999-9999-9999-9999-999999999999', evp.request_id, evp.vendor_id, ft.id, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM t_event_vendor_payment evp
JOIN financial_transaction ft ON ft.source_id = evp.id AND ft.source_type = 'EVENT_VENDOR_PAYMENT'
WHERE (SELECT should_seed FROM jnv_seed_guard);

-- ===========================================================================================
-- PART 14: workflow approvals (for infra + event expense requests)
-- ===========================================================================================
CREATE TEMP TABLE t_approval_request (id UUID DEFAULT gen_random_uuid(), entity_type TEXT, entity_id UUID, status TEXT);
INSERT INTO t_approval_request (entity_type, entity_id, status)
SELECT 'INFRA_EXPENSE_REQUEST', id, status FROM t_infra_request
UNION ALL
SELECT 'EVENT_EXPENSE_REQUEST', id, status FROM t_event_expense_request;

INSERT INTO approval_request (id, school_id, entity_type, entity_id, status, submitted_by, approved_by, comment, created_at, updated_at)
SELECT id, '99999999-9999-9999-9999-999999999999', entity_type, entity_id, status,
       'accountant@jnv.demo',
       CASE WHEN status IN ('APPROVED', 'COMPLETED') THEN 'vice.principal@jnv.demo' ELSE NULL END,
       'Auto-generated approval workflow record', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM t_approval_request
WHERE (SELECT should_seed FROM jnv_seed_guard);

INSERT INTO approval_history (id, school_id, approval_request_id, from_status, to_status, changed_by, changed_at, created_at, updated_at)
SELECT gen_random_uuid(), '99999999-9999-9999-9999-999999999999', id, NULL, 'PENDING', 'accountant@jnv.demo', CURRENT_TIMESTAMP - interval '20 days', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM t_approval_request
WHERE (SELECT should_seed FROM jnv_seed_guard);

INSERT INTO approval_history (id, school_id, approval_request_id, from_status, to_status, changed_by, changed_at, created_at, updated_at)
SELECT gen_random_uuid(), '99999999-9999-9999-9999-999999999999', id, 'PENDING', status, 'vice.principal@jnv.demo', CURRENT_TIMESTAMP - interval '15 days', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM t_approval_request
WHERE status != 'PENDING' AND (SELECT should_seed FROM jnv_seed_guard);

-- ===========================================================================================
-- PART 15: credentials (all employees + a sample of 200 students)
-- ===========================================================================================
INSERT INTO credential (id, school_id, owner_type, owner_id, username, password_hash, role, created_at, updated_at)
SELECT
    gen_random_uuid(), '99999999-9999-9999-9999-999999999999', 'EMPLOYEE', id, contact_phone,
    '$2a$10$JUk9x9LMYVT951gsh4VZDOZrGxuGRX/DKqDwsX4fxMZsfArokccNe',
    CASE WHEN role_tag IN ('CLASS_TEACHER', 'SPECIALIST') THEN 'TEACHER'
         ELSE 'ADMIN' END,
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM t_employee
WHERE (SELECT should_seed FROM jnv_seed_guard);

INSERT INTO credential (id, school_id, owner_type, owner_id, username, password_hash, role, created_at, updated_at)
SELECT
    gen_random_uuid(), '99999999-9999-9999-9999-999999999999', 'STUDENT', id, parent_contact,
    '$2a$10$JUk9x9LMYVT951gsh4VZDOZrGxuGRX/DKqDwsX4fxMZsfArokccNe',
    'STUDENT', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM t_student
WHERE global_rn <= 200 AND (SELECT should_seed FROM jnv_seed_guard);

-- ===========================================================================================
-- PART 16: chat (1:1 staff<->student, staff<->staff, helpdesk bot threads)
-- ===========================================================================================
CREATE TEMP TABLE t_conversation (id UUID DEFAULT gen_random_uuid(), type TEXT);

INSERT INTO t_conversation (type)
SELECT 'STAFF_STUDENT' FROM generate_series(1, 60)
UNION ALL
SELECT 'STAFF_STAFF' FROM generate_series(1, 20)
UNION ALL
SELECT 'BOT' FROM generate_series(1, 10);

INSERT INTO conversation (id, school_id, type, created_at, updated_at)
SELECT id, '99999999-9999-9999-9999-999999999999', type, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM t_conversation
WHERE (SELECT should_seed FROM jnv_seed_guard);

CREATE TEMP TABLE t_conversation_participant (id UUID DEFAULT gen_random_uuid(), conversation_id UUID, owner_type TEXT, owner_id UUID);

WITH staff_student AS (
    SELECT c.id AS conversation_id, row_number() OVER (ORDER BY c.id) AS rn
    FROM t_conversation c WHERE c.type = 'STAFF_STUDENT'
),
teachers AS (SELECT id, row_number() OVER (ORDER BY id) AS rn FROM t_employee WHERE role_tag = 'CLASS_TEACHER'),
students_sample AS (SELECT id, row_number() OVER (ORDER BY id) AS rn FROM t_student WHERE global_rn <= 60)
INSERT INTO t_conversation_participant (conversation_id, owner_type, owner_id)
SELECT ss.conversation_id, 'EMPLOYEE', t.id FROM staff_student ss JOIN teachers t ON t.rn = 1 + ((ss.rn - 1) % 21)
UNION ALL
SELECT ss.conversation_id, 'STUDENT', s.id FROM staff_student ss JOIN students_sample s ON s.rn = ss.rn;

WITH staff_staff AS (
    SELECT c.id AS conversation_id, row_number() OVER (ORDER BY c.id) AS rn
    FROM t_conversation c WHERE c.type = 'STAFF_STAFF'
),
emp_a AS (SELECT id, row_number() OVER (ORDER BY id) AS rn FROM t_employee),
emp_b AS (SELECT id, row_number() OVER (ORDER BY id) AS rn FROM t_employee)
INSERT INTO t_conversation_participant (conversation_id, owner_type, owner_id)
SELECT ss.conversation_id, 'EMPLOYEE', a.id FROM staff_staff ss JOIN emp_a a ON a.rn = 1 + ((ss.rn - 1) % 46)
UNION ALL
SELECT ss.conversation_id, 'EMPLOYEE', b.id FROM staff_staff ss JOIN emp_b b ON b.rn = 1 + ((ss.rn + 10) % 46) AND b.rn != 1 + ((ss.rn - 1) % 46);

WITH bot AS (
    SELECT c.id AS conversation_id, row_number() OVER (ORDER BY c.id) AS rn
    FROM t_conversation c WHERE c.type = 'BOT'
),
emp AS (SELECT id, row_number() OVER (ORDER BY id) AS rn FROM t_employee)
INSERT INTO t_conversation_participant (conversation_id, owner_type, owner_id)
SELECT b.conversation_id, 'EMPLOYEE', e.id FROM bot b JOIN emp e ON e.rn = 1 + ((b.rn - 1) % 46);

INSERT INTO conversation_participant (id, school_id, conversation_id, owner_type, owner_id, created_at, updated_at)
SELECT id, '99999999-9999-9999-9999-999999999999', conversation_id, owner_type, owner_id, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM t_conversation_participant
WHERE (SELECT should_seed FROM jnv_seed_guard);

-- Human messages: each STAFF_STUDENT/STAFF_STAFF conversation gets ~8 alternating messages.
INSERT INTO message (id, school_id, conversation_id, sender_kind, sender_owner_type, sender_owner_id, content, sent_at, created_at, updated_at)
SELECT
    gen_random_uuid(), '99999999-9999-9999-9999-999999999999', p.conversation_id, 'HUMAN', p.owner_type, p.owner_id,
    (ARRAY['Hello, how are things going?', 'Please share the latest update.', 'Thanks, noted.',
           'Can we discuss this tomorrow?', 'Sure, that works for me.', 'Attaching the details as requested.',
           'Following up on this.', 'Appreciate the quick response.'])[g],
    CURRENT_TIMESTAMP - interval '10 days' + (g || ' hours')::interval, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM t_conversation_participant p
JOIN t_conversation c ON c.id = p.conversation_id
CROSS JOIN generate_series(1, 4) g
WHERE c.type IN ('STAFF_STUDENT', 'STAFF_STAFF') AND (SELECT should_seed FROM jnv_seed_guard);

-- Helpdesk BOT threads: human asks, bot replies (sender_owner_type/id NULL per chk_message_sender).
INSERT INTO message (id, school_id, conversation_id, sender_kind, sender_owner_type, sender_owner_id, content, sent_at, created_at, updated_at)
SELECT gen_random_uuid(), '99999999-9999-9999-9999-999999999999', p.conversation_id, 'HUMAN', p.owner_type, p.owner_id,
       'What is the process for requesting infrastructure expense approval?', CURRENT_TIMESTAMP - interval '5 days', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM t_conversation_participant p JOIN t_conversation c ON c.id = p.conversation_id
WHERE c.type = 'BOT' AND (SELECT should_seed FROM jnv_seed_guard);

INSERT INTO message (id, school_id, conversation_id, sender_kind, sender_owner_type, sender_owner_id, content, sent_at, created_at, updated_at)
SELECT gen_random_uuid(), '99999999-9999-9999-9999-999999999999', id, 'BOT', NULL, NULL,
       'Submit an infrastructure expense request via the Expenses module; it will route to the Vice Principal for approval.',
       CURRENT_TIMESTAMP - interval '5 days' + interval '2 minutes', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM t_conversation
WHERE type = 'BOT' AND (SELECT should_seed FROM jnv_seed_guard);

-- ===========================================================================================
-- PART 17: announcements (school-wide + one per class section)
-- ===========================================================================================
INSERT INTO announcement (id, school_id, scope, section_id, author_employee_id, title, body, created_at, updated_at)
SELECT gen_random_uuid(), '99999999-9999-9999-9999-999999999999', 'SCHOOL', NULL,
       (SELECT id FROM t_employee WHERE designation = 'Vice Principal' LIMIT 1),
       title, body, CURRENT_TIMESTAMP - (g || ' days')::interval, CURRENT_TIMESTAMP
FROM generate_series(1, 10) g
CROSS JOIN LATERAL (VALUES (
    'School Notice #' || g,
    'This is a school-wide announcement regarding upcoming activities and schedule changes.'
)) AS v(title, body)
WHERE (SELECT should_seed FROM jnv_seed_guard);

INSERT INTO announcement (id, school_id, scope, section_id, author_employee_id, title, body, created_at, updated_at)
SELECT gen_random_uuid(), '99999999-9999-9999-9999-999999999999', 'CLASS', cs.id, cs.class_teacher_id,
       'Class Update - Grade ' || cs.grade || cs.section,
       'Homework, exam schedule, and other updates for this class section.',
       CURRENT_TIMESTAMP - interval '3 days', CURRENT_TIMESTAMP
FROM t_class_section cs
WHERE (SELECT should_seed FROM jnv_seed_guard);
