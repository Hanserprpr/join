ALTER TABLE department_interview_session
  ADD COLUMN name VARCHAR(64) NULL AFTER department_id;

UPDATE department_interview_session
SET name = CONCAT('面试场次 ', id)
WHERE name IS NULL;

ALTER TABLE department_interview_session
  MODIFY COLUMN name VARCHAR(64) NOT NULL COMMENT '场次名称';
