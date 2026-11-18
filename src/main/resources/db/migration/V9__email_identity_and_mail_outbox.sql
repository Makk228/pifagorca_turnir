ALTER TABLE users RENAME COLUMN phone TO email;
ALTER TABLE users ALTER COLUMN email VARCHAR(254);
ALTER TABLE users RENAME COLUMN sms_consent TO email_consent;

ALTER TABLE otp RENAME COLUMN phone TO email;
ALTER TABLE otp ALTER COLUMN email VARCHAR(254);

ALTER TABLE sms_outbox RENAME TO mail_outbox;
