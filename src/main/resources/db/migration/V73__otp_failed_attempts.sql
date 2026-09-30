-- Wrong guesses per OTP code: the code is burned after a few, so a code can't be brute-forced
-- inside its expiry window.
ALTER TABLE otp_code ADD COLUMN failed_attempts INT NOT NULL DEFAULT 0;
