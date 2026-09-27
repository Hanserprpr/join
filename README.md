# 学生在线纳新系统（Join）

学生在线纳新系统的后端服务，提供组织展示、学生报名、面试签到与排队、面试评价、录取发布及管理统计等接口。本仓库包含后端源码、数据库迁移和接口说明，前端需单独部署。

## 功能

- **组织与纳新展示**：板块 → 工作站 → 部门三级组织结构，支持部门资料、海报、成果和 Banner。
- **学生报名**：部门问卷、报名信息、校区筛选及 Excel 导出。
- **面试管理**：面试场次、面试室、二维码签到、队列叫号、顺延与重新签到、评分及 SSE 实时更新。
- **录取管理**：批量处理与发布录取结果，可配置邮件和微信公众号通知。
- **登录与权限**：SDU OIDC、外部身份 Token、微信登录与绑定；按角色、权限和组织范围控制管理操作。
- **文件与字典**：头像及海报支持本地或 S3 兼容存储；学院专业字典支持 Nacos 更新和包内数据回退。

## 技术栈

| 组件 | 实现 |
| --- | --- |
| 运行环境 | Java 21、Maven Wrapper |
| Web | Spring Boot 4.1.0、Spring MVC、Spring Security OAuth2 Client |
| 身份与权限 | Sa-Token 1.45.0，Redis 持久化登录态 |
| 数据库 | MySQL、MyBatis-Plus 3.5.17、Flyway |
| 扩展能力 | OpenFeign、Nacos、Apache POI、AWS S3 SDK、SMTP、Sentry |
| 测试 | Spring Boot Test、H2（MySQL 兼容模式） |

版本以 [pom.xml](pom.xml) 为准。

## 本地运行

### 1. 准备依赖

安装 JDK 21，启动 MySQL 和 Redis。首次构建需要下载 Maven 及依赖；无需另外安装 Maven。

为开发环境创建空数据库，例如：

```sql
CREATE DATABASE `join` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

数据库账号需有建表及执行迁移的权限。首次启动由 Flyway 自动创建表并执行后续迁移。

### 2. 配置环境

在仓库根目录执行：

```sh
cp .env.example .env
```

应用通过 `spring.config.import` 将当前工作目录的 `.env` 作为 properties 文件读取，因此应从仓库根目录启动。`.env` 已加入 Git 忽略规则。

编辑数据库配置，并补充以下设置（OIDC、Redis、前端地址等项目不全在示例文件中）：

```properties
DB_URL=jdbc:mysql://127.0.0.1:3306/join?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
DB_USERNAME=join_dev
DB_PASSWORD=替换为本地数据库密码
REDIS_HOST=127.0.0.1
REDIS_PORT=6379
REDIS_PASSWORD=
SERVER_PORT=8080

OIDC_CLIENT_ID=替换为已注册的客户端ID
OIDC_CLIENT_SECRET=替换为客户端密钥
OIDC_REDIRECT_URI=http://localhost:8080/api/login/oauth2/code/sdu
FRONTEND_URL=http://localhost:5173
CORS_ORIGINS=http://localhost:5173

NACOS_CONFIG_ENABLED=false
AVATAR_STORAGE=local
AVATAR_PUBLIC_BASE_URL=http://localhost:8080
POSTER_PUBLIC_BASE_URL=http://localhost:8080
ADMISSION_EMAIL_ENABLED=false
SENTRY_ENVIRONMENT=development
```

OIDC 客户端必须在身份提供方登记对应的回调地址。`OIDC_CLIENT_ID` 和 `OIDC_CLIENT_SECRET` 没有默认值，需要配置；完成真实登录还需要有效凭据和身份服务连通性。授权、Token、用户信息及 JWK 端点在 `application.yaml` 中显式配置。

默认 Session 和 Sa-Token Cookie 使用 `SameSite=None`、`Secure=true`。前后端联调需携带 Cookie，并正确配置跨域来源；非 localhost 的 HTTP 环境应使用 HTTPS，以满足安全 Cookie 要求。

示例文件中的微信回调及文件公开地址含占位域名，使用对应功能前需替换。完整配置见 [.env.example](.env.example) 和 [application.yaml](src/main/resources/application.yaml)。

### 3. 启动服务

```sh
sh ./mvnw spring-boot:run
```

默认监听 `8080` 端口。OIDC 登录入口为 `/api/oauth2/authorization/sdu`，回调路径为 `/api/login/oauth2/code/sdu`。

### 4. 测试与打包

```sh
# 运行测试
sh ./mvnw test

# 与 CI 一致：验证并打包
sh ./mvnw -B -ntp verify

# 运行打包产物（仍从含 .env 的目录启动）
java -jar target/join-0.0.1-SNAPSHOT.jar
```

测试配置使用 H2 和测试建表脚本，关闭 Flyway，并排除 Redis 自动配置；测试通过不代表实际 MySQL 迁移或外部服务联调成功。CI 配置见 [.github/workflows/ci.yml](.github/workflows/ci.yml)。

## 可选服务配置

| 能力 | 配置要点 |
| --- | --- |
| 学院专业字典 | 默认启用 Nacos；本地可设 `NACOS_CONFIG_ENABLED=false` 使用 `college-majors.json`。使用 Nacos 时 `NACOS_NAMESPACE` 填 namespace ID。 |
| 微信 | 配置 `WECHAT_APP_ID`、`WECHAT_APP_SECRET`、登录/绑定/签到回调和模板 ID；公开回调地址需与公众号配置一致。 |
| 录取邮件 | `ADMISSION_EMAIL_ENABLED=true`，并配置 `MAIL_HOST`、端口、账号、密码及 SSL；邮件通过 outbox 队列处理。 |
| S3 文件存储 | `AVATAR_STORAGE=s3`，配置 `AVATAR_S3_*`；海报共享头像的存储后端与 S3 凭据。 |
| 本地文件存储 | 默认保存到 `data/avatars` 和 `data/posters`，部署时持久化这些目录。 |
| 上传扫描 | `CLAMAV_ENABLED=true` 并配置 clamd；启用后扫描失败会拒绝上传。 |
| 错误监控 | 配置 `SENTRY_DSN`；留空不向 Sentry 上报，性能追踪采样默认 `0.0`。 |

## 接口与权限

- 服务生成的 OpenAPI JSON：`http://localhost:8080/v3/api-docs`。当前依赖提供 API 文档生成，未引入 Swagger UI。
- 仓库内接口快照：[学生在线纳新系统 API.openapi.yaml](docs/学生在线纳新系统%20API.openapi.yaml)。快照可能落后于源码，应以当前 Controller 和运行服务生成的文档为准。
- [接口权限对照](docs/interface-permissions.md)与[权限校验模型](docs/permission-check.md)：说明角色权限及组织范围继承。
- `GET /api/organizations`：公开的启用组织树。
- `GET /api/user/permissions`：获取当前登录用户的权限及可访问组织范围。

业务接口由 Sa-Token 和部门权限校验控制。管理员角色包括 `SYSTEM_ADMIN`、`BOARD_ADMIN`、`WORKSTATION_ADMIN`、`DEPARTMENT_ADMIN`、`DEPARTMENT_ASSISTANT`；普通学生基础角色为 `USER`。组织管理接口中的删除操作是硬删除，具体级联范围见权限文档。

## 数据库与部署

迁移脚本位于 `src/main/resources/db/migration/`，默认启动时自动执行。新建空库无需手动导入 `docs/schema.sql` 或逐个运行 `docs/migrations/` 中的历史脚本。

配置启用了 `baseline-on-migrate`，基线版本为 `1`：非空且没有 Flyway 历史的数据库会跳过 V1，只运行后续迁移。接入已有数据库前，应核对表结构是否满足 V1 基线并备份数据。数据库结构说明见 [docs/mysql.md](docs/mysql.md)。

生产部署需配置数据库、Redis、OIDC 和前后端公开地址。反向代理应转发 `/api/` 下的业务和 OAuth2 路径，按公网前缀设置所有回调 URL，并正确传递 `X-Forwarded-*`。应用使用 `forward-headers-strategy=framework`。

面试队列使用 SSE 长连接，代理需支持持续连接，并为 SSE 路径关闭响应缓冲、设置合理超时。根据并发规模调整 `SERVER_TOMCAT_MAX_CONNECTIONS` 和系统文件句柄限制。使用本地文件存储时，需备份并持久化 `data/`；数据库、Redis 和本地文件的数据生命周期应在部署方案中明确。

## 目录与补充文档

```text
src/main/java/cn/sduonline/join/
├── controller/       HTTP 接口
├── service/          业务逻辑、通知和存储
├── mapper/           数据库访问
├── data/             DTO、实体、枚举和响应结构
├── security/         身份认证和权限控制
├── config/           应用与外部服务配置
├── client/           外部身份、微信等客户端
└── validation/       自定义校验
src/main/resources/   应用配置、Flyway 迁移、学院专业字典
src/test/             测试与测试数据库脚本
docs/                 数据库、接口及前端联调说明
scripts/              运维辅助脚本
```

前端联调说明：

- [报名校区筛选](docs/frontend-application-campus-filter.md)
- [学院与校区推导](docs/frontend-college-campus-derivation.md)
- [部门海报编辑](docs/frontend-department-poster-editing.md)
- [微信绑定](docs/frontend-wechat-binding.md)
- [重新签到恢复](docs/frontend-recheck-in-recovery.md)

运维辅助脚本可能修改业务数据，执行前需阅读脚本及确认目标环境。
