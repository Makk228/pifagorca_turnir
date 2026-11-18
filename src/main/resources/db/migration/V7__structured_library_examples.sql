ALTER TABLE library_examples ADD COLUMN digits INT;
ALTER TABLE library_examples ADD COLUMN terms INT;
ALTER TABLE library_examples ADD COLUMN operation VARCHAR(20);
CREATE INDEX library_recipe ON library_examples(library_id,category,digits,terms,operation);
