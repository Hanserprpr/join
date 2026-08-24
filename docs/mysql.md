# MySQL 表结构

完整建表及初始化 SQL 见 `docs/schema.sql`。

数据库变更由 Flyway 管理，脚本放在 `src/main/resources/db/migration/`，
应用启动时自动执行：

- `V1__baseline_schema.sql` 是引入 Flyway 时的完整结构，只对全新库执行；
  已有库启动时会被 `baseline-on-migrate` 标记为已应用，只补跑 V1 之后的脚本。
- 之后每次改表新增一个 `V{n}__{描述}.sql`，脚本一旦提交就不要再修改内容
  （Flyway 会校验校验和）。
- `docs/migrations/` 下是引入 Flyway 之前的历史脚本，仅作存档，不再新增。

## `user`

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `cas_id` | VARCHAR(32) | PK，非空 | 统一认证账号（学号/工号） |
| `sub` | VARCHAR(128) | UK，可空 | OIDC subject |
| `name` | VARCHAR(64) | 非空 | 姓名 |
| `email` | VARCHAR(128) | UK，可空 | 邮箱 |
| `phone` | VARCHAR(20) | 可空 | 手机号 |
| `wechat_openid` | VARCHAR(64) | UK，可空 | 微信公众号 OpenID |
| `profile_completed` | TINYINT(1) | 非空，默认 0 | 手机号、学院、专业和年级是否完整（邮箱选填） |
| `qq` | VARCHAR(20) | 可空 | QQ 号，选填 |
| `college` | VARCHAR(64) | 可空 | 学院 |
| `major` | VARCHAR(64) | 可空 | 专业 |
| `grade` | SMALLINT | 可空 | 入学年级 |
| `created_at` | DATETIME | 非空，默认当前时间 | 创建时间 |
| `updated_at` | DATETIME | 非空，自动更新时间 | 更新时间 |

索引：

- 主键：`cas_id`
- 唯一索引：`sub`、`email`、`wechat_openid`
- 普通索引：`(college, major)`、`grade`

## `board`

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | BIGINT | PK，自增 | 板块 ID |
| `name` | VARCHAR(64) | 非空 | 板块名称 |
| `sort_order` | INT | 非空，默认 0 | 展示顺序 |
| `enabled` | TINYINT(1) | 非空，默认 1 | 是否启用 |

## `workstation`

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | BIGINT | PK，自增 | 工作站 ID |
| `board_id` | BIGINT | FK，非空 | 所属板块 |
| `name` | VARCHAR(64) | 非空 | 工作站名称 |
| `sort_order` | INT | 非空，默认 0 | 展示顺序 |
| `enabled` | TINYINT(1) | 非空，默认 1 | 是否启用 |

索引与外键：

- 索引：`board_id`
- 外键：`board_id → board.id`

## `department`

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | BIGINT | PK，自增 | 部门 ID |
| `workstation_id` | BIGINT | FK，非空 | 所属工作站 |
| `name` | VARCHAR(64) | 非空 | 部门名称 |
| `campus` | ENUM | 可空 | 部门所在校区 |
| `asset_id` | BIGINT | 可空 | 部门素材 ID，指向外部素材系统 |
| `introduction` | TEXT | 可空 | 组织介绍 |
| `recruitment_requirements` | TEXT | 可空 | 纳新要求 |
| `contact` | VARCHAR(1000) | 可空 | 联系方式 |
| `recruitment_group` | VARCHAR(1000) | 可空 | 纳新群信息或链接 |
| `pass_delay_count` | INT | 非空，默认 3 | 过号后顺延位数 |
| `max_pass_count` | INT | 非空，默认 2 | 单人在本部门最大过号次数 |
| `pass_mode` | VARCHAR(16) | 非空，默认 `DELAY` | 过号处理方式：顺延或重新签到 |
| `sort_order` | INT | 非空，默认 0 | 展示顺序 |
| `enabled` | TINYINT(1) | 非空，默认 1 | 是否启用 |

`campus` 可选值：

| 数据库存储值 | 接口中文值 |
|---|---|
| `SOFTWARE_PARK` | 软件园校区 |
| `CENTRAL` | 中心校区 |
| `QIANFOSHAN` | 千佛山校区 |
| `XINGLONGSHAN` | 兴隆山校区 |
| `HONGJIALOU` | 洪家楼校区 |
| `BAOTUQUAN` | 趵突泉校区 |

索引与外键：

- 索引：`workstation_id`
- 外键：`workstation_id → workstation.id`

## `department_poster`

一个部门可以关联多张海报，查询时按照 `sort_order`、`id` 升序排列

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | BIGINT | PK，自增 | 海报 ID |
| `department_id` | BIGINT | FK，非空 | 所属部门 |
| `url` | VARCHAR(2048) | 非空 | 海报地址 |
| `sort_order` | INT | 非空，默认 0 | 展示顺序 |
| `created_at` | DATETIME | 非空，默认当前时间 | 创建时间 |

索引与外键：

- 联合索引：`(department_id, sort_order, id)`
- 外键：`department_id → department.id`
- 部门删除时通过 `ON DELETE CASCADE` 自动删除所属海报

## `department_achievement`

一个部门可以关联多条成果，查询时按照 `sort_order`、`id` 升序排列。
`sort_order` 由接口请求数组的下标决定，不需要调用方单独传。

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | BIGINT | PK，自增 | 成果 ID |
| `department_id` | BIGINT | FK，非空 | 所属部门 |
| `title` | VARCHAR(200) | 非空 | 成果标题 |
| `content` | TEXT | 可空 | 成果内容 |
| `sort_order` | INT | 非空，默认 0 | 展示顺序 |

索引与外键：

- 联合索引：`(department_id, sort_order, id)`
- 外键：`department_id → department.id`
- 部门删除时通过 `ON DELETE CASCADE` 自动删除所属成果

## `department_question`

部门报名问卷题目，查询时按照 `sort_order`、`id` 升序排列

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | BIGINT | PK，自增 | 题目 ID |
| `department_id` | BIGINT | FK，非空 | 所属部门 |
| `title` | VARCHAR(200) | 非空 | 题干 |
| `description` | VARCHAR(1000) | 可空 | 题目说明 |
| `type` | VARCHAR(32) | 非空 | 题型 |
| `required` | TINYINT(1) | 非空，默认 0 | 是否必答 |
| `sort_order` | INT | 非空，默认 0 | 展示顺序 |

`type` 可选值：`SINGLE_CHOICE`、`MULTIPLE_CHOICE`、`SHORT_TEXT`、`LONG_TEXT`

索引与外键：

- 联合索引：`(department_id, sort_order, id)`
- 外键：`department_id → department.id`
- 部门删除时通过 `ON DELETE CASCADE` 自动删除所属题目

## `department_question_option`

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | BIGINT | PK，自增 | 选项 ID |
| `question_id` | BIGINT | FK，非空 | 所属题目 |
| `content` | VARCHAR(200) | 非空 | 选项内容 |
| `sort_order` | INT | 非空，默认 0 | 展示顺序 |

索引与外键：

- 联合索引：`(question_id, sort_order, id)`
- 外键：`question_id → department_question.id`
- 题目删除时通过 `ON DELETE CASCADE` 自动删除所属选项

## `department_application`

一个用户在同一部门只能有一条报名记录

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | BIGINT | PK，自增 | 报名 ID |
| `department_id` | BIGINT | FK，非空 | 报名部门 |
| `cas_id` | VARCHAR(32) | FK，非空 | 报名用户 |
| `status` | VARCHAR(32) | 非空，默认 `SUBMITTED` | 报名状态 |
| `submitted_at` | DATETIME | 非空，默认当前时间 | 提交时间 |

索引与外键：

- 唯一索引：`(department_id, cas_id)`
- 普通索引：`cas_id`
- 外键：`department_id → department.id`
- 外键：`cas_id → user.cas_id`

## `admission_email_outbox`

录取结果发布时与报名状态更新在同一事务内写入的邮件投递队列。后台任务领取
`PENDING` 记录发送，失败后按退避时间重试；本地显式关闭邮件时记录为 `SKIPPED`。

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | BIGINT | PK，自增 | 投递任务 ID |
| `application_id` | BIGINT | UK、FK、非空 | 对应报名记录 |
| `recipient` | VARCHAR(128) | 非空 | 收件地址快照 |
| `subject` | VARCHAR(255) | 非空 | 邮件标题快照 |
| `content` | TEXT | 非空 | 个性化正文快照 |
| `status` | VARCHAR(16) | 非空 | `PENDING/PROCESSING/SENT/SKIPPED` |
| `attempts` | INT | 非空，默认 0 | 已领取次数 |
| `next_attempt_at` | DATETIME | 非空 | 下次可领取时间 |
| `locked_at` | DATETIME | 可空 | 当前领取时间 |
| `last_error` | VARCHAR(1000) | 可空 | 最近一次失败摘要 |
| `sent_at` | DATETIME | 可空 | 发送完成时间 |

索引与外键：

- 唯一索引：`application_id`，保证同一录取结果不会重复建任务
- 调度索引：`(status, next_attempt_at, id)`
- 外键：`application_id → department_application.id`

## `department_application_answer`

提交时冗余保存题目快照，之后修改或删除问卷不影响历史答案

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | BIGINT | PK，自增 | 答案 ID |
| `application_id` | BIGINT | FK，非空 | 所属报名 |
| `question_id` | BIGINT | FK，可空 | 原题目，题目删除后置空 |
| `question_title` | VARCHAR(200) | 非空 | 提交时的题目快照 |
| `question_type` | VARCHAR(32) | 非空 | 提交时的题型快照 |
| `answer_text` | TEXT | 可空 | 文本题答案 |

索引与外键：

- 唯一索引：`(application_id, question_id)`
- 外键：`application_id → department_application.id`，`ON DELETE CASCADE`
- 外键：`question_id → department_question.id`，`ON DELETE SET NULL`

## `department_application_answer_option`

选择题所选选项，同样保存提交时的选项快照。此表没有主键

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `answer_id` | BIGINT | FK，非空 | 所属答案 |
| `option_id` | BIGINT | FK，可空 | 原选项，选项删除后置空 |
| `option_content` | VARCHAR(200) | 非空 | 提交时的选项快照 |

索引与外键：

- 普通索引：`answer_id`
- 外键：`answer_id → department_application_answer.id`，`ON DELETE CASCADE`
- 外键：`option_id → department_question_option.id`，`ON DELETE SET NULL`

## `department_interview_session`

部门面试场次，签到与叫号只在 `PUBLISHED` 场次上进行

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | BIGINT | PK，自增 | 场次 ID |
| `department_id` | BIGINT | FK，非空 | 所属部门 |
| `starts_at` | DATETIME | 非空 | 开始时间 |
| `ends_at` | DATETIME | 非空 | 结束时间 |
| `location` | VARCHAR(255) | 非空 | 面试地点 |
| `check_in_limit` | INT | 非空 | 本场次取号上限 |
| `qr_check_in_enabled` | TINYINT(1) | 非空，默认 `0` | 本场次是否要求通过动态二维码签到 |
| `qr_code_ttl_seconds` | INT | 非空，默认 `8` | 签到二维码有效秒数 |
| `status` | VARCHAR(16) | 非空，默认 `DRAFT` | 场次状态 |
| `published_at` | DATETIME | 可空 | 发布时间 |
| `ended_at` | DATETIME | 可空 | 结束时间 |

`status` 可选值：`DRAFT`、`PUBLISHED`、`ENDED`

索引、外键与检查约束：

- 联合索引：`(department_id, status, starts_at)`
- 外键：`department_id → department.id`
- 检查约束：`check_in_limit > 0`
- 检查约束：`qr_code_ttl_seconds` 在 `5` 到 `86400` 之间
- 检查约束：`ends_at > starts_at`

## `department_check_in`

现场签到并取号，`queue_number` 是展示给用户的号码，`queue_order` 是过号顺延后
实际使用的排序位置

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | BIGINT | PK，自增 | 签到 ID |
| `department_id` | BIGINT | FK，非空 | 所属部门 |
| `session_id` | BIGINT | FK，非空 | 所属面试场次 |
| `application_id` | BIGINT | FK，非空 | 对应报名记录 |
| `cas_id` | VARCHAR(32) | FK，非空 | 签到用户 |
| `checked_in_at` | DATETIME | 非空，默认当前时间 | 签到时间 |
| `queue_number` | INT | 非空 | 部门内等待叫号序号 |
| `queue_order` | BIGINT | 非空 | 当前队列排序位置 |
| `pass_count` | INT | 非空，默认 0 | 在本部门累计过号次数 |
| `priority` | TINYINT(1) | 非空，默认 0 | 是否为顺延优先签到 |
| `requires_recheck_in` | TINYINT(1) | 非空，默认 0 | 是否因过号等待重新签到 |

索引与外键：

- 唯一索引：`(session_id, application_id)`、`(session_id, queue_number)`
- 联合索引：`(department_id, checked_in_at)`、`(department_id, queue_order)`
- 普通索引：`cas_id`
- 外键：`department_id → department.id`
- 外键：`session_id → department_interview_session.id`
- 外键：`application_id → department_application.id`
- 外键：`cas_id → user.cas_id`

## `department_check_in_sequence`

按场次发号，取号时对本表加行锁保证 `queue_number` 连续且不重复

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `session_id` | BIGINT | PK，FK | 所属面试场次 |
| `next_number` | INT | 非空，默认 1 | 下一个可用号码 |

索引与外键：

- 主键：`session_id`
- 外键：`session_id → department_interview_session.id`

## `department_interview`

面试历史。`ended_at` 为空表示面试进行中；过号或停止叫号会删除对应记录

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | BIGINT | PK，自增 | 面试 ID |
| `department_id` | BIGINT | FK，非空 | 所属部门 |
| `check_in_id` | BIGINT | UK，FK，非空 | 对应签到记录 |
| `application_id` | BIGINT | FK，非空 | 对应报名记录 |
| `candidate_cas_id` | VARCHAR(32) | FK，非空 | 面试者 |
| `interviewer_cas_id` | VARCHAR(32) | FK，非空 | 面试官 |
| `queue_number` | INT | 非空 | 叫号序号 |
| `started_at` | DATETIME | 非空 | 开始时间 |
| `ended_at` | DATETIME | 可空 | 结束时间 |
| `score` | TINYINT UNSIGNED | 可空 | 面试评分，1-5 分 |
| `evaluation` | VARCHAR(2000) | 可空 | 面试评价 |

索引、外键与检查约束：

- 唯一索引：`check_in_id`
- 联合索引：`(department_id, started_at)`
- 普通索引：`candidate_cas_id`、`interviewer_cas_id`
- 外键：`department_id → department.id`
- 外键：`check_in_id → department_check_in.id`
- 外键：`application_id → department_application.id`
- 外键：`candidate_cas_id → user.cas_id`
- 外键：`interviewer_cas_id → user.cas_id`
- 检查约束：`score IS NULL OR score BETWEEN 1 AND 5`

## `department_interview_active`

进行中的面试占用表。三个唯一索引保证同一签到、同一面试官、同一面试者同时
只能出现在一场面试中

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `interview_id` | BIGINT | PK，FK | 对应面试记录 |
| `check_in_id` | BIGINT | UK，FK，非空 | 对应签到记录 |
| `interviewer_cas_id` | VARCHAR(32) | UK，FK，非空 | 面试官 |
| `candidate_cas_id` | VARCHAR(32) | UK，FK，非空 | 面试者 |

索引与外键：

- 主键：`interview_id`
- 唯一索引：`check_in_id`、`interviewer_cas_id`、`candidate_cas_id`
- 外键：`interview_id → department_interview.id`，`ON DELETE CASCADE`
- 外键：`check_in_id → department_check_in.id`
- 外键：`interviewer_cas_id → user.cas_id`
- 外键：`candidate_cas_id → user.cas_id`

## `department_interview_carryover`

场次结束时为未叫到的用户生成顺延资格，下一场次发布时绑定。仅当当前场次启用
二维码签到且用户扫码签到时使用该资格，并将 `department_check_in.priority`
置为 1；普通签到模式不使用顺延优先资格，按本场签到顺序排队。

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | BIGINT | PK，自增 | 顺延资格 ID |
| `department_id` | BIGINT | FK，非空 | 所属部门 |
| `application_id` | BIGINT | FK，非空 | 对应报名记录 |
| `source_session_id` | BIGINT | FK，非空 | 产生顺延的场次 |
| `target_session_id` | BIGINT | FK，可空 | 绑定的目标场次 |
| `status` | VARCHAR(16) | 非空，默认 `PENDING` | 顺延状态 |
| `created_at` | DATETIME | 非空，默认当前时间 | 创建时间 |
| `used_at` | DATETIME | 可空 | 使用时间 |

`status` 可选值：`PENDING`、`USED`、`CANCELLED`

索引与外键：

- 唯一索引：`(source_session_id, application_id)`
- 联合索引：`(department_id, application_id, status)`
- 外键：`department_id → department.id`
- 外键：`application_id → department_application.id`
- 外键：`source_session_id → department_interview_session.id`
- 外键：`target_session_id → department_interview_session.id`

## `role`

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | BIGINT | PK，自增 | 角色 ID |
| `code` | VARCHAR(64) | UK，非空 | 角色编码 |
| `name` | VARCHAR(64) | 非空 | 角色名称 |
| `description` | VARCHAR(255) | 可空 | 角色说明 |

## `permission`

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | BIGINT | PK，自增 | 权限 ID |
| `code` | VARCHAR(128) | UK，非空 | 权限编码 |
| `name` | VARCHAR(128) | 非空 | 权限名称 |
| `description` | VARCHAR(255) | 可空 | 权限说明 |

## `role_permission`

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `role_id` | BIGINT | 联合 PK，FK | 角色 ID |
| `permission_id` | BIGINT | 联合 PK，FK | 权限 ID |

主键与外键：

- 联合主键：`(role_id, permission_id)`
- 外键：`role_id → role.id`
- 外键：`permission_id → permission.id`

## `user_role_scope`

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | BIGINT | PK，自增 | 用户角色范围记录 ID |
| `cas_id` | VARCHAR(32) | FK，非空 | 用户统一认证账号 |
| `role_id` | BIGINT | FK，非空 | 角色 ID |
| `scope_type` | VARCHAR(32) | 非空 | `BOARD`、`WORKSTATION`、`DEPARTMENT` 或 `ALL` |
| `scope_id` | BIGINT | 非空 | 组织 ID；`ALL` 使用 0 |

索引与外键：

- 唯一索引：`(cas_id, role_id, scope_type, scope_id)`
- 普通索引：`cas_id`、`(scope_type, scope_id)`
- 外键：`cas_id → user.cas_id`
- 外键：`role_id → role.id`

## 表关系

```text
board
└── workstation
    └── department
        ├──< department_poster
        ├──< department_question
        │       └──< department_question_option
        ├──< department_application
        │       ├─── admission_email_outbox
        │       └──< department_application_answer
        │               └──< department_application_answer_option
        └──< department_interview_session
                ├─── department_check_in_sequence
                └──< department_check_in
                        └──< department_interview
                                └─── department_interview_active

department_interview_session ──< department_interview_carryover

user ──< user_role_scope >── role
                              └──< role_permission >── permission
```

`department_application`、`department_check_in`、`department_interview` 和
`department_interview_carryover` 同时通过 `department_id` 直接关联部门，便于按
部门维度查询。

## 初始化角色

| 角色编码 | 名称 |
|---|---|
| `BOARD_ADMIN` | 板块管理员 |
| `WORKSTATION_ADMIN` | 站长管理员 |
| `DEPARTMENT_ADMIN` | 部门管理员 |
| `DEPARTMENT_ASSISTANT` | 辅助管理员 |
| `SYSTEM_ADMIN` | 平台管理员 |

## 初始化权限

| 权限编码 | 名称 |
|---|---|
| `recruitment:manage` | 管理部门纳新信息及活动 |
| `application:read` | 查看报名信息 |
| `application:export` | 导出报名信息 |
| `check-in:manage` | 展示签到二维码及管理签到 |
| `interview:manage` | 创建及修改面试安排 |
| `interview:evaluate` | 进行面试考核 |
| `admission:manage` | 管理录取结果 |
| `notification:manage` | 发布纳新通知 |
| `statistics:read` | 查看纳新统计 |
| `admin:assistant:assign` | 任命部门辅助管理员 |
| `system:user:manage` | 管理平台用户 |
| `system:organization:manage` | 管理板块、工作站和部门 |
| `system:config:manage` | 管理系统配置 |
| `admin:role:assign` | 分配管理员角色 |

`SYSTEM_ADMIN` 不在 `role_permission` 中配置权限，`SaAuthorizationConfig` 直接
为其返回 `*`。
