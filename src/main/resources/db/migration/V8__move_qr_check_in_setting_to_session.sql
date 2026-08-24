ALTER TABLE department_interview_session
  ADD COLUMN qr_check_in_enabled TINYINT(1) NOT NULL DEFAULT 0
    COMMENT '是否要求通过动态二维码签到'
    AFTER check_in_limit;

UPDATE department_interview_session s
JOIN department d ON d.id = s.department_id
SET s.qr_check_in_enabled = d.qr_check_in_enabled;

ALTER TABLE department
  DROP COLUMN qr_check_in_enabled;
