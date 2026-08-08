-- Chat attachments (images/PDFs), uploaded straight from the client to S3 via a presigned PUT -
-- only the S3 object key is stored here, not a URL (see AttachmentService.presignDownload).
-- content becomes nullable: a message can now be attachment-only (no caption).
ALTER TABLE message ALTER COLUMN content DROP NOT NULL;
ALTER TABLE message ADD COLUMN attachment_object_key VARCHAR(1024);
ALTER TABLE message ADD COLUMN attachment_content_type VARCHAR(100);
ALTER TABLE message ADD COLUMN attachment_file_name VARCHAR(255);

ALTER TABLE message ADD CONSTRAINT chk_message_has_content_or_attachment
    CHECK (content IS NOT NULL OR attachment_object_key IS NOT NULL);
