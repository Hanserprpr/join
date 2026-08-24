-- 报名资料不再要求邮箱。重算历史用户的完整度，避免旧标记继续拦截报名。
UPDATE `user`
SET profile_completed = CASE
  WHEN phone IS NOT NULL AND TRIM(phone) <> ''
   AND college IS NOT NULL AND TRIM(college) <> ''
   AND major IS NOT NULL AND TRIM(major) <> ''
   AND grade IS NOT NULL
  THEN 1
  ELSE 0
END;
