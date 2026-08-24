ALTER TABLE `user`
  ADD COLUMN `avatar_key` VARCHAR(255) NULL COMMENT '头像存储对象 key' AFTER `phone`;
