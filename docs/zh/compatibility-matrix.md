# 兼容性矩阵

本文汇总 nexus-plus 当前公开兼容面。这里关注的是用户可见行为：客户端命令、HTTP 路径、仓库 recipe、迁移支持和已知限制。除非 Nexus 内部机制会影响客户端行为，否则不把内部实现细节作为兼容目标。

更详细的验证流程见 [Nexus 兼容性测试说明](nexus-compatibility-testing.md)。

## 兼容原则

- 保持 Nexus `/repository/<repo>/...` URL 布局，尽量复用既有客户端配置。
- 先对齐官方协议和 Nexus 用户可见行为，再增加项目自定义行为。
- 对外可见行为优先通过真实 Nexus 参考实例做兼容性测试。
- 有状态逻辑默认按多副本部署设计：MySQL 是元数据和协调状态的事实来源；blob 内容放在 OSS/S3/File 存储；进程内缓存必须可重建。

## 仓库格式矩阵

| 格式 | 仓库类型 | 主要客户端操作 | 浏览/搜索 | 迁移支持 | 兼容性验证 |
| --- | --- | --- | --- | --- | --- |
| Maven | hosted / proxy / group | Maven deploy、PUT 上传、GET/HEAD/checksum 读取、snapshot/release metadata、管理台组件上传 | 支持 | 默认迁移 hosted；proxy 可选 | `MavenRepositoryBlackBoxCompatibilityTest`、`MavenMetadataMergeCompatibilityTest`、`MavenWritePolicyCompatibilityTest`、`ComponentUploadBlackBoxCompatibilityTest` |
| npm | hosted / proxy / group | `npm publish`、tarball 下载、包 metadata、dist-tags、audit endpoint 兼容、管理台上传 | 支持 | 默认迁移 hosted；proxy 可选 | `NpmProtocolCompatibilityTest`、`NpmRepositoryBlackBoxCompatibilityTest`、`ComponentUploadBlackBoxCompatibilityTest` |
| PyPI | hosted / proxy / group | `twine upload`、包下载、simple index 读取、管理台上传 | 支持 simple index | 默认迁移 hosted；proxy 可选 | `PypiRepositoryBlackBoxCompatibilityTest`、`ComponentUploadBlackBoxCompatibilityTest` |
| Go | proxy / group | Go module proxy 读取：list、info、mod、zip、latest、group fallback | 支持 | proxy 可选 | `GoProxyBlackBoxCompatibilityTest` |
| Helm | hosted / proxy | Chart push、PUT 上传、chart 下载、`index.yaml`、proxy index rewrite、管理台上传 | 支持 `index.yaml` | 默认迁移 hosted；proxy 可选 | `HelmRepositoryBlackBoxCompatibilityTest`、`ComponentUploadBlackBoxCompatibilityTest` |
| NuGet | hosted / proxy / group | package push、包下载、v3 service index、registration、flat container、search/autocomplete、管理台上传 | 支持 v3 service index/search | 默认迁移 hosted；proxy 可选 | `NugetRubygemsYumRepositoryBlackBoxCompatibilityTest` |
| RubyGems | hosted / proxy / group | gem push/yank、gem 下载、compact 和 legacy index assets、管理台上传 | 支持 | 默认迁移 hosted；proxy 可选 | `NugetRubygemsYumRepositoryBlackBoxCompatibilityTest` |
| Yum | hosted / proxy / group | RPM PUT/upload、包下载、`repodata` metadata | 支持 `repodata` | 默认迁移 hosted；proxy 可选 | `NugetRubygemsYumRepositoryBlackBoxCompatibilityTest` |
| Raw | hosted / proxy / group | PUT 上传、GET/HEAD 读取、group/proxy fallback、管理台上传 | 支持 | 默认迁移 hosted；proxy 可选 | `RawRepositoryBlackBoxCompatibilityTest`、`ComponentUploadBlackBoxCompatibilityTest` |
| Docker / OCI | 计划先支持 hosted，再支持 proxy/group | 开发中，见开发计划 | 计划中 | 计划中 | [Docker / OCI 开发计划](dev/docker-repository-implementation-plan.md) |

## 管理和安全兼容

| 领域 | 当前兼容目标 | 验证方式 |
| --- | --- | --- |
| 安全管理 API | Nexus 风格的用户、角色、权限、仓库引用、realm 类型名，以及部分 ExtDirect/UI contract | `SecurityAdminBlackBoxCompatibilityTest` |
| 仓库权限模型 | Nexus 风格的 repository view、browse、read、edit、add、delete、component-create 语义 | server 安全测试和 live compatibility 测试 |
| 组件上传 API | Nexus 风格 `/service/rest/v1/components` 上传规格和部分格式上传 | `ComponentUploadBlackBoxCompatibilityTest` |
| Browse API | 仓库 browse 返回形态和权限过滤 | `SecurityAdminBlackBoxCompatibilityTest` 和 server browse 测试 |
| 认证 realm | Local 用户、LDAP、OIDC bearer/auth-code、API key、session subject | server 安全测试 |

## URL 兼容

主要客户端入口是：

```text
/repository/<repo>/<artifact-path>
```

示例：

```text
/repository/maven-public/org/example/app/1.0.0/app-1.0.0.pom
/repository/npm-hosted/@scope/package
/repository/pypi-proxy/simple/demo/
/repository/helm-hosted/index.yaml
/repository/nuget-group/v3/index.json
```

Docker / OCI 比较特殊，因为 Docker 客户端使用 registry `/v2/...` 路由。当前计划是增加 Docker 专用端口，并使用 path-based repository routing：

```text
<host>:<docker-port>/<repo>/<image>:<tag>
```

## 迁移兼容

nexus-plus 把迁移作为产品能力，而不是一次性脚本：

- 元数据迁移覆盖用户、角色、权限、blob store、repository 定义和相关兼容数据。
- 仓库数据迁移默认扫描 hosted 仓库。
- proxy 仓库可显式指定，用于迁移历史备份数据或回源缓存数据。
- 迁移步骤按 preflight/dry-run、resume、checksum 校验和报告能力设计。
- 不支持或被阻塞的条目应进入报告，而不是静默跳过。

详见 [Nexus 迁移说明](nexus-migration-guide.md)。

## 已知限制

- nexus-plus 不是 Nexus 内部机制的完整复刻。Karaf、OSGi、OrientDB、内嵌 Elasticsearch 和 Nexus task 子系统不是兼容目标。
- Docker / OCI Registry 支持正在开发中，当前还不是已完成仓库格式。
- Go 不支持 hosted 上传；Go module proxy 行为以读取代理为主。
- 不承诺覆盖每一个 Nexus UI endpoint。只有在支持用户工作流或迁移兼容需要时，才补对应 endpoint。
- 当协议允许非确定性时，测试中可能规范化排序、时间戳、生成 ID 和 hostname。
- File blob storage 可用于本地试用和开发；生产部署建议使用 OSS/S3 兼容存储。

## 如何反馈兼容差异

提交 Nexus compatibility issue，并包含：

- Nexus 版本和 nexus-plus 版本或 commit。
- 仓库格式和 recipe。
- 精确客户端命令或 HTTP 请求。
- Nexus 的状态码、header 和响应体语义。
- nexus-plus 的状态码、header 和响应体语义。
- 对真实客户端的影响。

普通兼容差异可以用公开 issue。可利用的安全问题请按 [SECURITY.md](../../SECURITY.md) 私下报告。
