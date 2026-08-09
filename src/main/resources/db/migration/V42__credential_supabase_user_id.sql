-- Links a Credential to the Supabase auth.users row that verified it (real phone-OTP login via
-- Supabase, replacing the dummy-OTP path when app.auth.supabase.enabled=true). Nullable/unique:
-- most existing credentials predate any Supabase login and get linked lazily on first use.
ALTER TABLE credential ADD COLUMN supabase_user_id UUID;
ALTER TABLE credential ADD CONSTRAINT uq_credential_supabase_user_id UNIQUE (supabase_user_id);
