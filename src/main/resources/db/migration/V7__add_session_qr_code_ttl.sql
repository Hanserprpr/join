ALTER TABLE department_interview_session
  ADD COLUMN qr_code_ttl_seconds INT NOT NULL DEFAULT 8
    COMMENT '签到二维码有效秒数'
    AFTER check_in_limit,
  ADD CONSTRAINT chk_interview_session_qr_code_ttl
    CHECK (qr_code_ttl_seconds BETWEEN 5 AND 86400);
