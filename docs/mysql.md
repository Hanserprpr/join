# MySQL 表结构

完整建表及初始化 SQL 见 `docs/schema.sql`。

## `user`

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `cas_id` | VARCHAR(32) | PK，非空 | 统一认证账号（学号/工号） |
| `sub` | VARCHAR(128) | UK，可空 | OIDC subject |
| `name` | VARCHAR(64) | 非空 | 姓名 |
| `email` | VARCHAR(128) | UK，可空 | 邮箱 |
| `phone` | VARCHAR(20) | 可空 | 手机号 |
| `profile_completed` | TINYINT(1) | 非空，默认 0 | 必填资料是否完整 |
| `qq` | VARCHAR(20) | 可空 | QQ 号，选填 |
| `college` | VARCHAR(64) | 可空 | 学院 |
| `major` | VARCHAR(64) | 可空 | 专业 |
| `grade` | SMALLINT | 可空 | 入学年级 |
| `created_at` | DATETIME | 非空，默认当前时间 | 创建时间 |
| `updated_at` | DATETIME | 非空，自动更新时间 | 更新时间 |

索引：

- 主键：`cas_id`
- 唯一索引：`sub`、`email`
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
| `introduction` | TEXT | 可空 | 组织介绍 |
| `achievements` | TEXT | 可空 | 部门成果 |
| `recruitment_requirements` | TEXT | 可空 | 纳新要求 |
| `contact` | VARCHAR(1000) | 可空 | 联系方式 |
| `recruitment_group` | VARCHAR(1000) | 可空 | 纳新群信息或链接 |
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
        └──< department_poster

user ──< user_role_scope >── role
                              └──< role_permission >── permission
```

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
