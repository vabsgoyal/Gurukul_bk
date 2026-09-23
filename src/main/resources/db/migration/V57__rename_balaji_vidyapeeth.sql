-- Correct the school's spelling: "Vidyapeeth", not "Vidhyapeeth" (user-reported 2026-09-23). Keyed by
-- the production id; a no-op on any database without it. The app has no school-name edit field.
UPDATE school SET name = 'Balaji Vidyapeeth Unhel', bank_account_holder_name = CASE
        WHEN bank_account_holder_name = 'Balaji Vidhyapeeth Unhel' THEN 'Balaji Vidyapeeth Unhel'
        ELSE bank_account_holder_name END
WHERE id = '6c1cb8cd-6d2f-4095-842e-82c77ea68d2b';
