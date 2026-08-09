CREATE TABLE IF NOT EXISTS `admission_email_outbox` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `application_id` BIGINT NOT NULL,
  `recipient` VARCHAR(128) NOT NULL,
  `subject` VARCHAR(255) NOT NULL,
  `content` TEXT NOT NULL,
  `status` VARCHAR(16) NOT NULL DEFAULT 'PENDING'
    COMMENT 'PENDING/PROCESSING/SENT/SKIPPED',
  `attempts` INT NOT NULL DEFAULT 0,
  `next_attempt_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `locked_at` DATETIME NULL,
  `last_error` VARCHAR(1000) NULL,
  `sent_at` DATETIME NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
    ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_admission_email_application` (`application_id`),
  KEY `idx_admission_email_dispatch` (`status`, `next_attempt_at`, `id`),
  CONSTRAINT `fk_admission_email_application`
    FOREIGN KEY (`application_id`) REFERENCES `department_application` (`id`),
  CONSTRAINT `chk_admission_email_status`
    CHECK (`status` IN ('PENDING', 'PROCESSING', 'SENT', 'SKIPPED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='录取邮件可靠投递队列';
