ALTER TABLE children ADD COLUMN birth_date DATE;
ALTER TABLE children ADD CONSTRAINT children_birth_date_year_check
    CHECK (birth_date IS NULL OR EXTRACT(YEAR FROM birth_date) = birth_year);
