ALTER TABLE `department_achievement`
  ADD COLUMN `image_urls` JSON NULL COMMENT '成果图片地址列表'
  AFTER `content`;
