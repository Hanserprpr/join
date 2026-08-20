# 接口权限对照(按身份 code)

> 本文档以当前代码(Controller 上的权限注解)为准,整理各身份可访问的接口。
> 校验模型与 Scope 继承规则见 [permission-check.md](./permission-check.md)。
> 注意:OpenAPI 导出文件可能与当前代码不一致(导出自旧构建时尤其如此),排障时以本文档与代码为准。

## 身份总览

| 身份 code | 名称 | 数据范围 Scope | 权限来源 |
|---|---|---|---|
| `SYSTEM_ADMIN` | 平台管理员 | `ALL`(全平台) | 权限映射为 `*`,硬编码 `@SaCheckRole("SYSTEM_ADMIN")` |
| `BOARD_ADMIN` | 板块管理员 | `BOARD/{boardId}` 下所有部门 | `role_permission` 关联 |
| `WORKSTATION_ADMIN` | 站长管理员 | `WORKSTATION/{workstationId}` 下所有部门 | `role_permission` 关联 |
| `DEPARTMENT_ADMIN` | 部门管理员 | `DEPARTMENT/{departmentId}` | `role_permission` 关联 |
| `DEPARTMENT_ASSISTANT` | 辅助管理员 | `DEPARTMENT/{departmentId}` | `role_permission` 关联 |
| `USER` | 学生用户(基础角色) | 个人数据 | 代码中追加,无组织权限 |

除公开接口外,所有接口都要求已登录(`@SaCheckLogin` 或类级);带组织归属的接口
额外要求"权限 + 范围"同时满足(`@DepartmentPermission`)。

---

## `SYSTEM_ADMIN` 平台管理员

拥有全部权限(`*`),可访问所有接口,另含组织管理与角色分配:

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/admin/boards` | 创建板块 |
| POST | `/api/admin/workstations` | 创建工作站 |
| POST | `/api/admin/departments` | 创建部门 |
| POST | `/api/admin/role-assignments` | 分配角色(所有可分配角色) |
| DELETE | `/api/admin/role-assignments` | 撤销角色 |

> 角色分配接口本身只要求登录,实际按"操作者持有**更高级**身份且范围覆盖目标组织"在
> Service 层校验(等级:BOARD_ADMIN 40 > WORKSTATION_ADMIN 30 > DEPARTMENT_ADMIN 20
> > DEPARTMENT_ASSISTANT 10),`SYSTEM_ADMIN` 等级最高、范围全平台。

---

## `BOARD_ADMIN` 板块管理员 / `WORKSTATION_ADMIN` 站长管理员

两个身份权限集相同(板块管理员范围按板块继承,站长管理员按工作站继承)。

拥有的权限码:
`recruitment:manage`、`application:read`、`application:export`、`check-in:manage`、
`interview:manage`、`interview:evaluate`、`admission:manage`、`notification:manage`、
`statistics:read`

可访问接口(除登录即可的通用接口外):

### recruitment:manage — 部门纳新信息及活动

| 方法 | 路径 | 说明 |
|---|---|---|
| PUT | `/api/departments/{departmentId}` | 完整更新部门详情 |
| PATCH | `/api/departments/{departmentId}` | 部分更新部门详情 |
| PUT | `/api/departments/{departmentId}/questionnaire` | 完整更新报名问卷 |
| DELETE | `/api/departments/{departmentId}/questionnaire` | 清空报名问卷 |

### application:read / application:export — 报名信息

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| GET | `/api/departments/{departmentId}/applications` | 分页查询报名 | `application:read` |
| GET | `/api/departments/{departmentId}/applications/{applicationId}` | 报名详情 | `application:read` |
| GET | `/api/departments/{departmentId}/applications/export` | 导出报名 | `application:export` |

### check-in:manage — 签到

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/departments/{departmentId}/check-in/qr-code` | 生成部门签到二维码 |

### interview:manage — 面试安排

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/departments/{departmentId}/interviews/sessions` | 创建面试场次 |
| PUT | `/api/departments/{departmentId}/interviews/sessions/{sessionId}` | 修改面试场次 |
| POST | `/api/departments/{departmentId}/interviews/sessions/{sessionId}/publish` | 发布面试场次 |
| POST | `/api/departments/{departmentId}/interviews/sessions/{sessionId}/end` | 结束面试场次 |
| GET | `/api/departments/{departmentId}/interviews/sessions` | 查询全部场次 |
| PUT | `/api/departments/{departmentId}/interviews/queue-config` | 完整更新过号配置 |
| PATCH | `/api/departments/{departmentId}/interviews/queue-config` | 部分更新过号配置 |
| POST | `/api/departments/{departmentId}/interview-rooms` | 创建面试室 |
| POST | `/api/departments/{departmentId}/interview-rooms/{roomId}/close` | 关闭面试室 |

### interview:evaluate — 面试考核(含面试室)

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/departments/{departmentId}/interviews/queue-config` | 查询过号配置 |
| GET | `/api/departments/{departmentId}/interviews/queue` | 查询面试队列 |
| GET | `/api/departments/{departmentId}/interviews/events` | SSE 订阅队列事件 |
| GET | `/api/departments/{departmentId}/interviews/{interviewId}` | 查询面试记录 |
| PUT | `/api/departments/{departmentId}/interviews/{interviewId}/evaluation` | 更新面试评价 |
| GET | `/api/departments/{departmentId}/interview-rooms` | 面试室列表 |
| POST | `/api/departments/{departmentId}/interview-rooms/{roomId}/join` | 加入面试室 |
| POST | `/api/departments/{departmentId}/interview-rooms/{roomId}/leave` | 离开面试室 |
| POST | `/api/departments/{departmentId}/interview-rooms/{roomId}/call-next` | 呼叫下一位 |
| POST | `/api/departments/{departmentId}/interview-rooms/{roomId}/force-call-next` | 强制呼叫下一位 |
| GET | `/api/departments/{departmentId}/interview-rooms/{roomId}/current` | 当前面试 |
| POST | `/api/departments/{departmentId}/interview-rooms/{roomId}/current/finish` | 结束当前面试 |
| POST | `/api/departments/{departmentId}/interview-rooms/{roomId}/current/force-finish` | 强制结束当前面试 |
| POST | `/api/departments/{departmentId}/interview-rooms/{roomId}/current/evaluation` | 提交面试评价 |
| POST | `/api/departments/{departmentId}/interview-rooms/{roomId}/current/pass` | 标记过号 |
| POST | `/api/departments/{departmentId}/interview-rooms/{roomId}/current/stop-calling` | 停止叫号 |
| GET | `/api/departments/{departmentId}/interview-rooms/{roomId}/state` | 房间状态 |
| GET | `/api/departments/{departmentId}/interview-rooms/{roomId}/interviews/{interviewId}/evaluations` | 面试评价列表 |
| GET | `/api/departments/{departmentId}/interview-rooms/{roomId}/events` | SSE 订阅房间事件 |

> `admission:manage`、`notification:manage`、`statistics:read` 已授予本身份,但当前
> 还没有对应的接口(见文末"未接线权限"章节)。

---

## `DEPARTMENT_ADMIN` 部门管理员

权限码:在板块/站长管理员的基础上,额外拥有 `admin:assistant:assign`(任命辅助管理员)。

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| (全部板块/站长管理员接口) | 同上 | 同上 | 同上 |
| — | `admin:assistant:assign` 任命辅助管理员 | **已授权,暂无接口** | `admin:assistant:assign` |

数据范围:仅本部门(`DEPARTMENT/{departmentId}`)。

---

## `DEPARTMENT_ASSISTANT` 辅助管理员

权限码:`application:read`、`check-in:manage`、`interview:evaluate`、`statistics:read`。
只有查看/签到/面试考核权限,**没有**纳新信息、报名导出、面试安排等修改权限。

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| GET | `/api/departments/{departmentId}/applications` | 分页查询报名 | `application:read` |
| GET | `/api/departments/{departmentId}/applications/{applicationId}` | 报名详情 | `application:read` |
| GET | `/api/departments/{departmentId}/check-in/qr-code` | 生成签到二维码 | `check-in:manage` |
| GET | `/api/departments/{departmentId}/interviews/queue-config` | 查询过号配置 | `interview:evaluate` |
| GET | `/api/departments/{departmentId}/interviews/queue` | 查询面试队列 | `interview:evaluate` |
| GET | `/api/departments/{departmentId}/interviews/events` | SSE 订阅队列事件 | `interview:evaluate` |
| GET | `/api/departments/{departmentId}/interviews/{interviewId}` | 查询面试记录 | `interview:evaluate` |
| PUT | `/api/departments/{departmentId}/interviews/{interviewId}/evaluation` | 更新面试评价 | `interview:evaluate` |
| GET | `/api/departments/{departmentId}/interview-rooms` | 面试室列表 | `interview:evaluate` |
| POST | `/api/departments/{departmentId}/interview-rooms/{roomId}/join` | 加入面试室 | `interview:evaluate` |
| POST | `/api/departments/{departmentId}/interview-rooms/{roomId}/leave` | 离开面试室 | `interview:evaluate` |
| POST | `/api/departments/{departmentId}/interview-rooms/{roomId}/call-next` | 呼叫下一位 | `interview:evaluate` |
| POST | `/api/departments/{departmentId}/interview-rooms/{roomId}/force-call-next` | 强制呼叫下一位 | `interview:evaluate` |
| GET | `/api/departments/{departmentId}/interview-rooms/{roomId}/current` | 当前面试 | `interview:evaluate` |
| POST | `/api/departments/{departmentId}/interview-rooms/{roomId}/current/finish` | 结束当前面试 | `interview:evaluate` |
| POST | `/api/departments/{departmentId}/interview-rooms/{roomId}/current/force-finish` | 强制结束当前面试 | `interview:evaluate` |
| POST | `/api/departments/{departmentId}/interview-rooms/{roomId}/current/evaluation` | 提交面试评价 | `interview:evaluate` |
| POST | `/api/departments/{departmentId}/interview-rooms/{roomId}/current/pass` | 标记过号 | `interview:evaluate` |
| POST | `/api/departments/{departmentId}/interview-rooms/{roomId}/current/stop-calling` | 停止叫号 | `interview:evaluate` |
| GET | `/api/departments/{departmentId}/interview-rooms/{roomId}/state` | 房间状态 | `interview:evaluate` |
| GET | `/api/departments/{departmentId}/interview-rooms/{roomId}/interviews/{interviewId}/evaluations` | 面试评价列表 | `interview:evaluate` |
| GET | `/api/departments/{departmentId}/interview-rooms/{roomId}/events` | SSE 订阅房间事件 | `interview:evaluate` |

> `statistics:read` 已授予本身份,暂无对应接口。

---

## `USER` 学生用户(所有已登录身份通用)

无需组织权限,登录即可(部分学生业务要求资料完整度 `profile_completed = 1`):

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/auth/me` | 当前用户资料 |
| GET | `/api/auth/oidc` | OIDC claims(调试) |
| GET | `/api/user/permissions` | 权限画像 |
| GET | `/api/user/profile` | 个人资料 |
| PUT | `/api/user/profile` | 更新联系方式 |
| GET | `/api/departments/{departmentId}/questionnaire` | 查看报名问卷 |
| GET | `/api/wechat/binding/url` | 获取微信绑定地址 |
| GET | `/api/wechat/binding/status` | 查询微信绑定状态 |
| DELETE | `/api/wechat/binding` | 解除微信绑定 |
| POST | `/api/departments/{departmentId}/applications` | 提交报名(需资料完整) |
| POST | `/api/check-ins` | 扫码签到 |
| GET | `/api/departments/{departmentId}/interviews/sessions/current` | 查询当前已发布场次 |
| GET | `/api/departments/{departmentId}/interviews/my-queue-status` | 我的排队状态 |
| GET | `/api/departments/{departmentId}/interviews/my-events` | SSE 订阅我的排队状态 |

---

## 公开接口(免登录)

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/auth/login` | OIDC 登录地址 |
| GET | `/api/auth/status` | 登录状态检查(不回显身份) |
| GET | `/api/banners` | 轮播图列表 |
| GET | `/api/boards` | 板块列表 |
| GET | `/api/college-majors` | 学院专业字典 |
| GET | `/api/workstations/{workstationId}` | 工作站详情 |
| GET | `/api/departments/{departmentId}` | 部门详情(可选登录,展示 `canManage`) |
| GET | `/api/wechat/binding/callback` | 微信授权回调 |

---

## 附录:已定义但当前未接到接口的权限码

以下权限码已存在于 `PermissionCode` 枚举且已授予相应角色,但当前没有任何 Controller
接口用 `@DepartmentPermission` 校验它们(对应功能尚未上线或未加注解):

| 权限码 | 说明 | 已授予的角色 |
|---|---|---|
| `admission:manage` | 管理录取结果 | BOARD/WORKSTATION/DEPARTMENT_ADMIN |
| `notification:manage` | 发布纳新通知 | BOARD/WORKSTATION/DEPARTMENT_ADMIN |
| `statistics:read` | 查看纳新统计 | BOARD/WORKSTATION/DEPARTMENT_ADMIN、DEPARTMENT_ASSISTANT |
| `admin:assistant:assign` | 任命部门辅助管理员 | DEPARTMENT_ADMIN |
| `admin:role:assign` | 分配管理员角色 | 仅 `SYSTEM_ADMIN`(`*`;角色分配实际走角色等级校验) |
| `system:user:manage` | 管理平台用户 | 仅 `SYSTEM_ADMIN` |
| `system:organization:manage` | 管理板块/工作站/部门 | 仅 `SYSTEM_ADMIN` |
| `system:config:manage` | 管理系统配置 | 仅 `SYSTEM_ADMIN` |

> `system:*` 与 `admin:role:assign` 未写入 `role_permission`,由 `SYSTEM_ADMIN` 的 `*`
> 权限覆盖;创建板块/工作站/部门的接口直接使用 `@SaCheckRole("SYSTEM_ADMIN")` 硬校验。
