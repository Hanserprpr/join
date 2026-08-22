ALTER TABLE `department`
  ADD COLUMN `pass_mode` VARCHAR(16) NOT NULL DEFAULT 'DELAY'
  COMMENT '过号处理方式：DELAY/RECHECK_IN'
  AFTER `max_pass_count`;

ALTER TABLE `department_check_in`
  ADD COLUMN `requires_recheck_in` TINYINT(1) NOT NULL DEFAULT 0
  COMMENT '过号后是否需要重新签到'
  AFTER `priority`;
