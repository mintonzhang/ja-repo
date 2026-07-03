# kkRepo 私服仓库 v0.2.0 发布

## 前言

kkRepo 是一个面向团队和企业内部使用的自托管制品仓库，目标是在保留 Nexus 常见客户端访问习惯的同时，提供更适合现代部署形态的仓库服务。

它支持 Maven、npm、PyPI、Go、Helm、Cargo/Rust、Docker/OCI、NuGet、RubyGems、Yum 和 Raw 等多种制品格式，并保留常见的 `/repository/<repo>/...` URL 结构，方便已有 Nexus 用户迁移或替换。kkRepo 使用 MySQL 存储元数据、权限、token、审计、迁移状态和运行时共享状态，blob 数据可以放在 OSS/S3/File 存储中；其中 File 更适合本地试用和开发验证，生产环境建议使用 OSS/S3。

本次发布的 kkRepo v0.2.0 是 v0.1.0 首个公开版本之后的一次重要功能更新。这个版本重点补齐了 Docker/OCI 和 Cargo/Rust 仓库能力，增强了 Nexus 多版本迁移能力，引入真实客户端 E2E 兼容性验证，并优化了管理端体验、token 使用方式和安全细节。

## 本次更新重点

v0.2.0 的核心变化可以概括为五类：

1. Docker/OCI 仓库能力正式完成。
2. Cargo/Rust 仓库能力正式完成。
3. Nexus 迁移支持覆盖更多源端版本和存储形态。
4. 新增真实客户端 E2E 兼容性测试体系。
5. 管理端、token、安全和运维体验继续打磨。

## 一、Docker/OCI 仓库能力正式完成

v0.2.0 新增 Docker/OCI hosted、proxy 和 group 仓库支持，覆盖常见镜像仓库使用场景：

- hosted 仓库用于托管团队内部镜像。
- proxy 仓库用于代理和缓存上游 registry。
- group 仓库用于提供统一拉取入口。

在协议层面，本次实现覆盖 Docker Registry HTTP API V2 的主要路径，包括 `docker login`、镜像 push/pull、manifest、tag、blob、上传会话、跨仓库 blob mount、OCI referrers、proxy cache 和 group 解析等能力。

在运维层面，Docker/OCI 仓库也接入了浏览元数据、清理 worker、专用指标和 connector port 访问能力。对于需要从 Nexus Docker 仓库迁移的场景，Docker hosted 仓库数据也可以通过 Nexus Repository Data 迁移流程处理。

需要说明的是，Docker Registry V1 API 和 `docker search` 不是本阶段目标。如果未来出现明确迁移需求，可以再评估 search-only 兼容层。

## 二、Cargo/Rust 仓库能力正式完成

Rust 生态使用 Cargo 作为包管理和发布工具。v0.2.0 新增 Cargo/Rust hosted、proxy 和 group 仓库支持，并采用 Cargo sparse registry 方式对齐现代 Cargo 客户端行为。

本次 Cargo/Rust 支持包含：

- `cargo publish` 发布 crate。
- crate 下载和 sparse index 访问。
- yank / unyank。
- `cargo search`。
- `CargoToken` 认证。
- 管理端和 API 上传 `.crate` 文件。
- Cargo 相关 metrics。

对于日常使用来说，团队可以创建一个 Cargo hosted 仓库托管内部 crate，创建 Cargo proxy 仓库代理 crates.io 或内部上游，再通过 Cargo group 仓库提供统一入口。

本次版本还补充了 Cargo/Rust 的兼容性测试，并以 Nexus Repository 3.77.x 之后的 Cargo 仓库行为作为重要参考。

## 三、Nexus 迁移能力继续增强

kkRepo 的一个重要目标是降低 Nexus 用户迁移成本。v0.2.0 在迁移方向做了比较大的增强：不再只按固定版本假设源端结构，而是引入 source profile、adapter-specific migration plan、source hash 和 plan hash 等机制。

这意味着迁移逻辑可以根据源端能力和存储形态选择合适的适配器。当前自动化迁移 E2E 已覆盖：

- Nexus 3.29.2，OrientDB 时代源端。
- Nexus 3.77.2，H2 datastore 源端。
- Nexus 3.77.2，PostgreSQL datastore 源端。

本次更新还增加了 datastore 时代 Nexus Cargo hosted 内容迁移，并让 Cargo 的动态 `config.json` 在 browse 和 repository 响应中可见。

管理端的迁移 preflight 信息也更清晰，用户不再需要手工填写 source version，迁移表单会要求源端密码等必要字段，减少配置错误。

## 四、真实客户端 E2E 兼容性验证

协议兼容不能只看 HTTP handler 是否能返回预期 JSON，还要看真实客户端能不能正常发布、拉取、解析和安装。v0.2.0 新增 `client-e2e` live compatibility suite，用真实客户端覆盖多种格式：

- Maven
- npm
- PyPI
- Go
- Helm
- Cargo/Rust
- NuGet
- RubyGems
- Yum
- Docker/OCI

这套测试会在一次性环境中创建 kkRepo candidate，准备所需仓库和 connector port，然后用真实 CLI 客户端执行发布、下载、解析等动作。对于影响客户端行为的改动，后续可以通过 `run-client-e2e` 标签或 live compatibility workflow 触发验证。

Docker/OCI 方向还新增了 OCI Distribution conformance workflow，用于验证 Registry/OCI 兼容行为。

## 五、管理端、token 和安全体验优化

v0.2.0 也包含一批体验和安全修复。

管理端方面：

- 新增 MySQL-backed UI language settings，支持跟随浏览器、英文和中文。
- 后台侧边栏支持按 Repository、Security、System、Migration 等分组折叠，并在浏览器中记住状态。
- 表单必填项提示和提交前校验更一致，OIDC 和迁移配置更不容易保存半成品。
- anonymous access 和 realm 设置会固定使用 Local source，避免 API 调用传入其他 source 造成歧义。

token 方面：

- 用户和管理端 token 下拉框现在暴露 `RubyGemsApiKey`。
- 新增可见的 `GenericToken` 类型，适合 CI、脚本和自定义 HTTP 客户端通过 API-key header 或 bearer token 调用。

安全方面，本次修复了 CodeQL 扫描发现的一组问题，包括：

- 组件搜索 tokenization 避免 ReDoS 风险。
- proxy remote URL 构造不允许请求路径覆盖配置好的远端 host。
- browse listing 使用框架 HTML escaping，并增加 XSS 回归测试。
- OIDC 授权和 token endpoint 会先经过出站策略、issuer 和 discovery host 校验。

## 快速体验 v0.2.0

本地体验可以直接使用 quickstart 脚本：

```bash
curl -fsSL https://raw.githubusercontent.com/klboke/kkrepo/main/scripts/quickstart.sh | bash
```

v0.2.0 起，quickstart 默认使用：

```text
ghcr.io/klboke/kkrepo:0.2.0
```

启动完成后访问：

```text
管理控制台：http://127.0.0.1:19090/admin/
用户侧浏览器：http://127.0.0.1:19090/browse/
健康检查：http://127.0.0.1:19091/actuator/health
```

首次进入管理控制台时，需要创建初始 `Local/admin` 管理员密码。进入后台后，先创建名为 `default` 的 blob store；本地试用可以选择 File，生产环境建议选择 OSS/S3。

也可以直接拉取镜像：

```bash
docker pull ghcr.io/klboke/kkrepo:0.2.0
docker pull ghcr.io/klboke/kkrepo:latest
```

本次镜像已发布 `linux/amd64` 和 `linux/arm64` 两个平台。

## 升级建议

已有 v0.1.0 部署可以升级到 v0.2.0。生产环境升级前建议先完成 MySQL 备份，并在测试环境验证仓库配置、权限、token、匿名访问策略和迁移流程。

本次版本会新增 Docker registry metadata、Docker connector port 唯一约束和 UI settings 等 Flyway schema 变更。部署 v0.2.0 后，服务启动时会按 Flyway 迁移流程更新数据库结构。

Docker/OCI 和 Cargo/Rust 是本次新增的公开能力。如果要在生产环境开放给开发者和 CI 使用，建议先在 staging 环境验证：

- hosted / proxy / group 仓库配置。
- token 和匿名访问策略。
- Docker connector port 或反向代理配置。
- proxy 回源和 group 成员顺序。
- CI 客户端发布和拉取流程。

## 小结

kkRepo v0.2.0 把 Docker/OCI 和 Cargo/Rust 从路线图推进到可用能力，同时继续强化 Nexus 迁移和真实客户端兼容验证。对于希望保留 Nexus 客户端访问习惯，又希望使用 MySQL、OSS/S3 和多副本友好架构的团队来说，这个版本已经覆盖了更多真实生产迁移和日常制品管理场景。

更多信息可以查看：

- GitHub Release：https://github.com/klboke/kkRepo/releases/tag/v0.2.0
- Changelog：https://github.com/klboke/kkRepo/blob/main/CHANGELOG.md
- 项目地址：https://github.com/klboke/kkRepo
