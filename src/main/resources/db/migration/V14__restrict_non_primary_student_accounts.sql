-- 历史数据也立即生效：包含英文字母的统一认证账号不是主修账号。
UPDATE `user`
SET `name` = '请使用主修账号进入',
    `updated_at` = CURRENT_TIMESTAMP
WHERE `cas_id` REGEXP '[A-Za-z]'
  AND `name` <> '请使用主修账号进入';
