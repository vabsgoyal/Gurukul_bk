-- Optional school logo, shown on generated PDFs (report cards; ID cards later). Holds the S3 object
-- key (under school-logos/{schoolId}/), never a URL - a GET url is presigned fresh on every read,
-- same as chat attachments. Null means "no logo": PDFs fall back to the Gurukul placeholder mark.
ALTER TABLE school ADD COLUMN logo_object_key VARCHAR(512);
