-- 最新完整初始化脚本，仅用于新建数据库。
-- 执行后即可得到当前版本所需的全部表、索引、外键、角色和权限。

CREATE DATABASE IF NOT EXISTS `join`
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE `join`;

CREATE TABLE `user` (
  `cas_id`             VARCHAR(32)  NOT NULL COMMENT '统一认证账号（学号/工号）',
  `sub`                VARCHAR(128) NULL COMMENT 'OIDC subject',
  `name`               VARCHAR(64)  NOT NULL COMMENT '姓名',
  `email`              VARCHAR(128) NULL COMMENT '邮箱（暂不验证）',
  `phone`              VARCHAR(20)  NULL COMMENT '手机号（暂不验证）',
  `wechat_openid`      VARCHAR(64)  NULL COMMENT '微信公众号 OpenID',
  `profile_completed`  TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '必填资料是否完整',
  `qq`                 VARCHAR(20)  NULL COMMENT 'QQ号（选填）',
  `college`            VARCHAR(64)  NULL COMMENT '学院',
  `major`              VARCHAR(64)  NULL COMMENT '专业',
  `grade`              SMALLINT     NULL COMMENT '入学年级',
  `created_at`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
                                      ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`cas_id`),
  UNIQUE KEY `uk_sub` (`sub`),
  UNIQUE KEY `uk_email` (`email`),
  UNIQUE KEY `uk_wechat_openid` (`wechat_openid`),
  KEY `idx_college_major` (`college`, `major`),
  KEY `idx_grade` (`grade`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='用户表';

CREATE TABLE `board` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(64) NOT NULL,
  `sort_order` INT NOT NULL DEFAULT 0,
  `enabled` TINYINT(1) NOT NULL DEFAULT 1,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='板块';

CREATE TABLE `workstation` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `board_id` BIGINT NOT NULL,
  `name` VARCHAR(64) NOT NULL,
  `sort_order` INT NOT NULL DEFAULT 0,
  `enabled` TINYINT(1) NOT NULL DEFAULT 1,
  PRIMARY KEY (`id`),
  KEY `idx_workstation_board` (`board_id`),
  CONSTRAINT `fk_workstation_board`
    FOREIGN KEY (`board_id`) REFERENCES `board` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作站';

CREATE TABLE `department` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `workstation_id` BIGINT NOT NULL,
  `name` VARCHAR(64) NOT NULL,
  `campus` ENUM(
    'SOFTWARE_PARK', 'CENTRAL', 'QIANFOSHAN',
    'XINGLONGSHAN', 'HONGJIALOU', 'BAOTUQUAN'
  ) NULL COMMENT '部门所在校区',
  `introduction` TEXT NULL COMMENT '组织介绍',
  `recruitment_requirements` TEXT NULL COMMENT '纳新要求',
  `contact` VARCHAR(1000) NULL COMMENT '联系方式',
  `recruitment_group` VARCHAR(1000) NULL COMMENT '纳新群信息或链接',
  `pass_delay_count` INT NOT NULL DEFAULT 3 COMMENT '过号后顺延位数',
  `max_pass_count` INT NOT NULL DEFAULT 2 COMMENT '单人在本部门最大过号次数',
  `sort_order` INT NOT NULL DEFAULT 0,
  `enabled` TINYINT(1) NOT NULL DEFAULT 1,
  PRIMARY KEY (`id`),
  KEY `idx_department_workstation` (`workstation_id`),
  CONSTRAINT `fk_department_workstation`
    FOREIGN KEY (`workstation_id`) REFERENCES `workstation` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='部门';

CREATE TABLE `department_poster` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `department_id` BIGINT NOT NULL,
  `url` VARCHAR(2048) NOT NULL,
  `sort_order` INT NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_department_poster_department_sort`
    (`department_id`, `sort_order`, `id`),
  CONSTRAINT `fk_department_poster_department`
    FOREIGN KEY (`department_id`) REFERENCES `department` (`id`)
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='部门海报';

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

CREATE TABLE `department_question` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `department_id` BIGINT NOT NULL,
  `title` VARCHAR(200) NOT NULL,
  `description` VARCHAR(1000) NULL,
  `type` VARCHAR(32) NOT NULL COMMENT 'SINGLE_CHOICE/MULTIPLE_CHOICE/SHORT_TEXT/LONG_TEXT',
  `required` TINYINT(1) NOT NULL DEFAULT 0,
  `sort_order` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_department_question_department_sort`
    (`department_id`, `sort_order`, `id`),
  CONSTRAINT `fk_department_question_department`
    FOREIGN KEY (`department_id`) REFERENCES `department` (`id`)
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='部门报名问卷题目';

CREATE TABLE `department_question_option` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `question_id` BIGINT NOT NULL,
  `content` VARCHAR(200) NOT NULL,
  `sort_order` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_department_question_option_question_sort`
    (`question_id`, `sort_order`, `id`),
  CONSTRAINT `fk_department_question_option_question`
    FOREIGN KEY (`question_id`) REFERENCES `department_question` (`id`)
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='部门报名问卷选项';

CREATE TABLE `department_application` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `department_id` BIGINT NOT NULL,
  `cas_id` VARCHAR(32)
    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` VARCHAR(32) NOT NULL DEFAULT 'SUBMITTED',
  `submitted_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_department_application_user` (`department_id`, `cas_id`),
  KEY `idx_department_application_user` (`cas_id`),
  CONSTRAINT `fk_department_application_department`
    FOREIGN KEY (`department_id`) REFERENCES `department` (`id`),
  CONSTRAINT `fk_department_application_user`
    FOREIGN KEY (`cas_id`) REFERENCES `user` (`cas_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='部门报名';

CREATE TABLE `admission_email_outbox` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `application_id` BIGINT NOT NULL,
  `recipient` VARCHAR(128) NOT NULL,
  `subject` VARCHAR(255) NOT NULL,
  `content` TEXT NOT NULL,
  `status` VARCHAR(16) NOT NULL DEFAULT 'PENDING'
    COMMENT 'PENDING/PROCESSING/SENT/SKIPPED',
  `attempts` INT NOT NULL DEFAULT 0,
  `next_attempt_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `locked_at` DATETIME NULL,
  `last_error` VARCHAR(1000) NULL,
  `sent_at` DATETIME NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
    ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_admission_email_application` (`application_id`),
  KEY `idx_admission_email_dispatch` (`status`, `next_attempt_at`, `id`),
  CONSTRAINT `fk_admission_email_application`
    FOREIGN KEY (`application_id`) REFERENCES `department_application` (`id`),
  CONSTRAINT `chk_admission_email_status`
    CHECK (`status` IN ('PENDING', 'PROCESSING', 'SENT', 'SKIPPED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='录取邮件可靠投递队列';

CREATE TABLE `department_application_answer` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `application_id` BIGINT NOT NULL,
  `question_id` BIGINT NULL,
  `question_title` VARCHAR(200) NOT NULL COMMENT '提交时的题目快照',
  `question_type` VARCHAR(32) NOT NULL COMMENT '提交时的题型快照',
  `answer_text` TEXT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_application_answer_question`
    (`application_id`, `question_id`),
  CONSTRAINT `fk_application_answer_application`
    FOREIGN KEY (`application_id`) REFERENCES `department_application` (`id`)
    ON DELETE CASCADE,
  CONSTRAINT `fk_application_answer_question`
    FOREIGN KEY (`question_id`) REFERENCES `department_question` (`id`)
    ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='部门报名问卷答案';

CREATE TABLE `department_application_answer_option` (
  `answer_id` BIGINT NOT NULL,
  `option_id` BIGINT NULL,
  `option_content` VARCHAR(200) NOT NULL COMMENT '提交时的选项快照',
  KEY `idx_application_answer_option_answer` (`answer_id`),
  CONSTRAINT `fk_application_answer_option_answer`
    FOREIGN KEY (`answer_id`) REFERENCES `department_application_answer` (`id`)
    ON DELETE CASCADE,
  CONSTRAINT `fk_application_answer_option_option`
    FOREIGN KEY (`option_id`) REFERENCES `department_question_option` (`id`)
    ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报名答案所选选项';

CREATE TABLE `department_interview_session` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `department_id` BIGINT NOT NULL,
  `starts_at` DATETIME NOT NULL,
  `ends_at` DATETIME NOT NULL,
  `location` VARCHAR(255) NOT NULL,
  `check_in_limit` INT NOT NULL,
  `status` VARCHAR(16) NOT NULL DEFAULT 'DRAFT'
    COMMENT 'DRAFT/PUBLISHED/ENDED',
  `published_at` DATETIME NULL,
  `ended_at` DATETIME NULL,
  PRIMARY KEY (`id`),
  KEY `idx_interview_session_department_status`
    (`department_id`, `status`, `starts_at`),
  CONSTRAINT `fk_interview_session_department`
    FOREIGN KEY (`department_id`) REFERENCES `department` (`id`),
  CONSTRAINT `chk_interview_session_limit`
    CHECK (`check_in_limit` > 0),
  CONSTRAINT `chk_interview_session_time`
    CHECK (`ends_at` > `starts_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='部门面试场次';

CREATE TABLE `department_check_in` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `department_id` BIGINT NOT NULL,
  `session_id` BIGINT NOT NULL,
  `application_id` BIGINT NOT NULL,
  `cas_id` VARCHAR(32)
    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `checked_in_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `queue_number` INT NOT NULL COMMENT '部门内等待叫号序号',
  `queue_order` BIGINT NOT NULL COMMENT '当前队列排序位置',
  `pass_count` INT NOT NULL DEFAULT 0 COMMENT '在本部门累计过号次数',
  `priority` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否为顺延优先签到',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_department_check_in_session_application`
    (`session_id`, `application_id`),
  UNIQUE KEY `uk_department_check_in_queue`
    (`session_id`, `queue_number`),
  KEY `idx_department_check_in_department_time`
    (`department_id`, `checked_in_at`),
  KEY `idx_department_check_in_queue_order`
    (`department_id`, `queue_order`),
  KEY `idx_department_check_in_user` (`cas_id`),
  CONSTRAINT `fk_check_in_department`
    FOREIGN KEY (`department_id`) REFERENCES `department` (`id`),
  CONSTRAINT `fk_check_in_session`
    FOREIGN KEY (`session_id`) REFERENCES `department_interview_session` (`id`),
  CONSTRAINT `fk_check_in_application`
    FOREIGN KEY (`application_id`) REFERENCES `department_application` (`id`),
  CONSTRAINT `fk_check_in_user`
    FOREIGN KEY (`cas_id`) REFERENCES `user` (`cas_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='部门现场签到';

CREATE TABLE `department_check_in_sequence` (
  `session_id` BIGINT NOT NULL,
  `next_number` INT NOT NULL DEFAULT 1,
  PRIMARY KEY (`session_id`),
  CONSTRAINT `fk_check_in_sequence_session`
    FOREIGN KEY (`session_id`) REFERENCES `department_interview_session` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='部门签到叫号序列';

CREATE TABLE `department_interview_room` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `department_id` BIGINT NOT NULL,
  `session_id` BIGINT NOT NULL,
  `name` VARCHAR(64) NOT NULL,
  `status` VARCHAR(16) NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN/CLOSED',
  `created_by` VARCHAR(32)
    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_interview_room_session_name` (`session_id`, `name`),
  KEY `idx_interview_room_department_status` (`department_id`, `status`),
  CONSTRAINT `fk_interview_room_department`
    FOREIGN KEY (`department_id`) REFERENCES `department` (`id`),
  CONSTRAINT `fk_interview_room_session`
    FOREIGN KEY (`session_id`) REFERENCES `department_interview_session` (`id`),
  CONSTRAINT `fk_interview_room_creator`
    FOREIGN KEY (`created_by`) REFERENCES `user` (`cas_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='面试室';

CREATE TABLE `department_interview_room_member` (
  `room_id` BIGINT NOT NULL,
  `admin_cas_id` VARCHAR(32)
    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `joined_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`room_id`, `admin_cas_id`),
  KEY `idx_interview_room_member_admin` (`admin_cas_id`),
  CONSTRAINT `fk_interview_room_member_room`
    FOREIGN KEY (`room_id`) REFERENCES `department_interview_room` (`id`)
    ON DELETE CASCADE,
  CONSTRAINT `fk_interview_room_member_admin`
    FOREIGN KEY (`admin_cas_id`) REFERENCES `user` (`cas_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='面试室管理员成员';

CREATE TABLE `department_interview` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `department_id` BIGINT NOT NULL,
  `room_id` BIGINT NULL,
  `check_in_id` BIGINT NOT NULL,
  `application_id` BIGINT NOT NULL,
  `candidate_cas_id` VARCHAR(32)
    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `interviewer_cas_id` VARCHAR(32)
    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `queue_number` INT NOT NULL,
  `started_at` DATETIME NOT NULL,
  `ended_at` DATETIME NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_department_interview_check_in` (`check_in_id`),
  KEY `idx_department_interview_department` (`department_id`, `started_at`),
  KEY `idx_department_interview_candidate` (`candidate_cas_id`),
  KEY `idx_department_interview_interviewer` (`interviewer_cas_id`),
  KEY `idx_department_interview_room` (`room_id`, `started_at`),
  CONSTRAINT `fk_interview_department`
    FOREIGN KEY (`department_id`) REFERENCES `department` (`id`),
  CONSTRAINT `fk_interview_room`
    FOREIGN KEY (`room_id`) REFERENCES `department_interview_room` (`id`),
  CONSTRAINT `fk_interview_check_in`
    FOREIGN KEY (`check_in_id`) REFERENCES `department_check_in` (`id`),
  CONSTRAINT `fk_interview_application`
    FOREIGN KEY (`application_id`) REFERENCES `department_application` (`id`),
  CONSTRAINT `fk_interview_candidate`
    FOREIGN KEY (`candidate_cas_id`) REFERENCES `user` (`cas_id`),
  CONSTRAINT `fk_interview_interviewer`
    FOREIGN KEY (`interviewer_cas_id`) REFERENCES `user` (`cas_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='部门面试历史';

CREATE TABLE `department_interview_active` (
  `interview_id` BIGINT NOT NULL,
  `room_id` BIGINT NULL,
  `check_in_id` BIGINT NOT NULL,
  `candidate_cas_id` VARCHAR(32)
    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`interview_id`),
  UNIQUE KEY `uk_active_interview_check_in` (`check_in_id`),
  UNIQUE KEY `uk_active_interview_candidate` (`candidate_cas_id`),
  UNIQUE KEY `uk_active_interview_room` (`room_id`),
  CONSTRAINT `fk_active_interview`
    FOREIGN KEY (`interview_id`) REFERENCES `department_interview` (`id`)
    ON DELETE CASCADE,
  CONSTRAINT `fk_active_interview_room`
    FOREIGN KEY (`room_id`) REFERENCES `department_interview_room` (`id`),
  CONSTRAINT `fk_active_interview_check_in`
    FOREIGN KEY (`check_in_id`) REFERENCES `department_check_in` (`id`),
  CONSTRAINT `fk_active_interview_candidate`
    FOREIGN KEY (`candidate_cas_id`) REFERENCES `user` (`cas_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='当前进行中的面试占用';

CREATE TABLE `department_interview_evaluation` (
  `interview_id` BIGINT NOT NULL,
  `admin_cas_id` VARCHAR(32)
    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `score` TINYINT UNSIGNED NOT NULL,
  `evaluation` VARCHAR(2000) NULL,
  `submitted_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`interview_id`, `admin_cas_id`),
  CONSTRAINT `chk_interview_evaluation_score`
    CHECK (`score` BETWEEN 1 AND 5),
  CONSTRAINT `fk_interview_evaluation_interview`
    FOREIGN KEY (`interview_id`) REFERENCES `department_interview` (`id`)
    ON DELETE CASCADE,
  CONSTRAINT `fk_interview_evaluation_admin`
    FOREIGN KEY (`admin_cas_id`) REFERENCES `user` (`cas_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='管理员独立面试评价';

CREATE TABLE `department_interview_carryover` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `department_id` BIGINT NOT NULL,
  `application_id` BIGINT NOT NULL,
  `source_session_id` BIGINT NOT NULL,
  `target_session_id` BIGINT NULL,
  `status` VARCHAR(16) NOT NULL DEFAULT 'PENDING'
    COMMENT 'PENDING/USED/CANCELLED',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `used_at` DATETIME NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_carryover_source_application`
    (`source_session_id`, `application_id`),
  KEY `idx_carryover_pending`
    (`department_id`, `application_id`, `status`),
  CONSTRAINT `fk_carryover_department`
    FOREIGN KEY (`department_id`) REFERENCES `department` (`id`),
  CONSTRAINT `fk_carryover_application`
    FOREIGN KEY (`application_id`) REFERENCES `department_application` (`id`),
  CONSTRAINT `fk_carryover_source_session`
    FOREIGN KEY (`source_session_id`)
      REFERENCES `department_interview_session` (`id`),
  CONSTRAINT `fk_carryover_target_session`
    FOREIGN KEY (`target_session_id`)
      REFERENCES `department_interview_session` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='未叫到用户的顺延资格';

CREATE TABLE `role` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `code` VARCHAR(64) NOT NULL,
  `name` VARCHAR(64) NOT NULL,
  `description` VARCHAR(255) NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色';

CREATE TABLE `permission` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `code` VARCHAR(128) NOT NULL,
  `name` VARCHAR(128) NOT NULL,
  `description` VARCHAR(255) NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_permission_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限';

CREATE TABLE `role_permission` (
  `role_id` BIGINT NOT NULL,
  `permission_id` BIGINT NOT NULL,
  PRIMARY KEY (`role_id`, `permission_id`),
  CONSTRAINT `fk_role_permission_role`
    FOREIGN KEY (`role_id`) REFERENCES `role` (`id`),
  CONSTRAINT `fk_role_permission_permission`
    FOREIGN KEY (`permission_id`) REFERENCES `permission` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `user_role_scope` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `cas_id` VARCHAR(32)
    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `role_id` BIGINT NOT NULL,
  `scope_type` VARCHAR(32) NOT NULL COMMENT 'BOARD/WORKSTATION/DEPARTMENT/ALL',
  `scope_id` BIGINT NOT NULL COMMENT '组织ID；ALL使用0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_role_scope`
    (`cas_id`, `role_id`, `scope_type`, `scope_id`),
  KEY `idx_user_role_scope_user` (`cas_id`),
  KEY `idx_user_role_scope_target` (`scope_type`, `scope_id`),
  CONSTRAINT `fk_user_role_scope_user`
    FOREIGN KEY (`cas_id`) REFERENCES `user` (`cas_id`),
  CONSTRAINT `fk_user_role_scope_role`
    FOREIGN KEY (`role_id`) REFERENCES `role` (`id`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='用户角色及数据范围';

CREATE TABLE `banner` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `url` VARCHAR(2048) NOT NULL COMMENT '轮播图图片地址',
  `route` VARCHAR(255) NULL COMMENT '点击跳转路径，可空',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='首页轮播图';

INSERT INTO `role` (`code`, `name`) VALUES
  ('BOARD_ADMIN', '板块管理员'),
  ('WORKSTATION_ADMIN', '站长管理员'),
  ('DEPARTMENT_ADMIN', '部门管理员'),
  ('DEPARTMENT_ASSISTANT', '辅助管理员'),
  ('SYSTEM_ADMIN', '平台管理员');

INSERT INTO `permission` (`code`, `name`) VALUES
  ('recruitment:manage', '管理部门纳新信息及活动'),
  ('application:read', '查看报名信息'),
  ('application:export', '导出报名信息'),
  ('check-in:manage', '展示签到二维码及管理签到'),
  ('interview:manage', '创建及修改面试安排'),
  ('interview:evaluate', '进行面试考核'),
  ('admission:manage', '管理录取结果'),
  ('notification:manage', '发布纳新通知'),
  ('statistics:read', '查看纳新统计'),
  ('admin:assistant:assign', '任命部门辅助管理员'),
  ('system:user:manage', '管理平台用户'),
  ('system:organization:manage', '管理板块、工作站和部门'),
  ('system:config:manage', '管理系统配置'),
  ('admin:role:assign', '分配管理员角色');

INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT r.id, p.id
FROM `role` r
JOIN `permission` p
WHERE
  (r.code = 'BOARD_ADMIN' AND p.code IN (
    'recruitment:manage',
    'application:read', 'application:export',
    'check-in:manage',
    'interview:manage', 'interview:evaluate',
    'admission:manage', 'notification:manage', 'statistics:read'
  ))
  OR
  (r.code = 'WORKSTATION_ADMIN' AND p.code IN (
    'recruitment:manage',
    'application:read', 'application:export',
    'check-in:manage',
    'interview:manage', 'interview:evaluate',
    'admission:manage', 'notification:manage', 'statistics:read'
  ))
  OR
  (r.code = 'DEPARTMENT_ADMIN' AND p.code IN (
    'recruitment:manage',
    'application:read', 'application:export',
    'check-in:manage',
    'interview:manage', 'interview:evaluate',
    'admission:manage', 'notification:manage', 'statistics:read',
    'admin:assistant:assign'
  ))
  OR
  (r.code = 'DEPARTMENT_ASSISTANT' AND p.code IN (
    'application:read', 'check-in:manage',
    'interview:evaluate', 'statistics:read'
  ));
