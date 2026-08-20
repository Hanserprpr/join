# 权限校验

## 校验模型

每个受保护的组织管理操作同时检查两件事：

1. Permission：用户是否可以执行这个动作；
2. Scope：用户是否可以在目标板块、工作站或部门执行。

只有两项都通过，Controller 方法才会执行。

```java
@DepartmentPermission(PermissionCode.RECRUITMENT_MANAGE)
```

权限和范围由一条联合查询校验，必须来自同一条角色范围授权，不能把用户在
不同组织或不同角色上的权限与范围拼接使用。

第三个隐含条件是目标组织必须启用（`enabled = 1`）。停用的板块、工作站和部门
一律拒绝，不依赖各 Service 记得再查一次 `selectDepartmentById`。

## 动态组织 ID

`CheckOrgScope.id` 是 SpEL 表达式，每次方法调用时从实际参数解析：

```java
@DepartmentPermission(PermissionCode.RECRUITMENT_MANAGE)
public void update(Long departmentId) {}
```

也可以从 DTO 读取：

```java
@WorkstationPermission(
    value = PermissionCode.SYSTEM_ORGANIZATION_MANAGE,
    id = "#request.workstationId"
)
public void update(UpdateRequest request) {}
```

常用接口优先使用 `BoardPermission`、`WorkstationPermission` 和
`DepartmentPermission`。它们默认分别读取 `#boardId`、`#workstationId` 和
`#departmentId`，权限参数使用 `PermissionCode` 枚举，IDE 可自动补全。

只有需要动态指定组织类型时才使用通用注解：

```java
@CheckOrgScope(
    permission = PermissionCode.RECRUITMENT_MANAGE,
    type = OrgType.DEPARTMENT,
    id = "#departmentId"
)
```

项目必须使用 Maven 的 `-parameters` 编译参数；当前 Maven 编译输出已启用
`parameters`，因此可以使用参数名 SpEL。

## Scope 继承

| 用户范围 | 板块目标 | 工作站目标 | 部门目标 |
|---|---:|---:|---:|
| `ALL` | 是 | 是 | 是 |
| 同一 `BOARD` | 是 | 是 | 是 |
| 同一 `WORKSTATION` | 否 | 是 | 是 |
| 同一 `DEPARTMENT` | 否 | 否 | 是 |

以上三列都还要求目标组织本身 `enabled = 1`。

板块管理员通过工作站和部门的上级关系获得向下数据范围。站长管理员通过
`department.workstation_id` 获得其工作站下的部门范围。

## Sa-Token 角色与权限来源

`SaAuthorizationConfig` 实现 `StpInterface`：

- `getRoleList` 从 `user_role_scope → role` 读取角色；
- `getPermissionList` 从
  `user_role_scope → role_permission → permission` 读取权限；
- 管理员角色和权限不受个人资料完整度影响；
- 平台管理员（`SYSTEM_ADMIN`）获得 `*`。

`/api/user/permissions` 返回的 `permissions` 是**扁平、不带范围**的权限码集合，
只说明「这个人在某处有这项职责」。前端要按部门控制入口时请用 `managedDepartments`
或 `departmentAccess`；用扁平列表会导致跨部门的按钮点下去必然 403。

`departmentAccess` 里的 `*` 只会来自 `SYSTEM_ADMIN`。没有配置任何 `role_permission`
的角色会被整行剔除，不会被 `COALESCE(p.code, '*')` 兜底成全权限。

Controller 示例：

```java
@SaCheckLogin
public Result<?> getOrUpdateOwnProfile() {}

@SaCheckRole("SYSTEM_ADMIN")
public Result<?> systemAdminOnly() {}

@DepartmentPermission(PermissionCode.INTERVIEW_EVALUATE)
public Result<?> review(Long departmentId) {}
```

不能只使用 `@SaCheckRole` 或 `@SaCheckPermission` 保护带组织归属的数据，因为这只能
证明用户在某处拥有该职责，不能证明他可以访问当前目标组织。组织范围接口应使用
带 `permission` 的 `@CheckOrgScope`，不需要再叠加 `@SaCheckPermission`。

## 需求角色的数据范围

| 角色 | Scope | 可操作的数据 |
|---|---|---|
| 平台管理员 `SYSTEM_ADMIN` | `ALL/0` | 全平台用户、组织、配置及全部纳新数据 |
| 板块管理员 `BOARD_ADMIN` | `BOARD/{boardId}` | 管理本板块下所有工作站、部门的纳新业务 |
| 站长管理员 `WORKSTATION_ADMIN` | `WORKSTATION/{workstationId}` | 管理本站下所有部门的纳新业务 |
| 部门管理员 `DEPARTMENT_ADMIN` | `DEPARTMENT/{departmentId}` | 本部门的纳新信息、报名、面试、通知、录取和统计 |
| 辅助管理员 `DEPARTMENT_ASSISTANT` | `DEPARTMENT/{departmentId}` | 本部门纳新数据查看、统计查看和面试考核 |
| 学生用户 `USER` | 个人数据 | 本人的报名记录和通知 |

板块管理员和站长管理员拥有各自范围内的纳新信息、面试、录取及通知
修改权限；辅助管理员仍只有查看、统计和面试考核权限。Permission 与 Scope 必须
同时满足，例如站长管理员可以修改本站部门的面试安排，但不能修改其他工作站数据。

学生提交报名、查看或修改本人报名、查看本人通知不配置 Permission。此类接口检查：

1. 用户已登录；
2. 手机号、邮箱、学院、专业和入学年级均已填写，即 `profile_completed = 1`；
   QQ 为选填项，不参与资料完整度计算；该校验只放在报名等学生业务接口，
   不参与管理员角色和权限解析；
3. 查询或修改的报名、通知属于当前登录用户；
4. 修改报名时仍满足报名截止时间和业务状态要求。

## 长连接的权限复查

SSE 是长连接，`@DepartmentPermission` 只在建立连接那一刻校验一次。
面试队列和面试室两个订阅会每 60 秒复查一次订阅者权限，失去权限时下发
`business-error` 事件并断开，因此角色撤销最多 60 秒内生效。
候选人订阅自己的排队状态（`/my-events`）不复查，数据本来就属于订阅者。

复查与心跳（20 秒）刻意分开，并且同一个 `(学号, 权限, 部门)` 在一轮里只查一次
数据库：一位面试官通常同时订阅队列和多个面试室，去重后查询次数按"人数 × 部门"
而不是"连接数"增长。按订阅上限（每部门 50 条队列 + 200 条面试室）估算，
不去重且跟着心跳走的话，50 个部门满载就是约 625 qps；现在约 35 qps。

复查失败（例如数据库抖动）按失去权限处理并断开——前端重连会重新走完整鉴权，
比继续推送陈旧数据安全。

复查只能覆盖**权限撤销**，覆盖不了**登录态失效**：连接建立后服务端不再持有
Token，无法重新验证。登录态要靠 SSE 连接 30 分钟超时后前端重连时重新走完整鉴权。

## 登录态的实际有效期

两套登录态互相独立，排障时不要混淆：

| | 谁在用 | 过期规则 |
|---|---|---|
| Sa-Token（`satoken` cookie） | `@SaCheckLogin`、`@SaCheckRole`、全部 `StpUtil` | `timeout` 默认 **30 天绝对过期**，`active-timeout` 未配置（-1，不检查闲置），**不会因为使用而顺延** |
| HttpSession（`JOINSESSION` cookie） | 仅 `@AuthenticationPrincipal OidcUser`，即 `/api/auth/status`、`/api/auth/oidc` | 默认 30 分钟**闲置滑动**，会因为使用而顺延 |

真正拦 API 的是 Sa-Token 那一套。

登录态存储在 Redis（`sa-token-redis-template` 提供的 `SaTokenDaoForRedisTemplate`，
键前缀 `satoken:`），因此服务重启不掉线、多实例之间共享登录态。
该 DAO 通过 `@Autowired RedisConnectionFactory` 复用 `spring.data.redis` 配置，
没有额外配置项。代价是 Redis 成为登录的强依赖：Redis 不可用时全站无法通过鉴权。

序列化选用 `sa-token-jackson3`。Spring Boot 4 用的是 Jackson 3（`tools.jackson`），
不要改用 `sa-token-redis-jackson`，那个会把 Jackson 2 拖进来。

> 如果希望登录态变成"闲置 30 分钟失效、使用即顺延"，设置
> `sa-token.active-timeout: 1800`（`auto-renew` 默认 true 会自动续签），
> `timeout` 再按需要调短。当前保持默认的 30 天绝对过期。
