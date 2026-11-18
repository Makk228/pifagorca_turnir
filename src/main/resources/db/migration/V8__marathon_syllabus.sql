ALTER TABLE marathons ADD COLUMN preschool_example_count INT NOT NULL DEFAULT 60;
ALTER TABLE marathons ADD COLUMN school_example_count INT NOT NULL DEFAULT 100;
ALTER TABLE marathons ADD COLUMN task_duration_minutes INT NOT NULL DEFAULT 30;
ALTER TABLE marathons ADD COLUMN syllabus_name VARCHAR(255);

CREATE TABLE marathon_syllabus_rules (
 marathon_id VARCHAR(36) NOT NULL REFERENCES marathons(id),
 age_band VARCHAR(12) NOT NULL,
 category VARCHAR(10) NOT NULL,
 competition_category VARCHAR(24) NOT NULL,
 position INT NOT NULL,
 digits INT NOT NULL,
 terms INT NOT NULL,
 operation VARCHAR(20) NOT NULL,
 example_count INT NOT NULL,
 PRIMARY KEY(marathon_id,age_band,category,competition_category,position)
);
CREATE INDEX marathon_syllabus_marathon ON marathon_syllabus_rules(marathon_id);
