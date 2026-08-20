-- 部门成果由 department.achievements 单个 TEXT 字段改为结构化的
-- [{title, content}] 列表，独立成表存储，与 department_poster 保持一致。
CREATE TABLE `department_achievement` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `department_id` BIGINT NOT NULL,
  `title` VARCHAR(200) NOT NULL COMMENT '成果标题',
  `content` TEXT NULL COMMENT '成果内容',
  `sort_order` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_department_achievement_department_sort`
    (`department_id`, `sort_order`, `id`),
  CONSTRAINT `fk_department_achievement_department`
    FOREIGN KEY (`department_id`) REFERENCES `department` (`id`)
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='部门成果';

-- 历史数据：原本整段文本没有标题，统一迁移为一条标题为「部门成果」的记录。
INSERT INTO `department_achievement`
    (`department_id`, `title`, `content`, `sort_order`)
SELECT `id`, '部门成果', `achievements`, 0
FROM `department`
WHERE `achievements` IS NOT NULL
  AND TRIM(`achievements`) <> '';

ALTER TABLE `department` DROP COLUMN `achievements`;
