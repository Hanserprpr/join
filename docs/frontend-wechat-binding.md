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

- `WAITING`：继续轮询。
- `BOUND`：绑定成功。网页 OAuth 只能确认 OpenID，不能据此确认用户是否关注公众号，
  因此接口不返回关注状态。
- `FAILED`：微信已绑定其他平台用户等确定性冲突，停止轮询并提示重新操作。

页面也应使用本地 `expiresAt` 倒计时；会话过期后接口返回参数错误，此时停止轮询。

二维码图片由前端二维码组件生成，二维码内容必须原样使用 `bindingUrl`。扫码后微信
会打开该链接，后端再发起 `snsapi_base` 静默授权并完成绑定；后端不调用公众号
`qrcode/create` 接口，也不依赖公众号 `SCAN` 或 `subscribe` 事件。
