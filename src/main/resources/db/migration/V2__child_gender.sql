ALTER TABLE children ADD COLUMN gender VARCHAR(6) CHECK (gender IN ('MALE', 'FEMALE'));
