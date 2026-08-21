-- 部门素材 ID：指向外部素材系统的数字 ID，暂不与本库建立外键。
-- 存量部门先留空，后续补齐数据后再考虑收紧为 NOT NULL。
ALTER TABLE `department`
  ADD COLUMN `asset_id` BIGINT NULL COMMENT '部门素材 ID' AFTER `campus`;
