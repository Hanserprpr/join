# 前端微信绑定接入

## 环境分流

- 微信内置浏览器：可以继续调用兼容接口 `GET /api/wechat/binding/url`，跳转返回的
  `authorizationUrl`，走静默 OAuth。该字段现在等于 `bindingUrl`。
- 电脑或普通手机浏览器：调用 `POST /api/wechat/bind/sessions`，前端把返回的
  `bindingUrl` 生成为二维码，并每 2～3 秒轮询绑定结果。
- 手机普通浏览器：除前端生成的二维码外，提供“复制链接，前往微信打开”，复制
  同一个 `bindingUrl`。该链接只包含五分钟有效的一次性随机令牌，不包含用户信息。

## 创建扫码绑定会话

```http
POST /api/wechat/bind/sessions
```

旧前端也可以继续请求：

```http
GET /api/wechat/binding/url
```

兼容接口会创建相同的绑定会话，并同时返回 `authorizationUrl` 和 `bindingUrl`；两者
内容完全相同。旧前端可以继续直接跳转 `authorizationUrl`，新前端可以将任一字段
生成为二维码，并使用返回的 `sessionId` 轮询。

接口要求当前用户已登录，并返回：

```json
{
  "code": 0,
  "data": {
    "sessionId": "wb_随机令牌",
    "status": "WAITING",
    "bindingUrl": "https://后端/api/wechat/bind/start?token=...",
    "expiresAt": "2026-09-01T10:05:00Z",
    "expiresIn": 300
  }
}
```

## 轮询结果

```http
GET /api/wechat/bind/sessions/{sessionId}
```

该接口同时校验当前登录用户和创建会话时的浏览器 Session。返回状态：

- `WAITING`：继续轮询。用户扫码/打开链接后若尚未关注公众号，后端会用公众号
  access_token 查询 `/cgi-bin/user/info` 的 `subscribe` 字段拦截绑定，此时会话
  仍保持 `WAITING`（不消耗一次性令牌）。此时前端应继续轮询——只要公众号「服务器
  配置」已经指向 `/api/wechat/mp/callback`，用户关注公众号的瞬间后端就会收到
  `subscribe` 事件推送并自动完成绑定，无需用户重新扫码；即使没有配置服务器
  推送，用户关注后重新扫码/打开同一链接也能完成绑定。
- `BOUND`：绑定成功（隐含用户已关注公众号）。
- `FAILED`：微信已绑定其他平台用户等确定性冲突，停止轮询并提示重新操作。

“复制链接，前往微信打开”这条快捷路径（`GET /api/wechat/bind/oauth/callback`）
在未关注时会直接把浏览器重定向到 `app.wechat.official-account-profile-url`
配置的公众号主页，引导用户先关注。

页面也应使用本地 `expiresAt` 倒计时；会话过期后接口返回参数错误，此时停止轮询。

二维码图片由前端二维码组件生成，二维码内容必须原样使用 `bindingUrl`。扫码后微信
会打开该链接，后端再发起 `snsapi_base` 静默授权并完成绑定；后端不调用公众号
`qrcode/create` 接口。

## 关注后自动完成绑定（可选）

在公众号后台「设置与开发 → 基本配置 → 服务器配置」填写：

- 服务器地址（URL）：`https://你的后端域名/api/wechat/mp/callback`
- Token：任意字符串，与后端 `WECHAT_SERVER_TOKEN`（`app.wechat.server-token`）
  保持一致
- 消息加密方式：明文模式（当前只实现了明文模式）

保存时微信会先发一次 `GET` 请求做签名校验（`signature`/`timestamp`/`nonce`/
`echostr`），通过后正式启用。此后用户关注公众号时，微信会以 `POST` 推送
`subscribe` 事件到该地址；若该 OpenID 恰好有一个正在等待关注的绑定会话
（即 `completeOAuth` 因未关注而中止的那次），后端会直接用事件里的 OpenID
完成绑定，会话状态变为 `BOUND`，前端轮询会立刻看到结果，不需要用户手动
重新扫码。不配置服务器推送也不影响绑定功能，只是失去这个自动完成的体验。
