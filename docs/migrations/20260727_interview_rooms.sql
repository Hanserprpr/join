CREATE TABLE `department_interview_room` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `department_id` BIGINT NOT NULL,
  `session_id` BIGINT NOT NULL,
  `name` VARCHAR(64) NOT NULL,
  `status` VARCHAR(16) NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN/CLOSED',
  `created_by` VARCHAR(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_interview_room_session_name` (`session_id`, `name`),
  KEY `idx_interview_room_department_status` (`department_id`, `status`),
  CONSTRAINT `fk_interview_room_department` FOREIGN KEY (`department_id`) REFERENCES `department` (`id`),
  CONSTRAINT `fk_interview_room_session` FOREIGN KEY (`session_id`) REFERENCES `department_interview_session` (`id`),
  CONSTRAINT `fk_interview_room_creator` FOREIGN KEY (`created_by`) REFERENCES `user` (`cas_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='面试室';

CREATE TABLE `department_interview_room_member` (
  `room_id` BIGINT NOT NULL,
  `admin_cas_id` VARCHAR(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `joined_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`room_id`, `admin_cas_id`),
  KEY `idx_interview_room_member_admin` (`admin_cas_id`),
  CONSTRAINT `fk_interview_room_member_room` FOREIGN KEY (`room_id`) REFERENCES `department_interview_room` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_interview_room_member_admin` FOREIGN KEY (`admin_cas_id`) REFERENCES `user` (`cas_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='面试室管理员成员';

ALTER TABLE `department_interview`
  ADD COLUMN `room_id` BIGINT NULL AFTER `department_id`,
  ADD KEY `idx_department_interview_room` (`room_id`, `started_at`),
  ADD CONSTRAINT `fk_interview_room` FOREIGN KEY (`room_id`) REFERENCES `department_interview_room` (`id`);

ALTER TABLE `department_interview_active`
  ADD COLUMN `room_id` BIGINT NULL AFTER `interview_id`,
  ADD UNIQUE KEY `uk_active_interview_room` (`room_id`),
  ADD CONSTRAINT `fk_active_interview_room` FOREIGN KEY (`room_id`) REFERENCES `department_interview_room` (`id`);

-- 执行迁移前必须先结束或停止所有旧的个人面试。
ALTER TABLE `department_interview_active`
  DROP FOREIGN KEY `fk_active_interview_interviewer`,
  DROP INDEX `uk_active_interview_interviewer`,
  DROP COLUMN `interviewer_cas_id`;

CREATE TABLE `department_interview_evaluation` (
  `interview_id` BIGINT NOT NULL,
  `admin_cas_id` VARCHAR(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `score` TINYINT UNSIGNED NOT NULL,
  `evaluation` VARCHAR(2000) NULL,
  `submitted_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`interview_id`, `admin_cas_id`),
  CONSTRAINT `chk_interview_evaluation_score` CHECK (`score` BETWEEN 1 AND 5),
  CONSTRAINT `fk_interview_evaluation_interview` FOREIGN KEY (`interview_id`) REFERENCES `department_interview` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_interview_evaluation_admin` FOREIGN KEY (`admin_cas_id`) REFERENCES `user` (`cas_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='管理员独立面试评价';
