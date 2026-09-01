ALTER TABLE `department`
  MODIFY COLUMN `campus` VARCHAR(255) NULL
    COMMENT '部门所在校区（可多个，逗号分隔，取值同原 ENUM）';
