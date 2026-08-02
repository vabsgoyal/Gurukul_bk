-- V25's name pool used CROSS JOIN LATERAL with LIMIT 40 applied *after* the join, which only ever
-- reached the first 2 of the 40 first names (each paired with all 20 last names) before the LIMIT
-- cut it off - so every JNV student/employee/parent name was drawn from just 2 first names,
-- producing heavy duplication (e.g. many students literally named "Aarav Sharma", parent names
-- sometimes matching the student's own name). V25 also generated 8-digit contact numbers
-- ('98' + 6 digits) instead of realistic 10-digit Indian mobile numbers.
--
-- This corrects the already-seeded JNV rows in place (V25 must never be edited once applied - see
-- the checksum-mismatch incident this migration follows). It rebuilds names/phone numbers
-- deterministically from each row's existing roll_number/bank_account suffix, using an expanded
-- 60 first-name x 30 last-name pool addressed via mixed-radix indexing (first = n mod 60,
-- last = (n / 60) mod 30) so all 1,050 students get distinct combinations with zero repeats.

CREATE TEMP TABLE t_first_name (idx BIGINT GENERATED ALWAYS AS IDENTITY, name TEXT);
INSERT INTO t_first_name (name) VALUES
    ('Aarav'),('Vivaan'),('Aditya'),('Vihaan'),('Arjun'),('Sai'),('Reyansh'),('Krishna'),('Ishaan'),('Rohan'),
    ('Priya'),('Ananya'),('Diya'),('Saanvi'),('Aadhya'),('Kavya'),('Myra'),('Anika'),('Isha'),('Neha'),
    ('Rahul'),('Amit'),('Vikram'),('Sanjay'),('Rajesh'),('Deepak'),('Manoj'),('Ashok'),('Ramesh'),('Suresh'),
    ('Pooja'),('Sunita'),('Kiran'),('Meena'),('Geeta'),('Rekha'),('Shalini'),('Nisha'),('Swati'),('Vandana'),
    ('Karan'),('Aryan'),('Dhruv'),('Yash'),('Aniket'),('Siddharth'),('Nikhil'),('Varun'),('Abhishek'),('Gaurav'),
    ('Neetu'),('Ritu'),('Simran'),('Pallavi'),('Divya'),('Shreya'),('Tanvi'),('Riya'),('Snehal'),('Komal');

CREATE TEMP TABLE t_last_name (idx BIGINT GENERATED ALWAYS AS IDENTITY, name TEXT);
INSERT INTO t_last_name (name) VALUES
    ('Sharma'),('Verma'),('Gupta'),('Singh'),('Kumar'),('Yadav'),('Mishra'),('Pandey'),('Reddy'),('Nair'),
    ('Iyer'),('Chauhan'),('Rathore'),('Joshi'),('Bhatt'),('Menon'),('Das'),('Ghosh'),('Patel'),('Shah'),
    ('Agarwal'),('Malhotra'),('Kapoor'),('Chopra'),('Bose'),('Nayar'),('Trivedi'),('Desai'),('Rao'),('Pillai');

-- Students: derive global_rn from roll_number ('JNV-00001' -> 1), recompute name/parent_name/
-- parent_contact. Parent name uses a +517 offset (517 is coprime to both pool sizes) so it never
-- lands on the same combination as the student's own name.
CREATE TEMP TABLE t_student_fix AS
SELECT
    s.id,
    s.parent_contact AS old_parent_contact,
    substring(s.roll_number FROM 5)::bigint AS global_rn
FROM student s
WHERE s.school_id = '99999999-9999-9999-9999-999999999999';

UPDATE student st
SET
    name = fn.name || ' ' || ln.name,
    parent_name = pfn.name || ' ' || pln.name,
    parent_contact = '9' || lpad((700000000 + f.global_rn)::text, 9, '0')
FROM t_student_fix f
JOIN t_first_name fn ON fn.idx = 1 + ((f.global_rn - 1) % 60)
JOIN t_last_name ln ON ln.idx = 1 + (((f.global_rn - 1) / 60) % 30)
JOIN t_first_name pfn ON pfn.idx = 1 + ((f.global_rn - 1 + 517) % 60)
JOIN t_last_name pln ON pln.idx = 1 + (((f.global_rn - 1 + 517) / 60) % 30)
WHERE st.id = f.id;

UPDATE credential c
SET username = '9' || lpad((700000000 + f.global_rn)::text, 9, '0'), updated_at = CURRENT_TIMESTAMP
FROM t_student_fix f
WHERE c.school_id = '99999999-9999-9999-9999-999999999999'
  AND c.owner_type = 'STUDENT'
  AND c.owner_id = f.id;

-- Employees: derive rn from bank_account ('JNVBANK00000001' -> 1), recompute name/contact_phone/
-- contact_email (email is name-derived, so it must follow the corrected name).
CREATE TEMP TABLE t_employee_fix AS
SELECT
    e.id,
    substring(e.bank_account FROM 8)::bigint AS rn
FROM employee e
WHERE e.school_id = '99999999-9999-9999-9999-999999999999';

UPDATE employee emp
SET
    name = fn.name || ' ' || ln.name,
    contact_phone = '9' || lpad((800000000 + f.rn)::text, 9, '0'),
    contact_email = lower(fn.name || '.' || ln.name) || '@jnv.demo'
FROM t_employee_fix f
JOIN t_first_name fn ON fn.idx = 1 + ((f.rn - 1) % 60)
JOIN t_last_name ln ON ln.idx = 1 + (((f.rn - 1) / 60) % 30)
WHERE emp.id = f.id;

UPDATE credential c
SET username = '9' || lpad((800000000 + f.rn)::text, 9, '0'), updated_at = CURRENT_TIMESTAMP
FROM t_employee_fix f
WHERE c.school_id = '99999999-9999-9999-9999-999999999999'
  AND c.owner_type = 'EMPLOYEE'
  AND c.owner_id = f.id;
