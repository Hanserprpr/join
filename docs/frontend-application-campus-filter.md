# 报名学生校区与列表筛选

本次为兼容性增量：现有 URL、旧参数、权限和响应原字段保持不变。

## 学生资料

`PUT /api/user/profile` 新增可选 `campus` 字段，可单独提交：

```json
{"campus":"CENTRAL"}
```

字段缺省或为 null 时不修改已有校区；不支持用空字符串清空。校区不加入原有必填资料校验，不影响历史用户登录、资料完成状态和报名。历史未填写的数据为 null。

> 该字段已不再是校区的权威来源，见 [按学院自动分配校区](frontend-college-campus-derivation.md)：
> 服务端按学院从字典推导校区，请求里的 `campus` 只在字典未给出该学院校区时兜底。

个人资料响应（UserProfileVO）、报名列表和报名详情增加 `campus`。前端可将 null 显示为“未填写”。

## 列表与导出

下面两个接口均新增可选查询参数 `campus`，使用下表的枚举编码：

- `GET /api/departments/{departmentId}/applications?campus=CENTRAL&page=1&size=20`
- `GET /api/departments/{departmentId}/applications/export?campus=CENTRAL`

不传参数时不限制校区，仍包含历史未填写校区的报名人；传入校区时仅匹配该校区的学生，可与接口原有筛选条件组合。非法编码返回参数错误。分页总数与列表采用相同筛选；导出按相同校区筛选，Excel 原有列和列顺序保持不变。

| 编码 | 显示名称 |
| --- | --- |
| SOFTWARE_PARK | 软件园校区 |
| CENTRAL | 中心校区 |
| QIANFOSHAN | 千佛山校区 |
| XINGLONGSHAN | 兴隆山校区 |
| HONGJIALOU | 洪家楼校区 |
| BAOTUQUAN | 趵突泉校区 |

前端增加“全部校区”选项（不传 campus），切换筛选时重置 page=1，导出时携带当前校区。筛选依据为用户当前资料的校区，而非部门所在校区或报名时的快照。

## 部署

通过 Flyway 执行 `V15__add_user_campus.sql`，为 user 表添加可空 VARCHAR(32) 列。先完成数据库迁移再提供新版接口；不修改历史迁移，不强制历史用户补填。当前仓库为后端，前端选择框需在前端项目接入。
