ALTER TABLE `user`
  ADD COLUMN `campus` VARCHAR(32) NULL COMMENT '学生所在校区，枚举编码；历史资料可空';
