-- The language each person last used SparkStock in (EN or ES), for the reminders sent to SparkyTC's bell.
ALTER TABLE users ADD COLUMN language TEXT NOT NULL DEFAULT 'EN';
