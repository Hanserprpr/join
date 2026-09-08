-- 关闭的面试室保留历史记录，但不再占用场次内的名称。
-- NULL 不参与唯一性冲突，允许同名面试室多次关闭后重新创建。
ALTER TABLE `department_interview_room`
  ADD COLUMN `open_name` VARCHAR(64)
    GENERATED ALWAYS AS (CASE WHEN `status` = 'OPEN' THEN `name` ELSE NULL END);

CREATE UNIQUE INDEX `uk_interview_room_session_open_name`
  ON `department_interview_room` (`session_id`, `open_name`);

ALTER TABLE `department_interview_room`
  DROP INDEX `uk_interview_room_session_name`;
