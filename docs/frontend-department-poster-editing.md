# 前端适配说明：海报删除/新增/调序合并到部门详情保存

## 1. 改动概要

海报在 S3 存储下，详情接口返回的 `url` 是**预签名的临时地址**（带签名 query，会过期）。
旧契约里 `posters` 数组只能按 `url` 提交，而预签名地址过不了服务端的白名单校验，
于是前端拿到的 URL 无法原样回传——结果是"保留其余海报、只删掉某一张"这种请求根本
拼不出来，海报实际上只能增不能删。

这一版把 `posters` 的写入语义从**按 URL 全量替换**改成**按 id 增量 diff**：
保留项按 `id` 引用，服务端沿用库里存的地址，前端不再需要回传任何已有海报的 URL。

前端需要改的核心只有一句：**已有海报回传 `id`，新增海报回传 `url`，
删除就是不出现在数组里，顺序由数组下标决定。**

删除、新增、调序在**一次保存**里完成，走的还是原来的部门详情更新接口，
**没有新增接口，也没有"立即删除海报"的接口**——所有改动都在点保存时才生效，
取消编辑则什么都不会发生。

统一返回体不变：

```ts
interface ApiResult<T> {
  code: number
  data: T | null
  msg: string
  timestamp: number
}
```

`code === 0` 表示成功。

## 2. 接口变更清单

| 接口 | 变更 | 影响 |
| --- | --- | --- |
| `PATCH /api/departments/{departmentId}` | `posters[]` 项结构变更，新增 `id` | **破坏性** |
| `PUT /api/departments/{departmentId}` | 同上 | **破坏性** |
| `GET /api/departments/{departmentId}` | 无变更 | 兼容 |
| `POST /api/departments/{departmentId}/posters/upload` | 无变更 | 兼容 |
| `PUT /api/departments/{departmentId}/posters/order` | 无变更，但编辑流程中不再需要 | 兼容 |

## 3. 请求结构

`posters` 数组项从 `{ url, sortOrder }` 变为：

```ts
interface DepartmentPosterRequest {
  /** 已有海报 ID，表示保留这张海报 */
  id?: number
  /** 新增海报地址，取自上传接口返回值 */
  url?: string
  /** 排序值，不传时按数组下标 */
  sortOrder?: number
}
```

**`id` 与 `url` 必须恰好提供其中之一**，两个都传或都不传返回 `40000`。

四条规则：

| 操作 | 怎么表达 |
| --- | --- |
| 保留已有海报 | 传 `{ id }`，**不要传 url** |
| 新增海报 | 传 `{ url }`，取上传接口返回的地址 |
| 删除海报 | 不出现在数组里 |
| 调整顺序 | 数组顺序即展示顺序，`sortOrder` 不用传 |

## 4. 典型流程

部门当前有三张海报 A(id=1)、B(id=2)、C(id=3)。用户在编辑态里删掉 B、
上传一张新的 D、并把 C 拖到最前面，然后点保存：

```http
PATCH /api/departments/12
Content-Type: application/json

{
  "posters": [
    { "id": 3 },
    { "id": 1 },
    { "url": "https://files.example.com/join/posters/新上传的.jpg" }
  ]
}
```

服务端会删掉 B、把 C 和 A 的顺序更新为 0 和 1、插入 D。
**A 和 C 的行 id 保持不变**（旧实现是全删全插，每次保存 id 都会变）。

上传仍然是独立一步，用户选完图就调，拿到的地址先存在前端本地状态里：

```http
POST /api/departments/12/posters/upload
Content-Type: multipart/form-data

file=<图片>
```

返回 `{ "url": "https://files.example.com/join/posters/xxx.jpg" }`。
这个地址是稳定的白名单地址，不是预签名地址，可以直接放进 `posters[].url`。

注意上传接口一调用文件就已经落到对象存储了，但**在保存之前它不属于任何部门**，
用户取消编辑就只是产生一个没人引用的孤儿文件，不会影响展示。

## 5. 前端状态怎么存

编辑态的本地数组建议存成这样，保存时直接映射：

```ts
type PosterDraft =
  | { kind: 'existing'; id: number; previewUrl: string }
  | { kind: 'new'; url: string; previewUrl: string }

// previewUrl 用于回显：existing 用详情接口返回的预签名 url，
// new 用上传接口返回的 url。它只用来显示，不参与提交。

const posters = drafts.map(d =>
  d.kind === 'existing' ? { id: d.id } : { url: d.url }
)
```

关键点：**`existing` 项必须把 `id` 一路带住**。如果某一项的 `id` 在中途丢了，
它会被当成"删除旧的 + 新增一张"，海报会被重建、id 变化；而如果你把预签名的
`previewUrl` 当成 `url` 提交，会直接报 `140032`。

## 6. 错误码

| 错误码 | 含义 | 前端处理 |
| --- | --- | --- |
| `40000` | 参数错误，通常是 `id`/`url` 没有恰好提供一个 | 检查提交逻辑 |
| `140032` | 海报地址不在白名单中 | 多半是把详情接口返回的预签名 URL 当 `url` 提交了，改成传 `id` |
| `140036` | 引用的海报 id 不属于该部门，或数组里 id 重复 | 提示"海报列表已变化，请刷新后重试"并重新拉详情 |

`140036` 在服务端是在任何写操作**之前**校验的，所以报这个错时详情的其他字段
（介绍、联系方式等）也不会被写入，整个保存是原子失败的，前端直接重试即可。

## 7. 关于 `/posters/order`

`PUT /api/departments/{departmentId}/posters/order` 没有变，但它要求请求覆盖
当前**全部**海报且都带 id。编辑态里新增的海报在保存前没有 id，所以只要这次编辑
含新增，就用不了这个接口。

**建议编辑页统一走 `PATCH` 一条路**，`/posters/order` 只留给"全部海报都已入库、
纯拖拽调序并立即生效"的场景（如果没有这种场景，可以不再调用它）。

## 8. 已知限制

- **并发覆盖未拦截**：两个管理员同时编辑同一个部门时，后保存的会按自己的列表
  删掉对方新增的海报。如果实际会出现多人同时编辑，需要再加一层乐观锁
  （前端提交时带上"读取时看到的 id 全集"），届时会另行通知。
- **对象文件不会删除**：删掉海报只删数据库记录，对象存储里的文件仍然保留，
  由服务端的清理任务处理。对前端无影响。
