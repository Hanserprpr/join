ALTER TABLE `department`
  ADD COLUMN `qr_check_in_enabled` TINYINT(1) NOT NULL DEFAULT 0
  COMMENT '是否要求通过动态二维码签到'
  AFTER `max_pass_count`;
