-- Optional RTE/regulatory fields for student onboarding from a school's existing paper register
-- (e.g. Madhya Pradesh SSSM/Samagra ID, Aadhaar, caste/category and income for RTE quota tracking,
-- and the bank account used for RTE fee reimbursement). None of these are required for enrollment.
-- aadhaar_number and bank_account_number are stored AES-GCM encrypted (see TokenCipher) - sized
-- generously since ciphertext (IV + tag + base64 inflation) is longer than the plaintext value.
ALTER TABLE student ADD COLUMN sssm_id VARCHAR(50);
ALTER TABLE student ADD COLUMN aadhaar_number_encrypted VARCHAR(255);
ALTER TABLE student ADD COLUMN caste VARCHAR(100);
ALTER TABLE student ADD COLUMN category VARCHAR(50);
ALTER TABLE student ADD COLUMN annual_income BIGINT;
ALTER TABLE student ADD COLUMN previous_school_name VARCHAR(255);
ALTER TABLE student ADD COLUMN bank_account_number_encrypted VARCHAR(255);
ALTER TABLE student ADD COLUMN bank_ifsc VARCHAR(20);
