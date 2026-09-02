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

目标组织必须是启用状态(`enabled = 1`):停用的板块、工作站和部门一律拒绝,
与 `/api/user/permissions` 只列出启用组织保持一致。

---

## `SYSTEM_ADMIN` 平台管理员

拥有全部权限(`*`),可访问所有接口,另含组织管理与角色分配:

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/admin/boards` | 创建板块 |
| POST | `/api/admin/workstations` | 创建工作站 |
| POST | `/api/admin/departments` | 创建部门 |
| PATCH | `/api/admin/boards/{boardId}` | 修改板块名称 |
| PATCH | `/api/admin/workstations/{workstationId}` | 修改工作站名称 |
| PATCH | `/api/admin/departments/{departmentId}` | 修改部门名称 |
| DELETE | `/api/admin/boards/{boardId}` | 删除板块(级联删除其下所有工作站、部门及关联数据) |
| DELETE | `/api/admin/workstations/{workstationId}` | 删除工作站(级联删除其下所有部门及关联数据) |
| DELETE | `/api/admin/departments/{departmentId}` | 删除部门(级联删除报名、面试、签到等关联数据) |
| GET | `/api/admin/users/page` | 分页查询平台用户,支持关键词与资料字段筛选 |
| POST | `/api/admin/role-assignments` | 分配角色(所有可分配角色) |
| DELETE | `/api/admin/role-assignments` | 撤销角色 |

> 删除接口为硬删除且不可恢复:板块删除会级联删除其下所有工作站与部门,
> 工作站删除会级联删除其下所有部门;部门删除会一并清理该部门的报名、面试场次、
> 签到、面试室、录取邮件队列等数据,以及指向该组织的 `user_role_scope` 角色分配。
> `department_poster`/`department_question` 等表在数据库层已配置
> `ON DELETE CASCADE`,随部门行删除自动清理。

> 角色分配接口本身只要求登录,实际按"操作者持有**更高级**身份且范围覆盖目标组织"在
> Service 层校验(等级:BOARD_ADMIN 40 > WORKSTATION_ADMIN 30 > DEPARTMENT_ADMIN 20
> > DEPARTMENT_ASSISTANT 10),`SYSTEM_ADMIN` 等级最高、范围全平台。
>
> **判定只看角色等级和数据范围,不看权限码。** `admin:assistant:assign` 和
> `admin:role:assign` 这两个权限码不参与本接口的校验:把 `admin:assistant:assign`
> 从 `DEPARTMENT_ADMIN` 的 `role_permission` 里删掉,他照样能任命辅助管理员。
> 这是刻意的设计——"能任命比自己低级的、且部门对得上"就是完整规则。

## 公开组织树接口

`GET /api/organizations` 无需登录，返回全部启用的板块 → 工作站 → 部门三级组织树。
板块、工作站包含 `id`、`name`；部门仅包含卡片展示所需的 `id`、`name`、
`campuses`、`assetId`、`introduction`。该接口不按角色或管理员作用域过滤，也不会返回
联系方式、纳新群、纳新要求、签到配置或成员身份信息。

## 角色授权页面读取接口

以下接口服务于“授予成员权限”页面，可由 `SYSTEM_ADMIN`、`BOARD_ADMIN`、
`WORKSTATION_ADMIN`、`DEPARTMENT_ADMIN` 使用；`DEPARTMENT_ASSISTANT` 没有可授予的
更低级身份，不能调用这些读取接口，以免借此枚举成员或用户信息。

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/admin/role-assignments?scopeType=BOARD|WORKSTATION|DEPARTMENT&scopeId={id}` | 查询与目标节点有关的有效成员授权；需要操作者作用域覆盖该节点 |
| GET | `/api/admin/users?casId={keyword}` | 按学号模糊匹配本地用户，至少输入 6 位，最多 20 条；过短或空输入返回空数组 |

`GET /api/admin/users` 只做学号联想、最多 20 条,刻意不支持翻页,避免下级管理员把
用户表当全量名单枚举。需要完整用户名单的平台用户管理页走
`GET /api/admin/users/page`,该接口仅 `SYSTEM_ADMIN` 可用。

## 平台用户管理接口

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/admin/users/page` | 分页查询平台用户;仅 `SYSTEM_ADMIN` |

查询参数(全部可选,`page`/`size` 有默认值):

| 参数 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `keyword` | string(≤100) | - | 模糊匹配姓名、学号、手机号、邮箱、QQ;`%` `_` 已转义 |
| `college` | string(≤64) | - | 学院精确匹配 |
| `major` | string(≤64) | - | 专业精确匹配 |
| `grade` | int(2000~2100) | - | 入学年级 |
| `profileCompleted` | boolean | - | 报名必填资料是否完整 |
| `wechatBound` | boolean | - | 是否已绑定微信公众号 |
| `sortBy` | `createdAt`\|`casId`\|`grade` | `createdAt` | 排序字段,白名单外的值会被参数校验拒绝 |
| `sortOrder` | `asc`\|`desc` | `desc` | 排序方向 |
| `page` | int ≥1 | `1` | 页码 |
| `size` | int 1~100 | `20` | 每页数量 |

返回 `PageVO`(`items`、`total`、`page`、`size`、`totalPages`),每项包含
`casId`、`name`、`email`、`phone`、`qq`、`college`、`major`、`grade`、
`profileCompleted`、`wechatBound`、`avatarUrl`、`createdAt`、`updatedAt`。
微信 OpenID 与 OIDC `sub` 属于身份凭据,不在列表中返回。

成员接口每一行对应一条原始 `user_role_scope` 授权，包含 `id`、`casId`、`name`、
`roleCode`、`roleName`、`scopeType`、`scopeId`、`scopeName` 以及板块/工作站/部门路径。
它会同时返回目标节点及其下级的直接授权、覆盖该节点的上级授权，以及 `ALL` 平台全局授权；
前端应根据返回的原始 `scopeType` 区分继承身份和直接身份。

---

## `BOARD_ADMIN` 板块管理员 / `WORKSTATION_ADMIN` 站长管理员

两个身份权限集相同(板块管理员范围按板块继承,站长管理员按工作站继承)。

拥有的权限码:
`recruitment:manage`、`application:read`、`application:export`、`check-in:manage`、
`interview:manage`、`interview:evaluate`、`admission:manage`、`notification:manage`、
`statistics:read`

可访问接口(除登录即可的通用接口外):

### statistics:read — 管理员数据总览

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/statistics/overview?scopeType={scopeType}&scopeId={scopeId}` | 按完整授权范围聚合报名、学院分布及部门面试摘要 |

### recruitment:manage — 部门纳新信息及活动

| 方法 | 路径 | 说明 |
|---|---|---|
| PUT | `/api/departments/{departmentId}` | 完整更新部门详情 |
| PATCH | `/api/departments/{departmentId}` | 部分更新部门详情 |
| POST | `/api/departments/{departmentId}/posters/upload` | 上传部门海报图片 |
| PUT | `/api/departments/{departmentId}/posters/order` | 按海报 ID 全量更新展示顺序 |

> 海报的删除、新增与调序在部门详情更新（`PUT`/`PATCH /api/departments/{departmentId}`）
> 的 `posters` 字段里一次提交完成：保留项传 `id`，新增项传上传接口返回的 `url`，
> 未出现在数组中的已有海报会被删除，顺序按数组下标。`/posters/order` 仅适用于
> 全部海报都已入库、只调顺序的场景。
| PUT | `/api/departments/{departmentId}/questionnaire` | 完整更新报名问卷 |
| DELETE | `/api/departments/{departmentId}/questionnaire` | 清空报名问卷 |

### application:read / application:export — 报名信息

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| GET | `/api/departments/{departmentId}/applications` | 分页查询报名 | `application:read` |
| GET | `/api/departments/{departmentId}/applications/{applicationId}` | 报名详情 | `application:read` |
| GET | `/api/departments/{departmentId}/interviews/users/{userId}/evaluations` | 按用户查询面试评价 | `application:read` |
| GET | `/api/departments/{departmentId}/applications/export` | 导出报名 | `application:export` |

### admission:manage — 录取结果

| 方法 | 路径 | 说明 |
|---|---|---|
| PUT | `/api/departments/{departmentId}/applications/{applicationId}/admission` | 标记为拟录取(草稿) |
| DELETE | `/api/departments/{departmentId}/applications/{applicationId}/admission` | 撤销拟录取草稿 |
| POST | `/api/departments/{departmentId}/applications/admissions/publish` | 发布本部门全部录取草稿，并向已绑定微信的报名者发送录取或未录取通知 |

录取发布事务提交后，拟录取草稿对应的报名者收到“录取”通知，其余仍为
`SUBMITTED` 的报名者收到“未录取”通知。通知使用姓名、面试结果和部门三个
模板字段；单条微信通知发送失败不会回滚录取发布。

### check-in:manage — 签到

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/departments/{departmentId}/interviews/sessions/{sessionId}/check-in/qr-code` | 生成指定场次签到二维码 |

### interview:manage — 面试安排

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/departments/{departmentId}/interviews/sessions` | 创建面试场次（含动态二维码开关和有效秒数） |
| PUT | `/api/departments/{departmentId}/interviews/sessions/{sessionId}` | 修改面试场次（含动态二维码开关和有效秒数） |
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
| GET | `/api/departments/{departmentId}/interviews/queue` | 查询未完成的面试队列 |
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

> `notification:manage` 已授予本身份,但当前还没有对应的接口
> (见文末"未接线权限"章节)。`DEPARTMENT_ASSISTANT` 没有 `admission:manage`,
> 因此上面的录取接口对辅助管理员一律 403。

---

## `DEPARTMENT_ADMIN` 部门管理员

权限码:在板块/站长管理员的基础上,额外拥有 `admin:assistant:assign`(任命辅助管理员)。

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| (全部板块/站长管理员接口) | 同上 | 同上 | 同上 |
| POST | `/api/admin/role-assignments` | 任命本部门辅助管理员 | 按角色等级校验 |
| DELETE | `/api/admin/role-assignments` | 撤销本部门辅助管理员 | 按角色等级校验 |

数据范围:仅本部门(`DEPARTMENT/{departmentId}`)。

任命接口走的是角色等级 + 数据范围(DEPARTMENT_ADMIN 20 > DEPARTMENT_ASSISTANT 10,
且 scope 必须覆盖目标部门),`admin:assistant:assign` 权限码本身不参与判定。

---

## `DEPARTMENT_ASSISTANT` 辅助管理员

权限码:`application:read`、`check-in:manage`、`interview:evaluate`、`statistics:read`。
只有查看/签到/面试考核权限,**没有**纳新信息、报名导出、面试安排等修改权限。

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| GET | `/api/departments/{departmentId}/applications` | 分页查询报名 | `application:read` |
| GET | `/api/departments/{departmentId}/applications/{applicationId}` | 报名详情 | `application:read` |
| GET | `/api/departments/{departmentId}/interviews/users/{userId}/evaluations` | 按用户查询面试评价 | `application:read` |
| GET | `/api/departments/{departmentId}/interviews/sessions/{sessionId}/check-in/qr-code` | 生成指定场次签到二维码 | `check-in:manage` |
| GET | `/api/departments/{departmentId}/interviews/queue-config` | 查询过号配置 | `interview:evaluate` |
| GET | `/api/departments/{departmentId}/interviews/queue` | 查询未完成的面试队列 | `interview:evaluate` |
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

`DEPARTMENT_ASSISTANT` 可使用 `GET /api/statistics/overview`查询其被授权部门的数据总览。

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
| GET | `/api/wechat/binding/url` | 兼容获取一次性微信绑定链接（保留 `authorizationUrl` 字段） |
| GET | `/api/wechat/binding/status` | 查询微信绑定状态 |
| DELETE | `/api/wechat/binding` | 解除微信绑定 |
| POST | `/api/wechat/bind/sessions` | 创建五分钟有效的一次性微信绑定链接 |
| GET | `/api/wechat/bind/sessions/{sessionId}` | 在原浏览器轮询扫码绑定结果 |
| POST | `/api/departments/{departmentId}/applications` | 提交报名(需资料完整) |
| DELETE | `/api/departments/{departmentId}/applications/me` | 取消本人尚未进入面试或录取流程的报名 |
| POST | `/api/check-ins` | 签到（扫码时令牌自带场次；否则传部门 ID 和场次 ID） |
| DELETE | `/api/departments/{departmentId}/interviews/sessions/{sessionId}/check-ins/me` | 取消本人指定场次尚未进入面试的签到；主动取消不恢复顺延优先资格 |
| GET | `/api/departments/{departmentId}/interviews/sessions/current` | 查询当前全部已发布场次 |
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
| GET | `/api/wechat/bind/start` | 复制到微信打开的扫码绑定 OAuth 入口 |
| GET | `/api/wechat/bind/oauth/callback` | 扫码绑定 OAuth 回调 |
| GET | `/api/wechat/login/url` | 获取微信内登录授权地址 |
| GET | `/api/wechat/login/callback` | 已绑定微信登录回调 |
| GET | `/api/wechat/check-in/entry` | 微信扫码登录并签到 |
| GET | `/api/wechat/js-sdk/config` | 【已废弃】获取订阅通知页面的 JS-SDK 签名与模板 ID（仅为兼容旧前端保留） |

---

## 附录:已定义但当前未接到接口的权限码

以下权限码已存在于 `PermissionCode` 枚举且已授予相应角色,但当前没有任何 Controller
接口用 `@DepartmentPermission` 校验它们(对应功能尚未上线或未加注解):

| 权限码 | 说明 | 已授予的角色 |
|---|---|---|
| `notification:manage` | 发布纳新通知 | BOARD/WORKSTATION/DEPARTMENT_ADMIN |
| `system:user:manage` | 管理平台用户 | 仅 `SYSTEM_ADMIN` |
| `system:organization:manage` | 管理板块/工作站/部门 | 仅 `SYSTEM_ADMIN` |
| `system:config:manage` | 管理系统配置 | 仅 `SYSTEM_ADMIN` |

> `system:*` 未写入 `role_permission`,由 `SYSTEM_ADMIN` 的 `*` 权限覆盖;
> 创建板块/工作站/部门的接口直接使用 `@SaCheckRole("SYSTEM_ADMIN")` 硬校验。
>
> `admin:assistant:assign` 和 `admin:role:assign` 不在本表内,因为它们不是"暂无接口",
> 而是**接口存在但刻意不按权限码校验**——角色分配统一走角色等级 + 数据范围,
> 详见上文 `SYSTEM_ADMIN` 章节的说明。
