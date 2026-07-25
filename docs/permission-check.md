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

板块管理员通过工作站和部门的上级关系获得向下数据范围。站长管理员通过
`department.workstation_id` 获得其工作站下的部门范围。

## Sa-Token 角色与权限来源

`SaAuthorizationConfig` 实现 `StpInterface`：

- `getRoleList` 从 `user_role_scope → role` 读取角色；
- `getPermissionList` 从
  `user_role_scope → role_permission → permission` 读取权限；
- 管理员角色和权限不受个人资料完整度影响；
- 平台管理员（`SYSTEM_ADMIN`）获得 `*`。

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
