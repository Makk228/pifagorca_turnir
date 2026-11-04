CREATE TABLE users (
 id VARCHAR(36) PRIMARY KEY, phone VARCHAR(16) NOT NULL UNIQUE, full_name VARCHAR(150) NOT NULL,
 password_hash VARCHAR(100) NOT NULL, role VARCHAR(10) NOT NULL CHECK(role IN ('PARENT','TEACHER','ADMIN')),
 enabled BOOLEAN NOT NULL DEFAULT TRUE, verified BOOLEAN NOT NULL DEFAULT FALSE, auth_version INT NOT NULL DEFAULT 0,
 sms_consent BOOLEAN NOT NULL, ranking_consent BOOLEAN NOT NULL, created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE TABLE consents (id VARCHAR(36) PRIMARY KEY,user_id VARCHAR(36) NOT NULL REFERENCES users(id),kind VARCHAR(30) NOT NULL, granted BOOLEAN NOT NULL, version VARCHAR(30) NOT NULL, at TIMESTAMP WITH TIME ZONE NOT NULL);
CREATE TABLE children (id VARCHAR(36) PRIMARY KEY,parent_id VARCHAR(36) NOT NULL REFERENCES users(id),last_name VARCHAR(80) NOT NULL,first_name VARCHAR(80) NOT NULL,middle_name VARCHAR(80) NOT NULL,birth_year INT NOT NULL,category VARCHAR(10) NOT NULL CHECK(category IN ('BABY','SCHOOL')));
CREATE TABLE marathons (id VARCHAR(36) PRIMARY KEY,title VARCHAR(150) NOT NULL,description VARCHAR(2000) NOT NULL,start_date DATE NOT NULL,days INT NOT NULL CHECK(days BETWEEN 1 AND 31),timezone VARCHAR(80) NOT NULL,status VARCHAR(20) NOT NULL,published BOOLEAN NOT NULL,join_code VARCHAR(16) NOT NULL UNIQUE,created_by VARCHAR(36) NOT NULL REFERENCES users(id));
CREATE TABLE marathon_teachers (marathon_id VARCHAR(36) REFERENCES marathons(id),user_id VARCHAR(36) REFERENCES users(id),PRIMARY KEY(marathon_id,user_id));
CREATE TABLE enrollments (marathon_id VARCHAR(36) REFERENCES marathons(id),child_id VARCHAR(36) REFERENCES children(id),PRIMARY KEY(marathon_id,child_id));
CREATE TABLE libraries (id VARCHAR(36) PRIMARY KEY,owner_id VARCHAR(36) NOT NULL REFERENCES users(id),name VARCHAR(120) NOT NULL,created_at TIMESTAMP WITH TIME ZONE NOT NULL);
CREATE TABLE library_examples (id VARCHAR(36) PRIMARY KEY,library_id VARCHAR(36) NOT NULL REFERENCES libraries(id),category VARCHAR(10) NOT NULL,topic VARCHAR(80) NOT NULL,level VARCHAR(10) NOT NULL,expression VARCHAR(200) NOT NULL,answer INT NOT NULL);
CREATE TABLE assignments (id VARCHAR(36) PRIMARY KEY,marathon_id VARCHAR(36) NOT NULL REFERENCES marathons(id),child_id VARCHAR(36) NOT NULL REFERENCES children(id),day INT NOT NULL,instruction VARCHAR(2000) NOT NULL,current_attempt VARCHAR(36),reopened_until TIMESTAMP WITH TIME ZONE,UNIQUE(marathon_id,child_id,day));
CREATE TABLE examples (id VARCHAR(36) PRIMARY KEY,assignment_id VARCHAR(36) NOT NULL REFERENCES assignments(id),position INT NOT NULL,expression VARCHAR(200) NOT NULL,answer INT NOT NULL,UNIQUE(assignment_id,position));
CREATE TABLE attempts (id VARCHAR(36) PRIMARY KEY,assignment_id VARCHAR(36) NOT NULL REFERENCES assignments(id),started_at TIMESTAMP WITH TIME ZONE NOT NULL,deadline TIMESTAMP WITH TIME ZONE NOT NULL,finished_at TIMESTAMP WITH TIME ZONE,status VARCHAR(20) NOT NULL,version INT NOT NULL DEFAULT 0,correct INT NOT NULL DEFAULT 0,errors INT NOT NULL DEFAULT 0,skipped INT NOT NULL DEFAULT 0);
ALTER TABLE assignments ADD CONSTRAINT current_attempt_fk FOREIGN KEY(current_attempt) REFERENCES attempts(id);
CREATE TABLE answers (attempt_id VARCHAR(36) REFERENCES attempts(id),example_id VARCHAR(36) REFERENCES examples(id),value INT,PRIMARY KEY(attempt_id,example_id));
CREATE TABLE reopen_events (id VARCHAR(36) PRIMARY KEY,assignment_id VARCHAR(36) NOT NULL REFERENCES assignments(id),previous_attempt VARCHAR(36),actor_id VARCHAR(36) NOT NULL REFERENCES users(id),reason VARCHAR(1000) NOT NULL,at TIMESTAMP WITH TIME ZONE NOT NULL);
CREATE TABLE tie_decisions (marathon_id VARCHAR(36) REFERENCES marathons(id),child_id VARCHAR(36) REFERENCES children(id),rank INT NOT NULL,score INT NOT NULL,reason VARCHAR(1000) NOT NULL,actor_id VARCHAR(36) NOT NULL REFERENCES users(id),at TIMESTAMP WITH TIME ZONE NOT NULL,PRIMARY KEY(marathon_id,child_id));
CREATE TABLE audit (id VARCHAR(36) PRIMARY KEY,actor_id VARCHAR(36),action VARCHAR(80) NOT NULL,entity_id VARCHAR(36),detail VARCHAR(2000) NOT NULL,at TIMESTAMP WITH TIME ZONE NOT NULL);
CREATE TABLE sms_outbox (id VARCHAR(36) PRIMARY KEY,user_id VARCHAR(36) NOT NULL REFERENCES users(id),event_key VARCHAR(180) NOT NULL UNIQUE,body VARCHAR(1000) NOT NULL,service BOOLEAN NOT NULL DEFAULT FALSE,status VARCHAR(20) NOT NULL,attempts INT NOT NULL DEFAULT 0,next_at TIMESTAMP WITH TIME ZONE NOT NULL,created_at TIMESTAMP WITH TIME ZONE NOT NULL,last_error VARCHAR(200));
CREATE TABLE otp (phone VARCHAR(16) NOT NULL,purpose VARCHAR(10) NOT NULL,code_hash VARCHAR(100) NOT NULL,expires_at TIMESTAMP WITH TIME ZONE NOT NULL,tries INT NOT NULL DEFAULT 0,PRIMARY KEY(phone,purpose));
CREATE TABLE rate_limits (bucket VARCHAR(180) PRIMARY KEY,hits INT NOT NULL,window_start TIMESTAMP WITH TIME ZONE NOT NULL);
CREATE INDEX assignments_marathon ON assignments(marathon_id);
CREATE INDEX attempts_active ON attempts(status,deadline);
CREATE INDEX children_parent ON children(parent_id);
CREATE INDEX examples_assignment ON examples(assignment_id);
CREATE INDEX library_source ON library_examples(library_id);
CREATE INDEX sms_due ON sms_outbox(status,next_at);
CREATE INDEX audit_time ON audit(at);
