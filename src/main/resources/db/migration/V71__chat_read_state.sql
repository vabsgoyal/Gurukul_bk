-- Server-side read state for chat: each participant's "read up to" time, so unread counts are the
-- same on every device and survive a reinstall (the app used to keep this only on the phone).
-- Existing chats start fully read as of this migration, rather than every old message showing unread.
ALTER TABLE conversation_participant ADD COLUMN last_read_at TIMESTAMP;
UPDATE conversation_participant SET last_read_at = CURRENT_TIMESTAMP;
