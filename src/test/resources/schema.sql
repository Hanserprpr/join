CREATE TABLE IF NOT EXISTS `user` (
  `sub`        VARCHAR(128)          DEFAULT NULL,
  `name`       VARCHAR(64)  NOT NULL,
  `cas_id`     VARCHAR(32)  NOT NULL,
  `email`      VARCHAR(128)          DEFAULT NULL,
  `phone`      VARCHAR(20)           DEFAULT NULL,
  `avatar_key` VARCHAR(255)          DEFAULT NULL,
  `wechat_openid` VARCHAR(64)        DEFAULT NULL,
  `profile_completed` BOOLEAN NOT NULL DEFAULT FALSE,
  `qq`         VARCHAR(20)           DEFAULT NULL,
  `college`    VARCHAR(64)           DEFAULT NULL,
  `campus` VARCHAR(32) DEFAULT NULL,
  `major`      VARCHAR(64)           DEFAULT NULL,
  `grade`      SMALLINT              DEFAULT NULL,
  `created_at` TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`cas_id`),
  UNIQUE (`sub`),
  UNIQUE (`email`)
  ,UNIQUE (`wechat_openid`)
);
