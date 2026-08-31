-- 平台用户管理列表默认按注册时间倒序翻页，
-- 没有索引时每页都要对整表 filesort。
ALTER TABLE `user`
  ADD KEY `idx_user_created_at` (`created_at`);
